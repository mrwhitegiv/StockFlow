# Issue #4：库存模型与只读查询

本阶段回答“哪个仓库、哪个 SKU 有多少库存”。不会创建库存、修改数量、执行采购入库或销售锁定。

## 1. 启动与已有数据

沿用 README 中的 MySQL → 后端 → 前端启动方式，在导航栏进入“库存查询”。
已经完成 Issue #3 的数据库无需迁移：001 已包含 inventory 表、唯一键、外键及 CHECK 约束；002 已补充商品启用字段。
新环境仍按 001 → 002 顺序执行。后端账户需要 SELECT（README 中库级 SELECT 已覆盖），不要增加库存写入权限。

首次打开可能没有数据，这表示目前没有库存记录。新建商品、SKU、仓库不会自动创建库存行；
没有记录与“已经存在且数量为零”的记录是两种状态。商品禁用后，其已有库存仍然可查询。

需要有数据的演示时，使用下文的隔离测试模式。测试数据不会写入 stockflow 开发库，
也没有隐藏的初始化接口或“修改库存”按钮。

## 2. API 契约

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/inventory | 库存分页查询 |
| GET | /api/inventory/{id} | 按库存记录 ID 查询详情；不存在返回 404 |

列表参数全部可选：

| 参数 | 默认值 / 规则 |
| --- | --- |
| page | 1，最小 1 |
| size | 10，范围 1–100 |
| warehouseId | 正整数，按仓库 ID 精确匹配 |
| skuId | 正整数，按 SKU ID 精确匹配 |
| skuCode | 最长 64，去除两端空白；精确且区分大小写，空白等同未筛选 |

多个筛选条件同时生效（AND），按库存 id 降序。未知仓库/SKU、筛选无匹配、页码超出结果范围均返回空列表。
参数非法返回 400；详情不存在返回 404；数据库不可用返回 503。其他异常返回通用 500，不暴露 SQL。

示例：GET /api/inventory?warehouseId=1&skuCode=PHONE-BLACK

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{
      "id": "1",
      "warehouseId": "1",
      "warehouseCode": "WH-GZ",
      "warehouseName": "广州仓",
      "skuId": "1",
      "skuCode": "PHONE-BLACK",
      "skuName": "黑色 / 256GB",
      "productName": "学习手机",
      "onHandQty": "10",
      "lockedQty": "3",
      "availableQty": "7",
      "version": "0",
      "updatedAt": "2026-10-05T00:00:00"
    }],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

上述 ID 为示例，实际操作应使用查询返回的 ID。详情接口的 data 为单条对象。

**库存响应中的 ID、数量、version 使用十进制字符串**，避免 JavaScript Number 对 MySQL BIGINT 产生精度丢失。
数据库和 Java 内仍为 BIGINT/long；分页的 total/page/size 为 JSON 数字。
这是新增库存接口的契约，未修改 Issue #3 基础资料接口。前端直接展示后端计算的 availableQty，不自行用 Number 相减。
updatedAt 沿用数据库 UTC 时间。

## 3. 模型与只读边界

- 实物数量 onHandQty ≥ 0；锁定数量 lockedQty ≥ 0 且 ≤ onHandQty。
- availableQty = onHandQty − lockedQty，每次从合法余额计算，不作为数据库字段存储。
- warehouseId、skuId 必须为正数；version ≥ 0。本阶段只读取 version，不实现更新、乐观锁或重试。
- 同仓库、同 SKU 只允许一条记录，由现有唯一键 uk_inventory_warehouse_sku 保证。
- Java Inventory 的构造器拒绝非法余额；数据库 CHECK 约束保护直接 SQL 写入。两层各有职责。
- Controller 仅有 GET；Mapper 只声明 SELECT，未继承包含写方法的 BaseMapper；Service 使用只读事务。
- Security 只放行库存 GET，POST/PUT/PATCH/DELETE 返回 403；库存 CORS 仅允许明确来源的 GET，不携带凭据。
- 数据库账户保持最小权限。只读事务是表达意图，不应替代数据库权限与接口访问控制。

同一次分页查询的列表与总数处于一个只读事务内。SQL 使用绑定参数，前端输入不会拼接进 SQL。
关联查询同时返回仓库、SKU 和商品名称，避免每行单独请求名称。当前无需新增依赖、索引或中间件。

## 4. 自动验收与浏览器演示（VS Code）

在 VS Code 打开项目根目录，在集成终端运行：

