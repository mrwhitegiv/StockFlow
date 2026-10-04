// Real MySQL tests, using only Node's standard library and the mysql CLI.
// Creates its own random database, and drops ONLY that database in finally.
import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

const mysql = process.env.MYSQL_BIN || 'mysql'
const username = process.env.DB_USERNAME || 'root'
if (process.env.DB_PASSWORD === undefined) {
  throw new Error('Set DB_USERNAME and DB_PASSWORD for a schema administrator before running.')
}
const database = `stockflow_schema_test_${randomBytes(8).toString('hex')}`
assert.match(database, /^stockflow_schema_test_[0-9a-f]{16}$/)
const connectionArgs = [
  '--no-defaults', '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
  '--connect-timeout=5', `--host=${process.env.DB_HOST || '127.0.0.1'}`,
  `--port=${process.env.DB_PORT || '3306'}`, `--user=${username}`,
]
const session = "SET SESSION time_zone='+00:00'; SET SESSION sql_mode='STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE';\n"
function execute(sql, selectedDatabase = database) {
  const result = spawnSync(mysql, [
    ...connectionArgs, ...(selectedDatabase ? [`--database=${selectedDatabase}`] : []),
  ], {
    input: session + sql,
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD },
    timeout: 30000,
    maxBuffer: 1024 * 1024,
  })
  if (result.error) throw result.error
  return result
}
function query(sql, selectedDatabase = database) {
  const result = execute(sql, selectedDatabase)
  assert.equal(result.status, 0, result.stderr)
  return result.stdout.replace(/\r\n/g, '\n').trim()
}
let passed = 0
function test(name, action) {
  action()
  passed++
  console.log(`PASS ${name}`)
}
function rejects(name, sql, errorCode) {
  test(name, () => {
    const result = execute(`START TRANSACTION;\n${sql};\nROLLBACK;`)
    assert.notEqual(result.status, 0, 'Expected MySQL to reject this operation')
    assert.match(result.stderr, new RegExp(`ERROR ${errorCode} \\(`), result.stderr)
  })
}
function accepts(name, sql) {
  test(name, () => query(`START TRANSACTION;\n${sql};\nROLLBACK;`))
}
const expectedTables = [
  'sys_user', 'sys_role', 'sys_user_role', 'category', 'product', 'sku', 'warehouse',
  'inventory', 'inventory_transaction', 'purchase_order', 'purchase_order_item',
  'sales_order', 'sales_order_item', 'stock_transfer', 'stock_transfer_item',
].sort()

