// Runs the packaged backend against an isolated MySQL database and least-privilege purchase application user.
// Usage: mvn clean package (in backend), then node backend/database/verify-purchase.mjs
import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { spawn, spawnSync } from 'node:child_process'
import { setTimeout as delay } from 'node:timers/promises'

const mysql = process.env.MYSQL_BIN || 'mysql'
const host = process.env.DB_HOST || '127.0.0.1'
const port = process.env.DB_PORT || '3306'
assert.ok(['127.0.0.1', 'localhost'].includes(host), 'Use a local development MySQL instance')
assert.match(port, /^\d{1,5}$/)
assert.ok(process.env.DB_PASSWORD !== undefined, 'Set DB_PASSWORD for a test database administrator')
const suffix = randomBytes(8).toString('hex')
const database = `stockflow_purchase_test_${suffix}`
const username = `sf_po_${suffix}`
const password = randomBytes(24).toString('hex')
let databaseCreated = false
let userCreated = false
let server
let serverExited
let baseUrl
let output = ''
let passed = 0

function sql(statement, selectedDatabase, expectedError, asReader = false) {
  const result = spawnSync(mysql, ['--no-defaults', '--batch', '--skip-column-names',
    '--default-character-set=utf8mb4', '--connect-timeout=5', `--host=${host}`,
    `--port=${port}`, `--user=${(asReader ? username : (process.env.DB_USERNAME || 'root'))}`,
    ...(selectedDatabase ? [`--database=${selectedDatabase}`] : [])],
  { input: statement, encoding: 'utf8', timeout: 30000,
    env: { ...process.env, MYSQL_PWD: asReader ? password : process.env.DB_PASSWORD }, maxBuffer: 1024 * 1024 })
  if (result.error) throw result.error
  if (expectedError) {
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, new RegExp('ERROR ' + expectedError + ' '))
    passed++
    console.log('PASS MySQL rejects invalid/unauthorized write with ' + expectedError)
    return
  }
  assert.equal(result.status, 0, result.stderr.replaceAll(password, '[redacted]').replaceAll(process.env.DB_PASSWORD || '\0', '[redacted]'))
  return result.stdout.trim()
}
async function api(method, path, body, status = 200) {
  const response = await fetch(baseUrl + path, {
    method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(10000),
  })
  const json = await response.json()
  if (response.status !== status) { await delay(100); console.error(output.split("\n").filter(line => /ERROR|Exception|Caused|###|denied/.test(line)).join("\n").replaceAll(password, "[redacted]").replaceAll(process.env.DB_PASSWORD || "\0", "[redacted]")) }
  assert.equal(response.status, status, `${method} ${path}: ${JSON.stringify(json)}`)
  assert.equal(json.code, status)
  assert.equal(typeof json.message, 'string')
  if (status >= 400) assert.equal(json.data, null)
  passed++
  console.log(`PASS ${method} ${path} -> ${status}`)
  return json.data
}
async function stopServer() {
  if (!server || !server.pid || server.exitCode !== null) return
  server.kill()
  await Promise.race([serverExited, delay(10000)])
  if (server.exitCode === null) {
    server.kill('SIGKILL')
    await serverExited
  }
}
try {
  console.log('Creating isolated database ' + database)
  sql('CREATE DATABASE ' + database + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
  databaseCreated = true
  for (const file of ['001_init_schema.sql', '002_product_enabled.sql'])
    sql(readFileSync(new URL('./' + file, import.meta.url), 'utf8'), database)
  sql("CREATE USER '" + username + "'@'localhost' IDENTIFIED BY '" + password + "';")
  userCreated = true
  sql("GRANT SELECT ON " + database + ".* TO '" + username + "'@'localhost';")
  for (const [table, privileges] of [['category', 'INSERT, UPDATE, DELETE'], ['product', 'INSERT, UPDATE'], ['sku', 'INSERT, UPDATE, DELETE'], ['warehouse', 'INSERT, UPDATE, DELETE'], ['purchase_order', 'INSERT, UPDATE'], ['purchase_order_item', 'INSERT, UPDATE, DELETE'], ['inventory', 'INSERT, UPDATE'], ['inventory_transaction', 'INSERT']])
    sql("GRANT " + privileges + " ON " + database + "." + table + " TO '" + username + "'@'localhost';")
  const jar = fileURLToPath(new URL('../target/stockflow-0.0.1-SNAPSHOT.jar', import.meta.url))
  const java = process.env.JAVA_BIN || (process.env.JAVA_HOME ? process.env.JAVA_HOME + '/bin/java' : 'java')
  server = spawn(java, ['-jar', jar], {
    windowsHide: true,
    env: {
      ...process.env, MYSQL_PWD: '', DB_USERNAME: username, DB_PASSWORD: password, DB_NAME: database,
      SERVER_ADDRESS: '127.0.0.1', SERVER_PORT: '0',
      CORS_ALLOWED_ORIGINS: 'http://localhost:5173,http://localhost:5174',
      SPRING_DATASOURCE_URL: 'jdbc:mysql://' + host + ':' + port + '/' + database + '?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=3000',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  serverExited = new Promise(resolve => { server.once('exit', resolve); server.once('error', resolve) })
  let spawnFailed = false
  server.on('error', error => { spawnFailed = true; output += error.message })
  const capture = chunk => {
    output = (output + chunk.toString()).slice(-64000)
    const match = output.match(/Tomcat started on port (\d+)/)
    if (match) baseUrl = 'http://127.0.0.1:' + match[1] + '/api'
  }
  server.stdout.on('data', capture)
  server.stderr.on('data', capture)
  for (let attempt = 0; !baseUrl && !spawnFailed && attempt < 120 && server.exitCode === null; attempt++) await delay(500)
  assert.ok(baseUrl, 'Backend did not start:\n' + output)
  assert.equal((await api('GET', '/health')).status, 'UP')

  sql(readFileSync(new URL('./dev_purchase_operator.sql', import.meta.url), 'utf8'), database)
  sql(readFileSync(new URL('./dev_purchase_operator.sql', import.meta.url), 'utf8'), database)
  sql("INSERT INTO category(id,name) VALUES(1,'采购测试');" +
    "INSERT INTO product(id,category_id,name,enabled) VALUES(1,1,'测试商品',1),(2,1,'停用商品',0);" +
    "INSERT INTO sku(id,product_id,code,name) VALUES(1,1,'SKU-A','规格 A'),(2,1,'SKU-B','规格 B'),(3,2,'DISABLED','停用规格'),(4,1,'BIG','大整数规格'),(5,1,'RACE-NEW','并发首笔'),(6,1,'BROWSER-SKU','浏览器验收规格');" +
    "INSERT INTO warehouse(id,code,name) VALUES(1,'WH-TEST','测试仓'),(2,'WH-ROLLBACK','回滚测试仓'),(3,'WH-BROWSER','浏览器演示仓');" +
    "INSERT INTO inventory(warehouse_id,sku_id,on_hand_qty,locked_qty,version) VALUES(1,1,10,3,7);", database)
  const prefix = '/purchase-orders'
  const actor = sql("SELECT id FROM sys_user WHERE username='stockflow-local-operator';", database)
  assert.equal(sql('SELECT COUNT(*) FROM sys_user;', database), '1')
  const create = async (warehouseId = 1, status = 200) => api('POST', prefix, { warehouseId, remark: '验收采购' }, status)
  const add = async (id, skuCode, quantity = '5', status = 200) =>
    api('POST', prefix + '/' + id + '/items', { skuCode, quantity }, status)
  const action = async (id, name, status = 200) => api('POST', prefix + '/' + id + '/' + name, undefined, status)
  const detail = id => api('GET', prefix + '/' + id)
  const approved = async (warehouseId, lines) => {
    let result = await create(warehouseId)
    for (const [skuCode, quantity] of lines) result = await add(result.order.id, skuCode, quantity)
    return action(result.order.id, 'approve')
  }
  const balance = (warehouseId, skuId) => sql('SELECT on_hand_qty,locked_qty,version FROM inventory WHERE warehouse_id=' +
    warehouseId + ' AND sku_id=' + skuId + ';', database)
  const snapshot = () => sql('SELECT id,warehouse_id,sku_id,on_hand_qty,locked_qty,version,updated_at FROM inventory ORDER BY id;' +
    'SELECT id,business_no,sku_id,quantity_before,quantity_change,quantity_after,operator_id FROM inventory_transaction ORDER BY id;' +
    'SELECT id,status,updated_at FROM purchase_order ORDER BY id;', database)

  assert.equal((await api('GET', prefix)).total, 0)
  await create(99999, 404)
  for (const body of [{}, { warehouseId: 0 }, { warehouseId: 1.5 }, { warehouseId: '1.5' }, { warehouseId: 1, remark: 'x'.repeat(501) }])
    await api('POST', prefix, body, 400)
  const draft = await api('POST', prefix, { warehouseId: 1, status: 'RECEIVED', createdBy: 999999, operatorId: 999999 })
  const id = draft.order.id
  assert.equal(draft.order.status, 'DRAFT')
  assert.equal(draft.order.createdBy, actor)
  assert.equal(typeof id, 'string')
  assert.deepEqual(draft.items, [])
  for (const name of ['approve', 'receive', 'complete']) await action(id, name, 409)
  await add(id, 'UNKNOWN', '1', 404)
  await add(id, 'DISABLED', '1', 409)
  for (const quantity of [0, -1, 1.5, '1.5', '9223372036854775808', null])
    await add(id, 'SKU-A', quantity, 400)
  await add(id, ' ', '1', 400)
  let edited = await add(id, ' SKU-A ', '5')
  const itemId = edited.items[0].id
  await add(id, 'SKU-A', '2', 409)
  await api('PUT', prefix + '/' + id + '/items/' + itemId, { quantity: '8' })
  await api('PUT', prefix + '/' + id + '/items/' + itemId, { quantity: '8' }) // unchanged quantity is valid
  await api('PUT', prefix + '/' + id + '/items/' + itemId, { quantity: -1 }, 400)
  const other = await create()
  await api('PUT', prefix + '/' + other.order.id + '/items/' + itemId, { quantity: '8' }, 404)
  await api('DELETE', prefix + '/' + other.order.id + '/items/' + itemId, undefined, 404)
  await api('DELETE', prefix + '/' + id + '/items/' + itemId)
  await api('DELETE', prefix + '/' + id + '/items/' + itemId, undefined, 404)
  edited = await add(id, 'SKU-A', '5')
  await add(id, 'SKU-B', '9')
  assert.equal(balance(1, 1), '10\t3\t7')
  assert.equal(balance(1, 2), '')
  sql('UPDATE product SET enabled=0 WHERE id=1;', database)
  await action(id, 'approve', 409)
  sql('UPDATE product SET enabled=1 WHERE id=1;', database)
  assert.equal((await action(id, 'approve')).order.status, 'APPROVED')
  for (const name of ['approve', 'cancel', 'complete']) await action(id, name, 409)
  await add(id, 'SKU-B', '1', 409)
  await api('PUT', prefix + '/' + id + '/items/' + edited.items[0].id, { quantity: '99' }, 409)
  await api('DELETE', prefix + '/' + id + '/items/' + edited.items[0].id, undefined, 409)
  // Approval fixes the commitment; later product disabling must not prevent fulfilment.
  sql('UPDATE product SET enabled=0 WHERE id=1;', database)
  const received = await action(id, 'receive')
  assert.equal(received.order.status, 'RECEIVED')
  assert.equal(balance(1, 1), '15\t3\t8')
  assert.equal(balance(1, 2), '9\t0\t1')
  assert.equal(received.transactions.length, 2)
  assert.deepEqual(received.transactions.map(t => [t.skuCode, t.quantityBefore, t.quantityChange, t.quantityAfter, t.operatorId, t.operationType]),
    [['SKU-A', '10', '5', '15', actor, 'PURCHASE_IN'], ['SKU-B', '0', '9', '9', actor, 'PURCHASE_IN']])
  assert.ok(received.transactions.every(t => t.businessNo === received.order.orderNo))
  assert.equal((await api('GET', '/inventory?warehouseId=1&skuCode=SKU-A')).records[0].availableQty, '12')
  const beforeDuplicate = snapshot()
  await action(id, 'receive', 409)
  assert.equal(snapshot(), beforeDuplicate)
  assert.equal((await action(id, 'complete')).order.status, 'COMPLETED')
  const completedSnapshot = snapshot()
  for (const name of ['approve', 'receive', 'complete', 'cancel']) await action(id, name, 409)
  assert.equal(snapshot(), completedSnapshot)
  sql('UPDATE product SET enabled=1 WHERE id=1;', database)
  assert.equal((await action(other.order.id, 'cancel')).order.status, 'CANCELLED')
  for (const name of ['approve', 'receive', 'complete', 'cancel']) await action(other.order.id, name, 409)
  await add(other.order.id, 'SKU-A', '1', 409)

  // Inject failures with database triggers ONLY in this disposable test schema.
  // First line updates an existing row + ledger; second line creates a row, then ledger insertion fails.
  sql('INSERT INTO inventory(warehouse_id,sku_id,on_hand_qty,locked_qty,version) VALUES(2,1,11,4,2);', database)
  const rollback = await approved(2, [['SKU-A', '3'], ['SKU-B', '7']])
  const rollbackId = rollback.order.id
  const beforeFailure = snapshot()
  sql("DELIMITER //\nCREATE TRIGGER test_reject_second_ledger BEFORE INSERT ON inventory_transaction FOR EACH ROW " +
    "BEGIN IF NEW.warehouse_id=2 AND NEW.sku_id=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced ledger failure'; END IF; END//\nDELIMITER ;", database)
  await action(rollbackId, 'receive', 500)
  assert.equal(snapshot(), beforeFailure)
  assert.equal((await detail(rollbackId)).order.status, 'APPROVED')
  assert.equal(balance(2, 2), '')
  sql('DROP TRIGGER test_reject_second_ledger;', database)
  console.log('PASS forced second-line ledger failure rolls back earlier balance, ledger, version, timestamp and new row')
  passed++

  sql("DELIMITER //\nCREATE TRIGGER test_reject_receipt_status BEFORE UPDATE ON purchase_order FOR EACH ROW " +
    "BEGIN IF NEW.id=" + rollbackId + " AND NEW.status='RECEIVED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced status failure'; END IF; END//\nDELIMITER ;", database)
  await action(rollbackId, 'receive', 500)
  assert.equal(snapshot(), beforeFailure)
  sql('DROP TRIGGER test_reject_receipt_status;', database)
  console.log('PASS forced final status failure rolls back all balances and ledgers')
  passed++
  assert.equal((await action(rollbackId, 'receive')).transactions.length, 2)

  // Concurrent delivery of the SAME action: exactly one receipt commits.
  const concurrent = await approved(1, [['SKU-A', '4']])
  const raceResponses = await Promise.all([1, 2].map(() => fetch(baseUrl + prefix + '/' + concurrent.order.id + '/receive',
    { method: 'POST', signal: AbortSignal.timeout(15000) })))
  assert.deepEqual(raceResponses.map(r => r.status).sort(), [200, 409])
  assert.equal(balance(1, 1), '19\t3\t9')
  assert.equal((await detail(concurrent.order.id)).transactions.length, 1)
  console.log('PASS concurrent duplicate receipt commits once')
  passed++

  // Different orders share stock. Cover both existing balance and concurrent first INSERT.
  for (const [skuCode, skuId, expectedBalance] of [['SKU-A', 1, '26\t3\t11'], ['RACE-NEW', 5, '7\t0\t2']]) {
    const left = await approved(1, [[skuCode, '3']])
    const right = await approved(1, [[skuCode, '4']])
    await Promise.all([action(left.order.id, 'receive'), action(right.order.id, 'receive')])
    assert.equal(balance(1, skuId), expectedBalance)
    assert.equal((await detail(left.order.id)).transactions.length, 1)
    assert.equal((await detail(right.order.id)).transactions.length, 1)
  }
  console.log('PASS concurrent different orders accumulate correctly for existing and missing balances')
  passed++

  // Quantity and version overflow must roll back preceding lines.
  const huge = await approved(1, [['BIG', '9223372036854775807']])
  const hugeReceived = await action(huge.order.id, 'receive')
  assert.equal(hugeReceived.transactions[0].quantityAfter, '9223372036854775807')
  const overflow = await approved(1, [['SKU-A', '1'], ['BIG', '1']])
  let overflowSnapshot = snapshot()
  await action(overflow.order.id, 'receive', 409)
  assert.equal(snapshot(), overflowSnapshot)
  sql('UPDATE inventory SET on_hand_qty=0,version=9223372036854775807 WHERE warehouse_id=1 AND sku_id=4;', database)
  overflowSnapshot = snapshot()
  await action(overflow.order.id, 'receive', 409)
  assert.equal(snapshot(), overflowSnapshot)
  console.log('PASS BIGINT precision and both overflow boundaries without partial writes')
  passed++

  // Read/filter/pagination contract, action surface, CORS and ledger permissions.
  const filtered = await api('GET', prefix + '?status=COMPLETED&warehouseId=1&orderNo=' + draft.order.orderNo)
  assert.equal(filtered.total, 1)
  assert.equal(filtered.records[0].id, id)
  assert.equal((await api('GET', prefix + '?orderNo=not-existing')).total, 0)
  const p1 = await api('GET', prefix + '?page=1&size=2')
  const p2 = await api('GET', prefix + '?page=2&size=2')
  assert.equal(new Set([...p1.records, ...p2.records].map(r => r.id)).size, 4)
  for (const query of ['page=0', 'size=101', 'status=unknown', 'warehouseId=-1'])
    await api('GET', prefix + '?' + query, undefined, 400)
  await api('GET', prefix + '/99999', undefined, 404)
  await action('99999', 'receive', 404)
  for (const [method, path] of [['PUT', prefix + '/' + id], ['PATCH', prefix + '/' + id + '/status'],
    ['DELETE', prefix + '/' + id], ['POST', '/inventory'], ['PUT', '/inventory/1'], ['POST', '/inventory-transactions'],
    ['PUT', '/inventory-transactions/1'], ['DELETE', '/inventory-transactions/1']])
    await api(method, path, { status: 'RECEIVED', onHandQty: 999 }, 403)
  for (const [origin, status] of [['http://localhost:5174', 200], ['https://untrusted.example', 403]]) {
    const response = await fetch(baseUrl + prefix + '/' + id + '/receive', { method: 'OPTIONS',
      headers: { Origin: origin, 'Access-Control-Request-Method': 'POST' }, signal: AbortSignal.timeout(10000) })
    assert.equal(response.status, status)
    assert.equal(response.headers.get('access-control-allow-credentials'), null)
    passed++
  }
  for (const statement of ['UPDATE inventory_transaction SET quantity_change=99 WHERE id=1;',
    'DELETE FROM inventory_transaction WHERE id=1;']) {
    sql(statement, database, 1142, true)
    sql(statement, database, 1644)
  }
  sql('DELETE FROM inventory WHERE id=1;', database, 1142, true)
  sql("INSERT INTO sys_user(username,password_hash,display_name) VALUES('not-allowed','!','test');", database, 1142, true)
  // Removing the configured audit identity must never fall back to arbitrary user IDs.
  sql("UPDATE sys_user SET enabled=1 WHERE username='stockflow-local-operator';", database)
  await create(1, 503)
  const missingActorSnapshot = snapshot()
  await action(overflow.order.id, 'receive', 503)
  assert.equal(snapshot(), missingActorSnapshot)
  sql("UPDATE sys_user SET enabled=0 WHERE username='stockflow-local-operator';", database)
  console.log('\nPASS ' + passed + ' purchase API/database checks (real MySQL, least-privilege application account).')
  if (process.argv.includes('--interactive')) {
    console.log('Browser fixture: warehouse WH-BROWSER, SKU BROWSER-SKU (no initial inventory).')
    console.log('Browser test API: ' + baseUrl + '\nPress Enter to stop the backend and remove the isolated database/user.')
    await new Promise(resolve => {
      process.stdin.resume()
      process.stdin.once('data', resolve)
      process.stdin.once('end', resolve)
      process.once('SIGINT', resolve)
      process.once('SIGTERM', resolve)
    })
    process.stdin.pause()
  }
} finally {
  await stopServer()
  try {
    if (userCreated) sql("DROP USER '" + username + "'@'localhost';")
  } finally {
    if (databaseCreated) sql('DROP DATABASE ' + database + ';')
  }
  console.log('Removed isolated purchase test resources.')
}
