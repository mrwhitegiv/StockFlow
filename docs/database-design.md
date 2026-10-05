# MVP 数据库设计（Issue #2）

本文记录 Issue #2 基线。Issue #3 通过 [002 升级脚本](../backend/database/002_product_enabled.sql) 增加 product.enabled；
当前接口与账户写入授权见 [基础资料管理说明](master-data.md)。下文的“本次”指 Issue #2。

- 建表脚本：[001_init_schema.sql](../backend/database/001_init_schema.sql)
- 关系图：[database-er.md](database-er.md)
- 自动验收：[verify-schema.mjs](../backend/database/verify-schema.mjs)
- 依据：[产品需求](product-requirements.md)、[Issue #2](https://github.com/mrwhitegiv/StockFlow/issues/2)

## 1. 表的职责

| 表 | 保存什么 | 关键关系 / 约束 |
| --- | --- | --- |
| sys_user | 用户名、密码哈希、显示名、启用状态 | username 唯一；不包含任何预置账户 |
| sys_role | 角色编码和名称 | code 唯一；不预置完整权限系统 |
| sys_user_role | 用户与角色的多对多关系 | 联合主键 (user_id, role_id) |
| category | 商品分类 | 当前采用单层分类，name 唯一 |
| product | 商品概念，如某款手机 | 一个分类下有多个商品，不存库存 |
| sku | 可交易的具体规格，如黑色 / 256GB | 属于一个商品，code 全局唯一，不存库存 |
| warehouse | 仓库编码、名称和可选地址 | code 唯一 |
| inventory | 一个 SKU 在一个仓库的当前余额 | (warehouse_id, sku_id) 唯一 |
| inventory_transaction | 每次库存余额变化的历史 | 指向存在的库存组合及操作人，只追加 |
| purchase_order | 采购单号、入库仓库、状态、创建人 | order_no 唯一 |
| purchase_order_item | 采购单上的 SKU 和数量 | 同一订单同一 SKU 只出现一次 |
| sales_order | 销售单号、出库仓库、状态、创建人 | order_no 唯一 |
| sales_order_item | 销售单上的 SKU 和数量 | 同一订单同一 SKU 只出现一次 |
| stock_transfer | 调拨单号、源仓、目标仓、状态、创建人 | transfer_no 唯一；源仓不能等于目标仓 |
| stock_transfer_item | 调拨单上的 SKU 和数量 | 同一调拨单同一 SKU 只出现一次 |

订单主表描述一次业务，明细表描述涉及哪些 SKU 及数量。仓库放在主表，因此一张采购/销售单只对应一个仓库。
订单可以先保存为没有明细的草稿；提交前必须有明细，这属于后续 Service 的检查。

## 2. 本次明确的建模选择

- 主键统一为自增的有符号 BIGINT，便于对应 Java Long；关联表 sys_user_role 使用自然联合主键。
- 数量使用 BIGINT，MVP 按整数件数计量，不支持称重或小数库存。后续接口也需要整数验证，不能依赖 MySQL 类型转换拒绝所有小数输入。
- 可变记录有 created_at / updated_at；角色关联只有创建时间；不可变流水只有创建时间，不设置 updated_at。
- 时间使用 DATETIME(6)，按 UTC 写入。初始化脚本及验收程序设置当前会话为 UTC；未来每个写入连接也必须统一会话时区，DATETIME 本身不携带时区。
- 文本采用 utf8mb4_0900_ai_ci；用户名和分类名称的唯一比较不区分大小写/重音。机器编码、单号和状态使用 ascii_bin，按大小写区分，状态只能使用规定的大写值。
- SKU 用 name 描述规格，不在此阶段增加规格字典或属性表。
- 当前不建供应商/客户/财务表，也不加入金额字段、分类树、软删除、多租户或分区。
- 所有外键采用 RESTRICT，不级联删除历史数据。将来禁用用户通过 enabled 完成，不删除被引用的操作人。
- 未引入 Flyway/Liquibase。编号 SQL 是一次性基线，后续用新的版本脚本升级；不要修改已部署基线后重新执行。

## 3. 库存约束

```text
available_qty = on_hand_qty - locked_qty
on_hand_qty >= 0
locked_qty >= 0
locked_qty <= on_hand_qty
version >= 0
```

available_qty 不创建普通列或生成列，读取时计算。数据库通过 NOT NULL、CHECK 和 UNIQUE 保证单行约束。
version 只保留字段；本次不实现自动递增或乐观锁插件。后续 Service 必须在更新时检查旧 version 并递增。

例：广州仓某 SKU 的 on_hand_qty=10、locked_qty=3，可用数量为 7。
不能再插入一条相同仓库和 SKU 的 inventory；可以在深圳仓创建同一 SKU 的另一条库存。

## 4. 流水记录哪一种数量

仅用 quantity_before/change/after 无法区分“锁定 3 件”和“实物减少 3 件”，因此增加 quantity_type：

| operation_type | business_type | quantity_type | quantity_change |
| --- | --- | --- | --- |
| PURCHASE_IN | PURCHASE | ON_HAND | 正数 |
| SALES_LOCK | SALES | LOCKED | 正数 |
| SALES_UNLOCK | SALES | LOCKED | 负数 |
| SALES_OUT | SALES | ON_HAND、LOCKED 各一条 | 负数 |
| TRANSFER_OUT | TRANSFER | ON_HAND | 负数 |
| TRANSFER_IN | TRANSFER | ON_HAND | 正数 |
| ADJUSTMENT | ADJUSTMENT | ON_HAND | 非零正/负数，仅保留需求指定类型 |

每条流水必须满足 before >= 0、after >= 0、change != 0、after = before + change，并匹配上表。
销售发货同时减少实物和锁定量，因此同一业务动作需要两条 SALES_OUT 流水，分别记录两种余额。
例如实物 10→7，锁定 3→0，change 都为 -3。按数量汇总时必须过滤 quantity_type，不能将两者相加。

使用 (warehouse_id, sku_id) 外键引用 inventory 的唯一键，确保流水属于真实库存组合。
首次入库应先建立零余额 inventory，再在同一业务事务中更新库存、写流水。

business_type + business_no 是业务定位键并建立索引，不是外键：它可能指向三种不同的单据表，也可能是调整记录。
数据库不负责检查这种多态引用；后续 Service 验证单据存在性、状态和权限。业务单号一旦用于流水，后续业务代码不得修改。
不把该索引设为唯一，因为同一单据可以涉及多个 SKU、不同操作和两种余额。

两个 BEFORE UPDATE/DELETE 触发器拒绝改写或删除流水。纠错需新增冲正/调整流水，不能覆盖历史。
这是对行级 DML 的约束；具有 DROP/TRUNCATE 等 DDL 权限的管理员仍能破坏数据，因此不能给运行时账户管理员权限。
触发器不自动创建流水，也不实施库存业务。

## 5. 订单状态与数据库能力边界

| 单据 | 允许保存的状态 |
| --- | --- |
| 采购 | DRAFT、APPROVED、RECEIVED、COMPLETED、CANCELLED |
| 销售 | DRAFT、CONFIRMED、SHIPPED、COMPLETED、CANCELLED |
| 调拨 | DRAFT、APPROVED、OUTBOUND、INBOUND、COMPLETED、CANCELLED |

所有单据默认为 DRAFT。调拨的 CANCELLED 是预留的取消终态，具体允许取消的阶段后续明确，不能据此允许已出库的单据直接取消。
CHECK 只约束状态集合，不能证明一次状态变更合法。例如 DRAFT 直接写成 COMPLETED 仍可能通过数据库约束。
合法路径遵循产品需求，由后续 Service、事务和并发控制实现；本 Issue 不建立假的流转逻辑。

还必须留给业务层的约束包括：单据必须有明细、库存余额与流水一致、每次业务动作仅执行一次、操作人权限、跨仓调拨两端正确关联。

## 6. 索引

- 单号、SKU 编码、仓库编码、用户名使用唯一索引，同时支持精准查找。
- inventory 唯一索引以 warehouse_id 开头；另建 sku_id 索引，支持从 SKU 查询各仓库存。
- 订单按 (warehouse_id, status, created_at) 查询某仓待处理单据；调拨分别建立源仓和目标仓索引。
- 明细 (order_id, sku_id) 唯一索引支持按订单读取；sku_id 索引支持按 SKU 追溯订单。
- 流水 (warehouse_id, sku_id, created_at, id) 支持余额历史排序，(business_type, business_no) 支持业务追溯。
- 外键字段具备以该字段开头的索引；不为每个普通字段随意加索引。上线后按实际查询和 EXPLAIN 调整。

## 7. 初始化与账户权限（Workbench）

要求 MySQL 8.0.16+，因为更早的 8.0 不实际执行 CHECK 约束。

1. 启动 MySQL。若沿用本机 Issue #1 环境，连接 127.0.0.1:3307。
2. 新建单独的管理员连接，例如 StockFlow Admin 3307，用户名 root。密码使用本机已有配置中的 rootPassword，不提交凭据。
3. 确认 `SELECT @@port, CURRENT_USER();` 的结果。通用新环境由管理员先执行 README 的 CREATE DATABASE；不要删除已有库重建。
4. 选择 stockflow 为默认 Schema，执行 `SHOW TABLES;`。只对空库初始化；已有业务表时先停止，检查当前版本。
5. File → Open SQL Script，打开 backend/database/001_init_schema.sql。确认当前默认 Schema 正确，然后执行整个文件（包括最后两个触发器）。
6. 查看 Action Output，确保没有错误。刷新 SCHEMAS → stockflow → Tables，应看到 15 张表。
7. 使用普通 stockflow 连接查看表结构和数据，避免日常查询使用 root。

命令行也可以：

```text
mysql --host=127.0.0.1 --port=3307 --user=root -p stockflow
```

输入密码后，在 mysql 提示符中执行（替换成实际绝对路径，建议使用正斜杠）：

```sql
SOURCE D:/your-project/StockFlow/backend/database/001_init_schema.sql;
SHOW TABLES;
SHOW CREATE TABLE inventory;
SHOW TRIGGERS;
```

脚本不包含 CREATE DATABASE、USE 或 DROP，避免偷偷切换目标库/覆盖数据；再次执行会因表已存在而失败。
MySQL DDL 会隐式提交，15 张表不是一个可整体回滚的事务。中途失败时检查报错和已有对象，不要盲目重复或删除已有数据。
安装账户需要 CREATE 和 TRIGGER 等 DDL 权限。若服务器启用了特殊二进制日志限制，创建触发器还受管理员配置约束，应由 DBA 处理，不要在脚本里修改全局安全参数。

普通 stockflow 账户继续保持 Issue #1 的 SELECT 权限；该库级授权会覆盖后续新建的表。
本次不扩大运行时账户权限。未来业务实现时按表授予所需 INSERT/UPDATE/DELETE；流水仅授予 SELECT/INSERT，不授予 UPDATE/DELETE 或 DDL。
不要让后端使用 root，不需要把管理员密码写到 application.yml。

## 8. 可重复验收（VS Code）

测试使用项目已有 Node.js 和 mysql 客户端，不安装 npm 依赖。它创建随机的 stockflow_schema_test_* 数据库，
初始化基线并执行正反向用例，最后只删除自己成功创建的那个测试库；不会使用 DB_NAME 指定的开发库。
数据库管理员需有创建/删除测试库、建表、建触发器和测试数据读写权限。

在 VS Code 仓库根目录的 PowerShell 终端中：

```powershell
$env:MYSQL_BIN = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe' # 或 PATH 中的 mysql
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307' # 通用安装通常是 3306
$env:DB_USERNAME = 'root'
$credential = Get-Credential -UserName root -Message '输入测试实例的管理员密码'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
try {
    node backend/database/verify-schema.mjs
} finally {
    Remove-Item Env:\DB_PASSWORD
}
```

密码通过子进程环境传递，不写入命令参数或仓库。如果强制关闭测试进程导致清理未执行，依据日志显示的测试库名核实后自行清理。
测试包含：15 表/引擎/时间列、无种子数据、唯一约束、每一个外键列、库存边界、订单状态/数量、流水记账与不可变性、重复初始化失败。
不测试尚未实现的业务状态机、乐观锁竞争或事务服务。

## 9. 学习顺序

1. category → product → sku：理解主键、一对多、外键和唯一编码。
2. warehouse → inventory：理解联合唯一键和 CHECK，自己计算可用库存。
3. purchase_order → purchase_order_item：理解主表/明细表及一张单多个 SKU。
4. inventory_transaction：理解两种数量、正负变动和只追加记录。
5. verify-schema.mjs：看每种错误怎样被真实数据库拒绝，再在独立练习库重现一个用例。

参考：[MySQL CHECK](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)、
[外键](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html)。