let created = false
try {
  const version = query('SELECT VERSION();', null)
  assert.match(version, /^8\./, 'Run this acceptance suite on MySQL 8')
  const [major, minor, patch] = version.split(/[.-]/).map(Number)
  assert.ok(major === 8 && (minor > 0 || patch >= 16), 'MySQL 8.0.16+ is required')
  query(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`, null)
  created = true
  console.log(`Testing MySQL ${version} in isolated database ${database}`)
  test('baseline initializes an empty database', () => {
    query(readFileSync(new URL('./001_init_schema.sql', import.meta.url), 'utf8'))
  })
  test('exactly the 15 expected tables exist, all using InnoDB', () => {
    assert.deepEqual(query('SHOW TABLES;').split('\n').sort(), expectedTables)
    assert.equal(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB';"), '15')
  })
  test('baseline contains no seed data', () => {
    for (const table of expectedTables) assert.equal(query(`SELECT COUNT(*) FROM ${table};`), '0')
  })
  test('product and sku have no stock fields; available quantity is not persisted', () => {
    assert.equal(query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name IN ('product','sku') AND column_name REGEXP 'qty|quantity|stock') OR column_name='available_qty');"), '0')
  })
  test('timestamps and optimistic-lock version are present', () => {
    assert.equal(query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='created_at';"), '15')
    assert.equal(query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='updated_at';"), '13')
    assert.equal(query("SELECT column_default FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory' AND column_name='version';"), '0')
  })

  // Deliberately synthetic fixtures, only inside the isolated test database.
  query(`
    INSERT INTO sys_user (id,username,password_hash,display_name) VALUES (1,'schema-test','NOT_A_USABLE_PASSWORD_HASH','测试用户');
    INSERT INTO sys_role (id,code,name) VALUES (1,'ADMIN','管理员');
    INSERT INTO sys_user_role (user_id,role_id) VALUES (1,1);
    INSERT INTO category (id,name) VALUES (1,'手机');
    INSERT INTO product (id,category_id,name) VALUES (1,1,'示例手机');
    INSERT INTO sku (id,product_id,code,name) VALUES (1,1,'PHONE-BLACK-256','黑色 / 256GB'), (2,1,'PHONE-WHITE-256','白色 / 256GB');
    INSERT INTO warehouse (id,code,name) VALUES (1,'GZ','广州仓'), (2,'SZ','深圳仓');
    INSERT INTO inventory (id,warehouse_id,sku_id,on_hand_qty,locked_qty) VALUES (1,1,1,10,3), (2,2,1,0,0);
    INSERT INTO purchase_order (id,order_no,warehouse_id,created_by) VALUES (1,'PO-001',1,1);
    INSERT INTO purchase_order_item (id,purchase_order_id,sku_id,quantity) VALUES (1,1,1,10);
    INSERT INTO sales_order (id,order_no,warehouse_id,created_by) VALUES (1,'SO-001',1,1);
    INSERT INTO sales_order_item (id,sales_order_id,sku_id,quantity) VALUES (1,1,1,3);
    INSERT INTO stock_transfer (id,transfer_no,source_warehouse_id,destination_warehouse_id,created_by) VALUES (1,'TR-001',1,2,1);
    INSERT INTO stock_transfer_item (id,stock_transfer_id,sku_id,quantity) VALUES (1,1,1,2);
    INSERT INTO inventory_transaction
      (warehouse_id,sku_id,business_type,business_no,operation_type,quantity_type,quantity_before,quantity_change,quantity_after,operator_id)
      VALUES (1,1,'PURCHASE','PO-001','PURCHASE_IN','ON_HAND',0,10,10,1),
             (1,1,'SALES','SO-001','SALES_LOCK','LOCKED',0,3,3,1);
  `)
  test('valid relationships and UTF-8 data are stored', () => {
    assert.equal(query('SELECT name FROM warehouse WHERE id=1;'), '广州仓')
    assert.equal(query('SELECT on_hand_qty-locked_qty FROM inventory WHERE id=1;'), '7')
  })
  rejects('warehouse + SKU must be unique', 'INSERT INTO inventory (warehouse_id,sku_id) VALUES (1,1)', 1062)
  accepts('one SKU may have balances in different warehouses', 'INSERT INTO inventory (warehouse_id,sku_id) VALUES (1,2),(2,2)')
  rejects('physical quantity cannot be negative', 'UPDATE inventory SET on_hand_qty=-1 WHERE id=1', 3819)
  rejects('locked quantity cannot be negative', 'UPDATE inventory SET locked_qty=-1 WHERE id=1', 3819)
  rejects('locked quantity cannot exceed physical quantity', 'UPDATE inventory SET locked_qty=11 WHERE id=1', 3819)
  rejects('reducing physical below locked quantity is rejected', 'UPDATE inventory SET on_hand_qty=2 WHERE id=1', 3819)
  rejects('version cannot be negative', 'UPDATE inventory SET version=-1 WHERE id=1', 3819)
  accepts('zero balance is allowed', 'UPDATE inventory SET on_hand_qty=0,locked_qty=0 WHERE id=1')
  rejects('inventory quantities cannot be null', 'UPDATE inventory SET locked_qty=NULL WHERE id=1', 1048)
  rejects('user-role membership must be unique', 'INSERT INTO sys_user_role (user_id,role_id) VALUES (1,1)', 1062)
  rejects('username must be unique', "INSERT INTO sys_user (username,password_hash,display_name) VALUES ('schema-test','x','duplicate')", 1062)
  rejects('SKU code must be unique', "INSERT INTO sku (product_id,code,name) VALUES (1,'PHONE-BLACK-256','duplicate')", 1062)
  rejects('warehouse code must be unique', "INSERT INTO warehouse (code,name) VALUES ('GZ','duplicate')", 1062)
  rejects('SKU code cannot be blank', "UPDATE sku SET code='   ' WHERE id=1", 3819)
  rejects('transfer source and destination must differ', 'UPDATE stock_transfer SET destination_warehouse_id=1 WHERE id=1', 3819)

  const orders = [
    ['purchase_order', 'order_no', 'purchase_order_item', 'purchase_order_id', ['DRAFT','APPROVED','RECEIVED','COMPLETED','CANCELLED']],
    ['sales_order', 'order_no', 'sales_order_item', 'sales_order_id', ['DRAFT','CONFIRMED','SHIPPED','COMPLETED','CANCELLED']],
    ['stock_transfer', 'transfer_no', 'stock_transfer_item', 'stock_transfer_id', ['DRAFT','APPROVED','OUTBOUND','INBOUND','COMPLETED','CANCELLED']],
  ]
  for (const [table, numberColumn, item, parentColumn, statuses] of orders) {
    test(`${table} defaults to DRAFT`, () => assert.equal(query(`SELECT status FROM ${table} WHERE id=1;`), 'DRAFT'))
    accepts(`${table} accepts exactly documented status values`, statuses.map(status => `UPDATE ${table} SET status='${status}' WHERE id=1`).join(';'))
    rejects(`${table} rejects unknown status`, `UPDATE ${table} SET status='UNKNOWN' WHERE id=1`, 3819)
    rejects(`${table} rejects lowercase status`, `UPDATE ${table} SET status='draft' WHERE id=1`, 3819)
    accepts(`${table} second distinct number`, table === 'stock_transfer'
      ? "INSERT INTO stock_transfer (id,transfer_no,source_warehouse_id,destination_warehouse_id,created_by) VALUES (2,'TR-002',1,2,1)"
      : `INSERT INTO ${table} (id,${numberColumn},warehouse_id,created_by) VALUES (2,'ORDER-002',1,1)`)
    rejects(`${table} duplicate number`, table === 'stock_transfer'
      ? "INSERT INTO stock_transfer (transfer_no,source_warehouse_id,destination_warehouse_id,created_by) VALUES ('TR-001',1,2,1)"
      : `INSERT INTO ${table} (${numberColumn},warehouse_id,created_by) SELECT ${numberColumn},warehouse_id,created_by FROM ${table} WHERE id=1`, 1062)
    rejects(`${item} duplicate SKU in same order`, `INSERT INTO ${item} (${parentColumn},sku_id,quantity) VALUES (1,1,1)`, 1062)
    rejects(`${item} zero quantity`, `UPDATE ${item} SET quantity=0 WHERE id=1`, 3819)
    rejects(`${item} negative quantity`, `UPDATE ${item} SET quantity=-1 WHERE id=1`, 3819)
    rejects(`${table} cannot delete a referenced order`, `DELETE FROM ${table} WHERE id=1`, 1451)
  }

  // Exercise every actual FK, including both columns of the composite ledger FK.
  const foreignKeys = query(`SELECT table_name,column_name FROM information_schema.key_column_usage
    WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL ORDER BY table_name,ordinal_position;`).split('\n')
  test('all expected foreign key columns exist', () => assert.equal(foreignKeys.length, 22))
  for (const row of foreignKeys) {
    const [table, column] = row.split('\t')
    // Ledger has immutable rows; exercise its FK checks on INSERT instead of UPDATE.
    if (table === 'inventory_transaction') {
      const values = { warehouse_id: 1, sku_id: 1, operator_id: 1, [column]: 999999 }
      rejects(`${table}.${column} rejects an absent parent`, `INSERT INTO inventory_transaction
        (warehouse_id,sku_id,business_type,business_no,operation_type,quantity_type,quantity_before,quantity_change,quantity_after,operator_id)
        VALUES (${values.warehouse_id},${values.sku_id},'PURCHASE','PO-001','PURCHASE_IN','ON_HAND',0,1,1,${values.operator_id})`, 1452)
    } else if (table === 'inventory') {
      // Row 2 has no ledger children, so this isolates the parent-existence check.
      rejects(`${table}.${column} rejects an absent parent`, `UPDATE inventory SET ${column}=999999 WHERE id=2`, 1452)
    } else {
      rejects(`${table}.${column} rejects an absent parent`, `UPDATE ${table} SET ${column}=999999 LIMIT 1`, 1452)
    }
  }
  rejects('referenced SKU cannot be deleted', 'DELETE FROM sku WHERE id=1', 1451)
  rejects('referenced user cannot be deleted', 'DELETE FROM sys_user WHERE id=1', 1451)
  rejects('ledger prevents deleting inventory history', 'DELETE FROM inventory WHERE id=1', 1451)
  rejects('ledger rows cannot be updated', "UPDATE inventory_transaction SET remark='edited' WHERE id=1", 1644)
  rejects('ledger rows cannot be deleted', 'DELETE FROM inventory_transaction WHERE id=1', 1644)
  function ledger({ business = 'PURCHASE', operation = 'PURCHASE_IN', type = 'ON_HAND', before = 0, change = 1, after = 1 } = {}) {
    return `INSERT INTO inventory_transaction (warehouse_id,sku_id,business_type,business_no,operation_type,quantity_type,quantity_before,quantity_change,quantity_after,operator_id)
      VALUES (1,1,'${business}','TEST-NO','${operation}','${type}',${before},${change},${after},1)`
  }
  rejects('ledger arithmetic must balance', ledger({ after: 2 }), 3819)
  rejects('ledger cannot have negative balances', ledger({ before: -1, after: 0 }), 3819)
  rejects('ledger cannot contain a zero delta', ledger({ change: 0, after: 0 }), 3819)
  rejects('ledger rejects mismatched business and operation', ledger({ business: 'SALES' }), 3819)
  rejects('ledger rejects unknown operations', ledger({ operation: 'UNKNOWN' }), 3819)
  rejects('sales lock must change LOCKED balance', ledger({ business: 'SALES', operation: 'SALES_LOCK' }), 3819)
  rejects('purchase receipt must increase stock', ledger({ before: 2, change: -1 }), 3819)
  for (const entry of [
    { business: 'SALES', operation: 'SALES_LOCK', type: 'LOCKED' },
    { business: 'SALES', operation: 'SALES_UNLOCK', type: 'LOCKED', before: 2, change: -1 },
    { business: 'SALES', operation: 'SALES_OUT', type: 'ON_HAND', before: 2, change: -1 },
    { business: 'SALES', operation: 'SALES_OUT', type: 'LOCKED', before: 2, change: -1 },
    { business: 'TRANSFER', operation: 'TRANSFER_OUT', before: 2, change: -1 },
    { business: 'TRANSFER', operation: 'TRANSFER_IN' },
    { business: 'ADJUSTMENT', operation: 'ADJUSTMENT' },
    { business: 'ADJUSTMENT', operation: 'ADJUSTMENT', before: 2, change: -1 },
  ]) accepts(`ledger supports ${entry.operation}/${entry.type || 'ON_HAND'}/${entry.change || 1}`, ledger(entry))
  test('re-running baseline fails instead of hiding existing tables', () => {
    const result = execute(readFileSync(new URL('./001_init_schema.sql', import.meta.url), 'utf8'))
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /ERROR 1050 \(/)
    assert.equal(query('SELECT COUNT(*) FROM inventory_transaction;'), '2')
  })
  console.log(`SUCCESS: ${passed} checks passed`)
} finally {
  if (created) {
    query(`DROP DATABASE ${database};`, null)
    console.log(`Removed test database ${database}`)
  }
}
