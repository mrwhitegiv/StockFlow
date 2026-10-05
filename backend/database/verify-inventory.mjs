// Runs the packaged backend against an isolated MySQL database and SELECT-only application user.
// Usage: mvn clean package (in backend), then node backend/database/verify-inventory.mjs
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
const database = `stockflow_inventory_test_${suffix}`
const username = `sf_inv_${suffix}`
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
    output = (output + chunk.toString()).slice(-16000)
    const match = output.match(/Tomcat started on port (\d+)/)
    if (match) baseUrl = 'http://127.0.0.1:' + match[1] + '/api'
  }
  server.stdout.on('data', capture)
  server.stderr.on('data', capture)
  for (let attempt = 0; !baseUrl && !spawnFailed && attempt < 120 && server.exitCode === null; attempt++) await delay(500)
  assert.ok(baseUrl, 'Backend did not start:\n' + output)
  assert.equal((await api('GET', '/health')).status, 'UP')
  assert.deepEqual(await api('GET', '/inventory'), { records: [], total: 0, page: 1, size: 10 })

  // Fixtures exist ONLY in this disposable database; the application has SELECT permission only.
  sql("INSERT INTO category(id,name) VALUES(1,'测试设备');" +
    "INSERT INTO product(id,category_id,name,enabled) VALUES(1,1,'学习手机',0),(2,1,'办公用品',1);" +
    "INSERT INTO sku(id,product_id,code,name) VALUES(1,1,'PHONE-BLACK','黑色 / 256GB'),(2,1,'PHONE-WHITE','白色 / 512GB'),(3,2,'KEYBOARD','机械键盘'),(4,2,'NO-STOCK','尚无库存'),(9007199254740993,2,'BIGINT','大整数边界');" +
    "INSERT INTO warehouse(id,code,name) VALUES(1,'WH-GZ','广州仓'),(2,'WH-SZ','深圳仓'),(3,'WH-EMPTY','尚无库存仓');" +
    "INSERT INTO inventory(id,warehouse_id,sku_id,on_hand_qty,locked_qty,version) VALUES(1,1,1,10,3,0),(2,2,1,20,0,2),(3,1,2,5,5,0),(4,2,2,0,0,1),(9007199254740993,2,9007199254740993,9223372036854775807,1,9223372036854775807);", database)
  for (let id = 10; id < 17; id++)
    sql("INSERT INTO warehouse(id,code,name) VALUES(" + id + ",'WH-TEST-" + id + "','测试仓" + id + "');" +
      'INSERT INTO inventory(id,warehouse_id,sku_id,on_hand_qty,locked_qty) VALUES(' + id + ',' + id + ',3,30,10);', database)
  const snapshotQuery = 'SELECT id,warehouse_id,sku_id,on_hand_qty,locked_qty,version,updated_at FROM inventory ORDER BY id;'
  const snapshot = sql(snapshotQuery, database)
  const first = await api('GET', '/inventory')
  const second = await api('GET', '/inventory?page=2&size=10')
  assert.equal(first.total, 12)
  assert.equal(first.records.length, 10)
  assert.equal(second.records.length, 2)
  assert.equal(new Set([...first.records, ...second.records].map(row => row.id)).size, 12)
  assert.equal(first.records[0].id, '9007199254740993')
  const warehouse = await api('GET', '/inventory?warehouseId=1')
  assert.equal(warehouse.total, 2)
  assert.ok(warehouse.records.every(row => row.warehouseId === '1'))
  assert.equal((await api('GET', '/inventory?skuId=1')).total, 2)
  const combined = await api('GET', '/inventory?warehouseId=1&skuId=1')
  assert.equal(combined.total, 1)
  assert.equal(combined.records[0].availableQty, '7')
  assert.equal(combined.records[0].productName, '学习手机') // Disabled products retain their stock.
  assert.equal((await api('GET', '/inventory?skuCode=PHONE-BLACK')).total, 2)
  assert.equal((await api('GET', '/inventory?warehouseId=1&skuCode=PHONE-BLACK')).total, 1)
  assert.equal((await api('GET', '/inventory?skuCode=%20PHONE-BLACK%20')).total, 2)
  for (const code of ['phone-black', 'PHONE', "' OR 1=1 --"])
    assert.equal((await api('GET', '/inventory?skuCode=' + encodeURIComponent(code))).total, 0)
  assert.equal((await api('GET', '/inventory?skuId=1&skuCode=PHONE-WHITE')).total, 0)
  const big = await api('GET', '/inventory/9007199254740993')
  assert.equal(big.skuId, '9007199254740993')
  assert.equal(big.onHandQty, '9223372036854775807')
  assert.equal(big.availableQty, '9223372036854775806')
  assert.equal(big.version, '9223372036854775807')
  assert.equal((await api('GET', '/inventory?skuId=9007199254740993')).total, 1)
  assert.equal((await api('GET', '/inventory/3')).availableQty, '0')
  assert.equal((await api('GET', '/inventory/4')).onHandQty, '0')
  for (const filter of ['warehouseId=3', 'skuId=4', 'warehouseId=99999', 'page=999'])
    assert.deepEqual((await api('GET', '/inventory?' + filter)).records, [])
  assert.equal((await api('GET', '/inventory?skuCode=%20%20')).total, 12)
  await api('GET', '/inventory/99999', undefined, 404)
  for (const query of ['warehouseId=0', 'warehouseId=-1', 'skuId=-1', 'skuId=text', 'skuId=9223372036854775808', 'page=0', 'page=1.5', 'size=0', 'size=101', 'skuCode=' + 'x'.repeat(65)])
    await api('GET', '/inventory?' + query, undefined, 400)
  for (const id of ['0', 'text']) await api('GET', '/inventory/' + id, undefined, 400)
  for (const method of ['POST', 'PUT', 'PATCH', 'DELETE'])
    for (const path of ['/inventory', '/inventory/1'])
      await api(method, path, { onHandQty: -1, lockedQty: 999 }, 403)

  sql('INSERT INTO inventory(warehouse_id,sku_id) VALUES(1,1);', database, 1062)
  for (const quantities of ['-1,0,0', '1,-1,0', '1,2,0', '0,0,-1'])
    sql('INSERT INTO inventory(warehouse_id,sku_id,on_hand_qty,locked_qty,version) VALUES(3,4,' + quantities + ');', database, 3819)
  for (const statement of ['INSERT INTO inventory(warehouse_id,sku_id) VALUES(3,4);', 'UPDATE inventory SET on_hand_qty=99 WHERE id=1;', 'DELETE FROM inventory WHERE id=1;'])
    sql(statement, database, 1142, true)
  assert.equal(sql(snapshotQuery, database), snapshot)
  assert.equal(sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory' AND column_name='available_qty';", database), '0')
  assert.equal(sql('SELECT COUNT(*) FROM inventory_transaction;', database), '0')
  passed++
  console.log('PASS stock, timestamps and versions unchanged; no stored available quantity or ledger writes')

  for (const [origin, method, status] of [['http://localhost:5174', 'GET', 200], ['http://localhost:5174', 'PUT', 403], ['https://untrusted.example', 'GET', 403]]) {
    const response = await fetch(baseUrl + '/inventory', { method: 'OPTIONS',
      headers: { Origin: origin, 'Access-Control-Request-Method': method }, signal: AbortSignal.timeout(10000) })
    assert.equal(response.status, status)
    if (status === 200) {
      assert.equal(response.headers.get('access-control-allow-origin'), origin)
      assert.equal(response.headers.get('access-control-allow-credentials'), null)
    }
    passed++
    console.log('PASS inventory CORS ' + method + ' -> ' + status)
  }
  console.log('\nPASS ' + passed + ' inventory API/database checks (real MySQL, SELECT-only application account).')
  if (process.argv.includes('--interactive')) {
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
  console.log('Removed isolated inventory test resources.')
}