```text
cd backend
mvn clean package
```

新增 InventoryTest、InventoryServiceTest、InventoryApiTest 覆盖领域规则、分页筛选传参、HTTP 校验、
序列化精度、CORS、写接口拒绝与内部错误。MySqlConnectionTest 默认跳过；按 README 设置
DB_INTEGRATION_TEST=true 可启用真实数据库连通测试。

打包后回到仓库根目录，用本机测试管理员运行隔离验收（以下安装路径按实际情况调整）：

```powershell
$env:MYSQL_BIN = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_USERNAME = 'root'
$credential = Get-Credential -UserName root -Message '本机测试数据库管理员'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
try {
    node backend/database/verify-inventory.mjs
} finally {
    Remove-Item Env:\DB_PASSWORD
}
```

脚本仅连接本机，创建随机 stockflow_inventory_test_* 库及 sf_inv_* 账户，执行 001 + 002，
让后端使用 **仅 SELECT 权限** 的账户在随机 HTTP 端口运行。管理员只负责测试准备和清理。
不会读取 DB_NAME 作为目标库，也不会改动 stockflow。

56 项实际 HTTP/MySQL 检查包括：空库、仓库/SKU/组合筛选、分页、禁用商品库存保留、精确匹配、
SQL 注入输入、BIGINT 边界、零库存、非法参数、所有写方法拒绝、重复记录与 CHECK 拒绝、
只读账户无法写入、库存数量/时间/version 不变、没有新增流水、CORS。

要在浏览器操作这些测试数据，将上述命令加上 --interactive：

```text
node backend/database/verify-inventory.mjs --interactive
```

成功后保留该终端，复制它打印的 API 地址。在另一个 VS Code 终端进入 frontend：

```powershell
cd frontend
# 替换为上一个终端打印的完整地址，随机端口每次不同。
$env:VITE_API_BASE_URL = 'http://127.0.0.1:实际端口/api'
npm ci
npm run dev -- --port 5174
```

打开 http://localhost:5174/inventory。演示后端明确允许 localhost:5173 和 localhost:5174。
依次验证：PHONE-BLACK 两条记录、广州仓+PHONE-BLACK 为 10−3=7、全部锁定为 0、
SKU ID 4 无记录、非法 ID 提示、分页、重置、刷新、大整数显示。
只有查询功能，没有新增/编辑/删除库存入口。

完成后在测试终端按 Enter，脚本停止临时后端并删除自身创建的库与用户；前端终端 Ctrl+C 停止 Vite。
强制结束整个测试进程可能跳过清理：只按日志中的随机名称核实残留，
测试账户为 sf_inv_ 加同一随机后缀；不要删除 stockflow 开发库。
VITE_API_BASE_URL 仅作用于当前终端；重新开终端恢复 README 的默认启动地址。

基础资料回归仍可运行 node backend/database/verify-master-data.mjs（65 项）。
前端构建运行 npm run build。没有新增 npm/Maven 依赖。

## 5. 学习时跟踪一条查询

可以先继续学习 Issue #3，再沿以下顺序看本阶段，无需一次掌握所有语法：

1. frontend/src/views/InventoryView.vue：ref 保存页面状态，组合条件经查询按钮传给 API；分页与错误如何显示。
2. frontend/src/api/inventory.js：复用 Axios 实例，把 params 编码为查询字符串。
3. controller/InventoryController.java：@GetMapping 对应 GET，@RequestParam 接收参数，约束注解拦截非法输入。
4. service/InventoryService.java：清理输入、计算分页偏移，组合 Mapper 与模型；只读事务覆盖列表与总数。
5. mapper/InventoryMapper.java：JOIN 获取名称，#{} 绑定参数，动态条件、LIMIT 和 OFFSET 完成筛选分页。
6. dto/InventoryRow.java：接收 SQL 结果；entity/Inventory.java：Java record 构造时验证余额，方法计算可用数量。
7. dto/InventoryView.java：对外响应数据与 JSON 字符串精度；数据库查询结果和 API 输出分开。
8. InventoryTest.java → InventoryServiceTest.java → InventoryApiTest.java → verify-inventory.mjs：从单条规则到真实请求链。

本阶段故意不实现：库存增减、采购入库、销售锁定/扣减、流水写入、并发更新、Redis、登录和 RBAC。
后续业务需要通过明确的业务操作变更库存，不能补一个任意修改数量的通用 CRUD 接口。
