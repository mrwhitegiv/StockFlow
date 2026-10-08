# Issue #3：基础资料管理与学习路线

本阶段提供分类、商品、SKU、仓库的第一个完整业务闭环。没有库存变更、采购/销售单、登录或 RBAC。

## 1. 从 Issue #2 升级

先启动 MySQL，用管理员连接选择目标数据库。已有 Issue #2 的 15 张表时，**只执行**：

```sql
USE stockflow;
SOURCE D:/your-project/StockFlow/backend/database/002_product_enabled.sql;
```

Workbench 也可以选择 stockflow 为默认 Schema，再打开 002 文件执行。
新环境先执行 001，再执行 002。两个文件都只执行一次；不使用重复执行来判断版本。
002 添加 product.enabled，已有商品默认启用，保留已有数据。不改写 001，不自动执行 SQL。

管理员为后端账户增加这四张表所需的权限（已有 SELECT 授权保留）：

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON stockflow.category TO 'stockflow'@'localhost';
GRANT SELECT, INSERT, UPDATE ON stockflow.product TO 'stockflow'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON stockflow.sku TO 'stockflow'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON stockflow.warehouse TO 'stockflow'@'localhost';
```

若你使用其他数据库名、用户名或 MySQL 用户 host，请相应替换；不授予订单、库存写入权限。
这些 GRANT 只调整权限，不修改密码，不需要 FLUSH PRIVILEGES。后端继续使用普通账户，不使用 root。

本机沿用 127.0.0.1:3307。数据库和前后端都要持续运行。VS Code 终端分别运行 README 的启动命令即可。

## 2. 页面操作顺序

1. 分类管理：新增一个分类，例如“电脑”。
2. 商品管理：新增“学习用笔记本”，选择分类；可以编辑名称、分类和描述。
3. 商品行的“管理 SKU”：新增编码 LAPTOP-BLACK-512，名称“黑色 / 512GB”。
4. 仓库管理：新增编码 WH-GZ，名称“广州仓”，地址可选。
5. 返回商品列表，按名称、分类、启用状态筛选；列表支持分页。
6. 禁用商品后，记录和 SKU 仍然可查，不能新增或修改它的 SKU；可以重新启用。
7. 分类、SKU、仓库可以删除，但已经被其他表引用时返回冲突，保留原数据。

商品不提供物理删除，避免后续业务历史断链。SKU 所属商品在创建后不可通过更新接口更换。
SKU 编码在全表唯一，仓库编码全表唯一，分类名称唯一；唯一性由 MySQL 最终保证，包含并发请求。

## 3. API 契约

基础地址默认 http://localhost:8080/api。写入使用 Content-Type: application/json。

| 方法 | 路径 | 功能 / 请求 |
| --- | --- | --- |
| GET / POST | /categories | 分类列表 / 新增：name |
| GET / PUT / DELETE | /categories/{id} | 详情 / 修改 name / 删除 |
| GET | /products | 分页，见下文 |
| POST | /products | 新增：categoryId、name、可选 description |
| GET / PUT | /products/{id} | 详情 / 修改上述三项 |
| PATCH | /products/{id}/enabled | 启用或禁用：enabled（布尔值） |
| GET / POST | /products/{productId}/skus | 分页 / 新增：code、name |
| GET / PUT / DELETE | /skus/{id} | 详情 / 修改 code、name / 删除 |
| GET / POST | /warehouses | 仓库列表 / 新增：code、name、可选 address |
| GET / PUT / DELETE | /warehouses/{id} | 详情 / 修改上述三项 / 删除 |

商品和 SKU 分页参数：page 默认 1，最小 1；size 默认 10，范围 1–100；keyword 最长 200。
商品支持 categoryId 和 enabled=true/false 筛选，keyword 按名称搜索；
SKU 的 keyword 按编码或规格名称搜索。按 id 降序，keyword 是子串查询，% 不作为通配符。
分类和仓库列表暂不分页。

返回形状：

```json
{
  "code": 200,
  "message": "success",
  "data": { "records": [], "total": 0, "page": 1, "size": 10 }
}
```

单条接口 data 为对象，分类/仓库列表 data 为数组，删除成功 data 为 null。创建也使用 HTTP 200，保持现有统一响应约定。
错误时 HTTP 状态与 code 一致，data 为 null：

| 状态 | 含义 |
| --- | --- |
| 400 | 必填、长度、ID、分页或 JSON 格式不合法 |
| 404 | 商品、分类、SKU 或仓库不存在 |
| 409 | 名称/编码重复、关联约束冲突、禁用商品下写入 SKU |
| 405 / 415 | 请求方法不支持 / 未使用 JSON |
| 503 | 数据库连接不可用 |
| 500 | 未预期的内部错误，不向客户端暴露 SQL 或堆栈 |

输入两端空白会清理；名称不允许空白。description/address 的空字符串按 null 保存，编辑时可清空。
分类/仓库名称最长 100；商品/SKU 名称最长 200；描述最长 1000；地址最长 255。
编码为 1–64 位 ASCII 字母、数字、点、横线、下划线，以字母或数字开头；编码比较区分大小写。
createdAt/updatedAt 是数据库生成的 UTC 时间，不接受客户端设置。

## 4. 不经过前端测试 API（VS Code PowerShell）

先启动后端，创建分类并读取真实返回的 ID，不要假设 ID 从 1 开始：

```powershell
$base = 'http://localhost:8080/api'
$categoryBody = @{ name = '学习分类' } | ConvertTo-Json
$category = Invoke-RestMethod "$base/categories" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($categoryBody))
$productBody = @{ categoryId = $category.data.id; name = '学习商品'; description = '第一条完整业务请求' } | ConvertTo-Json
$product = Invoke-RestMethod "$base/products" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($productBody))
Invoke-RestMethod "$base/products?page=1&size=10"
Invoke-RestMethod "$base/products/$($product.data.id)/enabled" -Method Patch -ContentType 'application/json' -Body '{"enabled":false}'
```

以上是你主动写入开发库的练习数据。自动验收则使用独立随机测试库。

## 5. 自动验收

不依赖本机数据库的后端测试：

```text
cd backend
mvn clean package
```

包含 HTTP 参数验证、错误响应、CORS、Service 规则。MySqlConnectionTest 默认跳过，
可按 README 配置 DB_INTEGRATION_TEST=true 后单独启用。

真实 HTTP + MySQL 验收（回到仓库根目录）：

```powershell
$env:MYSQL_BIN = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_USERNAME = 'root'
$credential = Get-Credential -UserName root -Message '本机测试数据库管理员'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
try {
    node backend/database/verify-master-data.mjs
} finally {
    Remove-Item Env:\DB_PASSWORD
}
```

管理员需要创建/删除临时库和临时用户、授予权限，以及表/触发器操作权限。
脚本限定本机 MySQL，创建随机 stockflow_api_test_* 数据库，执行 001 + 002，创建只具备四张表权限的临时应用账户，
在随机 HTTP 端口启动已打包的后端。65 项检查覆盖 HTTP、真实数据库、升级保留数据、分页、禁用规则、并发唯一编码和外键保护；
结束后停止临时后端，删除自身创建的临时账户与数据库。不会读取 DB_NAME 作为目标库。

加 --interactive 可以在检查成功后保留临时后端用于浏览器验证。终端会打印临时 API 地址；
将其设为前端终端的 VITE_API_BASE_URL，再启动 Vite。浏览器验收完毕后，在测试终端按 Enter 清理。
若强制终止整个进程，清理可能无法完成；根据日志中的随机库名核实残留，测试账户名为 sf_test_ 加相同随机后缀。
不要删除 stockflow 开发库。

前端：

```text
cd frontend
npm install
npm run build
npm run dev
```

实际浏览器验收应覆盖：必填验证、创建分类/商品/SKU、重复编码提示、编辑并刷新、商品禁用、
SKU 写入按钮限制，以及恢复启用。浏览器端测试不是用 mock API 代替后端。

## 6. 按一条请求学习代码

先跟踪“新增商品”，无需一次理解所有文件：

1. frontend/src/views/ProductView.vue：Vue 的 ref/reactive 保存列表与表单状态，save() 校验后调用 API；template 把数据渲染为表格和弹窗。
2. frontend/src/api/masterData.js：productsApi.create 通过 Axios 发送 POST；utils/request.js 统一 baseURL 和超时，组件不拼完整地址。
3. backend/.../controller/ProductController.java：@PostMapping 对应 HTTP 路由，@RequestBody 把 JSON 转成 ProductInput，@Valid 执行字段校验。Controller 只转交请求。
4. dto/MasterDataRequests.java：Java record 保存请求字段；@NotBlank / @Size / @Positive 描述约束。请求中不接收数据库时间或库存。
5. service/ProductService.java：检查分类是否存在、设置默认启用，@Transactional 保证该业务方法中的数据库操作共同提交或回滚。
6. entity/Product.java：普通 Java 类映射 product 表，getter/setter 供框架读写属性，@TableId 配置自增 ID。
7. mapper/ProductMapper.java：继承 MyBatis-Plus BaseMapper 获得基础 SQL，额外提供参数化分页查询，不增加分页解析依赖。
8. common/ApiResponse.java 和 exception/GlobalExceptionHandler.java：成功和失败用统一 JSON 返回；前端读取数据或展示错误。
9. MasterDataApiTest / MasterDataServiceTest：分别看 HTTP 边界和业务规则；verify-master-data.mjs 再通过真实 HTTP 与 MySQL 验证整条链。

商品编辑只更新可编辑字段，避免编辑信息时覆盖另一个请求刚设置的禁用状态。
写 SKU 时在事务内锁定父商品行，与商品禁用操作串行，防止校验启用后同时被禁用的竞态。
这只是基础资料约束，不涉及库存乐观锁或订单事务。

每次学习可只回答三个问题：请求从哪里来、这个方法负责哪条规则、数据最终保存在哪里。
然后修改独立练习数据并观察网络响应和数据库结果。

## 7. 当前安全边界与范围

当前基础资料 API 是无需登录的开发接口；后端默认仅监听 127.0.0.1，勿直接公开部署。
Security 放行明确的 Health 和基础资料路径；Issue #4 另增加库存 GET，其他路径拒绝。CORS 允许配置中的明确前端地址，不使用 Cookie/Basic/Session 凭据。
JSON 写接口仅对这组路径豁免 CSRF；未来实现认证时必须重新设计访问控制和 CSRF，不能把 CORS 当作身份认证。

当前没有库存写入 API、库存扣减、订单、JWT、RBAC、Redis 或 Docker。商品禁用也不会修改库存或删除 SKU。

实现参考：[MyBatis-Plus 字段更新策略](https://baomidou.com/en/reference/annotation/)、
[Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)。
