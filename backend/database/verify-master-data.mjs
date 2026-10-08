// Runs the packaged backend against an isolated MySQL database and least-privilege user.
// Usage: mvn clean package (in backend), then node backend/database/verify-master-data.mjs
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
const database = `stockflow_api_test_${suffix}`
const username = `sf_test_${suffix}`
const password = randomBytes(24).toString('hex')
let databaseCreated = false
let userCreated = false
let server
let serverExited
let baseUrl
let output = ''
let passed = 0

function sql(statement, selectedDatabase) {
  const result = spawnSync(mysql, ['--no-defaults', '--batch', '--skip-column-names',
    '--default-character-set=utf8mb4', '--connect-timeout=5', `--host=${host}`,
    `--port=${port}`, `--user=${process.env.DB_USERNAME || 'root'}`,
    ...(selectedDatabase ? [`--database=${selectedDatabase}`] : [])],
  { input: statement, encoding: 'utf8', timeout: 30000,
    env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD }, maxBuffer: 1024 * 1024 })
  if (result.error) throw result.error
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
  console.log(`Creating isolated database ${database}`)
  sql(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`)
  databaseCreated = true
  sql(readFileSync(new URL('./001_init_schema.sql', import.meta.url), 'utf8'), database)
  sql("INSERT INTO category(name) VALUES ('Migration fixture'); INSERT INTO product(category_id,name,description) VALUES(1,'Existing product','Preserved');", database)
  sql(readFileSync(new URL('./002_product_enabled.sql', import.meta.url), 'utf8'), database)
  assert.equal(sql("SELECT CONCAT(name,':',description,':',enabled) FROM product WHERE id=1;", database), 'Existing product:Preserved:1')
  passed++
  console.log('PASS migration preserves existing products and enables them')
  sql(`CREATE USER '${username}'@'localhost' IDENTIFIED BY '${password}';`)
  userCreated = true
  for (const table of ['category', 'product', 'sku', 'warehouse']) {
    const privileges = table === 'product' ? 'SELECT,INSERT,UPDATE' : 'SELECT,INSERT,UPDATE,DELETE'
    sql(`GRANT ${privileges} ON ${database}.${table} TO '${username}'@'localhost';`)
  }
  const jar = fileURLToPath(new URL('../target/stockflow-0.0.1-SNAPSHOT.jar', import.meta.url))
  const java = process.env.JAVA_BIN || (process.env.JAVA_HOME ? `${process.env.JAVA_HOME}/bin/java` : 'java')
  server = spawn(java, ['-jar', jar], {
    windowsHide: true,
    env: {
      ...process.env, MYSQL_PWD: '', DB_USERNAME: username, DB_PASSWORD: password, DB_NAME: database,
      SERVER_ADDRESS: '127.0.0.1', SERVER_PORT: '0',
      SPRING_DATASOURCE_URL: `jdbc:mysql://${host}:${port}/${database}?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=3000`,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  serverExited = new Promise(resolve => { server.once('exit', resolve); server.once('error', resolve) })
  let spawnFailed = false
  server.on('error', (error) => { spawnFailed = true; output += error.message })
  const capture = (chunk) => {
    output = (output + chunk.toString()).slice(-16000)
    const match = output.match(/Tomcat started on port (\d+)/)
    if (match) baseUrl = `http://127.0.0.1:${match[1]}/api`
  }
  server.stdout.on('data', capture)
  server.stderr.on('data', capture)
  for (let attempt = 0; !baseUrl && !spawnFailed && attempt < 120 && server.exitCode === null; attempt++) await delay(500)
  assert.ok(baseUrl, 'Backend did not start:\n' + output)
  assert.equal((await api('GET', '/health')).status, 'UP')

  const cat = await api('POST', '/categories', { name: '  手机  ' })
  assert.equal(cat.name, '手机')
  await api('POST', '/categories', { name: '手机' }, 409)
  await api('POST', '/categories', { name: '   ' }, 400)
  await api('POST', '/categories', { name: 'x'.repeat(101) }, 400)
  await api('PUT', `/categories/${cat.id}`, { name: '移动设备' })
  assert.ok((await api('GET', '/categories')).some(c => c.name === '移动设备'))

  const product = await api('POST', '/products', { categoryId: cat.id, name: '测试手机', description: '说明' })
  assert.equal(product.enabled, true)
  assert.ok(product.createdAt)
  assert.ok(product.updatedAt)
  await api('POST', '/products', { categoryId: 999999, name: 'No category' }, 404)
  await api('POST', '/products', { categoryId: cat.id, name: '' }, 400)
  await api('PUT', `/products/${product.id}`, { categoryId: cat.id, name: '新手机', description: '' })
  assert.equal((await api('GET', `/products/${product.id}`)).description, null)
  await api('DELETE', `/categories/${cat.id}`, undefined, 409)
  const other = await api('POST', '/products', { categoryId: cat.id, name: '另一手机' })
  const first = await api('GET', `/products?categoryId=${cat.id}&page=1&size=1`)
  const second = await api('GET', `/products?categoryId=${cat.id}&page=2&size=1`)
  assert.equal(first.total, 2)
  assert.equal(first.records.length, 1)
  assert.notEqual(first.records[0].id, second.records[0].id)
  assert.equal((await api('GET', '/products?keyword=' + encodeURIComponent('新手机'))).total, 1)
  assert.equal((await api('GET', '/products?keyword=%25')).total, 0)
  assert.equal((await api('GET', '/products?page=999')).records.length, 0)
  for (const path of ['/products?page=0', '/products?size=101', '/products?size=no', '/products/-1']) await api('GET', path, undefined, 400)
  await api('GET', '/products/999999', undefined, 404)
  await api('PATCH', `/products/${product.id}/enabled`, {}, 400)

  const sku = await api('POST', `/products/${product.id}/skus`, { code: 'PHONE-BLACK', name: '黑色 / 256GB' })
  await api('POST', `/products/${other.id}/skus`, { code: 'PHONE-BLACK', name: '重复编码' }, 409)
  await api('POST', `/products/${product.id}/skus`, { code: '非法 code', name: '坏编码' }, 400)
  const sku2 = await api('POST', `/products/${product.id}/skus`, { code: 'PHONE-WHITE', name: '白色' })
  await api('PUT', `/skus/${sku2.id}`, { code: 'PHONE-BLACK', name: '重复修改' }, 409)
  await api('PUT', `/skus/${sku.id}`, { code: 'PHONE-BLACK', name: '黑色 / 512GB' })
  assert.equal((await api('GET', `/skus/${sku.id}`)).name, '黑色 / 512GB')
  const skuPage = await api('GET', `/products/${product.id}/skus?size=1`)
  const skuPage2 = await api('GET', `/products/${product.id}/skus?size=1&page=2`)
  assert.equal(skuPage.total, 2)
  assert.notEqual(skuPage.records[0].id, skuPage2.records[0].id)
  assert.equal((await api('GET', `/products/${product.id}/skus?keyword=BLACK`)).total, 1)
  assert.equal((await api('GET', `/products/${other.id}/skus`)).total, 0)
  await api('GET', '/products/999999/skus', undefined, 404)
  await api('GET', `/products/${product.id}/skus?size=0`, undefined, 400)
  await api('PATCH', `/products/${product.id}/enabled`, { enabled: false })
  assert.equal((await api('GET', '/products?enabled=false')).total, 1)
  await api('POST', `/products/${product.id}/skus`, { code: 'DISABLED', name: 'Blocked' }, 409)
  await api('PUT', `/skus/${sku.id}`, { code: 'PHONE-BLACK', name: 'Blocked' }, 409)
  assert.equal((await api('GET', `/products/${product.id}/skus`)).total, 2)
  await api('PUT', `/products/${product.id}`, { categoryId: cat.id, name: '禁用商品编辑' })
  assert.equal((await api('GET', `/products/${product.id}`)).enabled, false)
  await api('PATCH', `/products/${product.id}/enabled`, { enabled: true })

  const warehouse = await api('POST', '/warehouses', { code: 'WH-GZ', name: '广州仓', address: '旧地址' })
  await api('POST', '/warehouses', { code: 'WH-GZ', name: '重复仓库' }, 409)
  await api('PUT', `/warehouses/${warehouse.id}`, { code: 'WH-GZ', name: '广州仓更新', address: '' })
  assert.equal((await api('GET', `/warehouses/${warehouse.id}`)).address, null)
  assert.equal((await api('GET', '/warehouses')).length, 1)
  // Real FK protection, without exposing any inventory mutation API.
  sql(`INSERT INTO inventory(warehouse_id,sku_id) VALUES(${warehouse.id},${sku.id});`, database)
  await api('DELETE', `/warehouses/${warehouse.id}`, undefined, 409)
  await api('DELETE', `/skus/${sku.id}`, undefined, 409)
  sql('DELETE FROM inventory;', database)
  await api('DELETE', `/skus/${sku.id}`)
  await api('GET', `/skus/${sku.id}`, undefined, 404)
  await api('DELETE', `/skus/${sku.id}`, undefined, 404)
  await api('DELETE', `/warehouses/${warehouse.id}`)
  await api('GET', `/warehouses/${warehouse.id}`, undefined, 404)
  const unused = await api('POST', '/categories', { name: '可删除' })
  await api('DELETE', `/categories/${unused.id}`)
  await api('GET', `/categories/${unused.id}`, undefined, 404)
  const responses = await Promise.all([1, 2].map(() => fetch(baseUrl + '/warehouses', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: 'CONCURRENT', name: '并发唯一性' }), signal: AbortSignal.timeout(10000),
  })))
  assert.deepEqual(responses.map(r => r.status).sort(), [200, 409])
  passed++
  console.log('PASS concurrent duplicate warehouse writes -> 200 / 409')
  const preflight = await fetch(baseUrl + '/products', { method: 'OPTIONS',
    headers: { Origin: 'http://localhost:5173', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type' } })
  assert.equal(preflight.status, 200)
  assert.equal(preflight.headers.get('Access-Control-Allow-Origin'), 'http://localhost:5173')
  passed++
  await api('PUT', '/inventory/1', { onHandQty: 999 }, 403)
  assert.equal(sql('SELECT COUNT(*) FROM inventory;', database), '0')
  assert.equal(sql('SELECT COUNT(*) FROM inventory_transaction;', database), '0')
  console.log(`SUCCESS: ${passed} checks passed against real HTTP + MySQL`)
  if (process.argv.includes('--interactive')) {
    console.log('Browser test API: ' + baseUrl)
    console.log('Press Enter after browser verification to stop the backend and clean the test database.')
    await new Promise(resolve => {
      process.stdin.once('data', resolve)
      process.stdin.once('end', resolve)
      process.once('SIGINT', resolve)
      process.once('SIGTERM', resolve)
      process.stdin.resume()
    })
    process.stdin.pause()
  }
} finally {
  try {
    await stopServer()
  } finally {
    try {
      if (userCreated) sql(`DROP USER '${username}'@'localhost';`)
    } finally {
      if (databaseCreated) sql(`DROP DATABASE ${database};`)
    }
  }
  console.log('Removed the isolated test user and database')
}
