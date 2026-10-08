# StockFlow

Order, inventory and warehouse management system for practicing production-oriented backend engineering.

## Goal

StockFlow is a learning-oriented enterprise application project focused on core backend engineering skills:

- business modeling
- REST API design
- authentication and RBAC
- MySQL data modeling
- inventory consistency
- transactions
- concurrency control
- auditability
- testing
- containerization and deployment

## MVP Scope

- User & role management
- Product / SKU management
- Warehouse management
- Inventory management
- Inventory transaction ledger
- Purchase inbound flow
- Sales outbound flow
- Warehouse transfer flow

## Tech Stack

### Backend
- Java 21
- Spring Boot 4.1.x
- Spring Security
- MyBatis-Plus
- MySQL 8
- Maven

### Frontend
- Vue 3
- Element Plus
- Pinia
- Vue Router
- Axios

Redis and RabbitMQ will be introduced only when a real project requirement justifies them.

## Project Docs

See `docs/product-requirements.md` for the MVP product requirements and engineering constraints.

数据库结构见 [设计与执行说明](docs/database-design.md) 和 [ER 图](docs/database-er.md)。

## Current baseline (Issues #1–5)

当前已实现前端 → Spring Boot → MySQL 的最小闭环，并提供 15 张 MVP 表的建表脚本、ER 图和数据库约束测试。
已提供分类、商品、SKU、仓库管理 API 与页面，包括分页、校验、商品禁用和数据库约束保护。
已提供只读库存查询，支持仓库/SKU 筛选、分页和可用数量计算。
已提供采购单草稿、审核、事务收货、完成与取消；收货一次性更新库存、追加流水和状态，防止重复收货并支持失败回滚。
后端仍是单体应用，未实现销售/调拨、登录、JWT、RBAC、Docker 或中间件。

```text
backend/    Java 21 / Spring Boot 4.1.1 / MyBatis-Plus 3.5.17
frontend/   Vue 3 / Vite / Element Plus / Pinia / Vue Router / Axios
docs/       产品需求
```

后端包：`com.stockflow` 下的 `config`、`controller`、`service`、`mapper`、`dto`、`common`、`exception`。
请求路径为 Controller → Service → Mapper → MySQL。entity 包映射分类、商品、SKU 和仓库。接口及学习路线见 [Issue #3 使用说明](docs/master-data.md)、[Issue #4 库存查询](docs/inventory.md) 和 [Issue #5 采购入库](docs/purchase-inbound.md)。

## Requirements

首次使用请安装并验证：

| 工具 | 要求 | 官方下载 | 验证命令 |
| --- | --- | --- | --- |
| JDK | 21（完整 JDK） | [Adoptium](https://adoptium.net/temurin/releases/?version=21) | `java -version`、`javac -version` |
| Maven | 3.9.x | [Maven](https://maven.apache.org/download.cgi) | `mvn -version` |
| Node.js | 22.12+，推荐 24 LTS | [Node.js](https://nodejs.org/en/download) | `node -v` |
| npm | 随 Node.js 安装 | 同上 | `npm -v` |
| MySQL Server | 8.0.16+（支持执行 CHECK 约束） | [MySQL Community](https://dev.mysql.com/downloads/mysql/) | `mysql --version` |
| Git | 用于克隆仓库 | [Git](https://git-scm.com/downloads) | `git --version` |

MySQL Workbench 只是图形客户端，仍需安装并启动 MySQL Server。
安装 MySQL 时保存自己设置的管理员密码，不要提交到 Git。

Windows 环境变量设置：`JAVA_HOME` 指向 JDK 根目录（不是 bin），PATH 加入
`%JAVA_HOME%\bin`、Maven 的 `bin`、Node.js 安装目录和 MySQL 的 `bin`。修改后重新打开终端。
`mvn -version` 必须显示 Java 21。PowerShell 若禁止执行 `npm.ps1`，使用 `npm.cmd`，无需放宽系统执行策略。

```bash
git clone https://github.com/mrwhitegiv/StockFlow.git
cd StockFlow
```

## Database

先启动 MySQL 服务，然后通过 `mysql -u root -p` 登录（密码在提示中输入）。
也可以打开 Workbench → MySQL Connections 旁的 `+` → 设置 Hostname 为 `127.0.0.1`、
Port 为 `3306`、Username 为 `root` → Test Connection → 输入安装时设置的密码 → 打开连接。
在 SQL 编辑器中执行以下语句；将占位密码替换成你自己生成的本地密码：

```sql
CREATE DATABASE stockflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'stockflow'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_LOCAL_PASSWORD';
GRANT SELECT ON stockflow.* TO 'stockflow'@'localhost';
```

以上先创建空数据库及开发账户。Health API 只需 SELECT；Issue #3 还需按下文为四张基础资料表授权写入。
若数据库或用户已存在，请使用已有配置，不要重复创建或删除重建。

### Initialize the schema (Issue #2)

用管理员连接选择空的 `stockflow` 数据库，再执行
[`backend/database/001_init_schema.sql`](backend/database/001_init_schema.sql)。
Workbench 可通过 File → Open SQL Script 打开文件并执行全部内容；先确认默认 Schema 是 `stockflow`。
命令行客户端也可在登录后执行 `SOURCE <建表脚本的绝对路径>;`。

脚本创建全部 15 张表和流水的两个只追加保护触发器，没有种子账户或业务数据。
不要对已有业务表的数据库重复执行；脚本不会 DROP 或覆盖数据，也不会被 Spring Boot 自动执行。
建表是管理员操作，后端依然使用 `stockflow` 账户。已有的库级 SELECT 授权会覆盖新表。

沿用本机专用环境时，请使用 `127.0.0.1:3307`，而不是上面的通用默认端口 3306。
完整的 Workbench 步骤、权限说明、设计取舍和 VS Code 测试命令见 [数据库设计文档](docs/database-design.md)。

### Upgrade for Issue #3

已有 Issue #2 表结构时，只执行 [002_product_enabled.sql](backend/database/002_product_enabled.sql) 一次。
新环境按 001 → 002 顺序执行；002 保留数据并将已有商品设为启用。
再按 [升级与授权步骤](docs/master-data.md#1-从-issue-2-升级) 为普通后端账户添加四张表的最小写入权限。
不要重新执行 001 或让后端使用 root。

Issue #4 的只读库存查询只需 SELECT。

### Upgrade for Issue #5

无需新表结构迁移。按 [采购升级步骤](docs/purchase-inbound.md#2-升级现有开发库) 执行开发操作者脚本，
为普通账户添加采购单/明细、库存与流水的表级权限。流水仅允许 SELECT/INSERT。
该技术身份处于停用状态，不能登录，后续认证阶段替换它。升级后重启后端。

后端通过环境变量配置连接：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL 主机 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `stockflow` | 数据库名 |
| `DB_USERNAME` | `stockflow` | 数据库用户 |
| `DB_PASSWORD` | 空 | 设置为上面创建用户时的密码 |
| `DEV_OPERATOR_USERNAME` | `stockflow-local-operator` | 停用的本地技术操作者，先按采购文档初始化 |
| `SERVER_ADDRESS` | `127.0.0.1` | 默认仅供本机开发访问 |
| `SERVER_PORT` | `8080` | 后端 HTTP 端口 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 多个明确来源用逗号分隔 |

## Backend

在一个终端中进入 `backend`。Windows PowerShell 示例（密码不进入命令历史）：

```powershell
cd backend
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'stockflow'
$env:DB_USERNAME = 'stockflow'
$dbCredential = Get-Credential -UserName stockflow -Message '请输入本地 MySQL 密码'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
mvn spring-boot:run
```

macOS/Linux Bash：

```bash
cd backend
export DB_USERNAME=stockflow
read -rsp 'MySQL password: ' DB_PASSWORD; echo
export DB_PASSWORD
mvn spring-boot:run
```

环境变量只在当前终端及其子进程有效。Spring Boot 不会自动加载 `.env` 文件。
等待 `Started StockFlowApplication` 后，在浏览器打开 [Health API](http://localhost:8080/api/health)。
每次 GET 都通过 MyBatis-Plus 配置的 MyBatis mapper 执行 `SELECT 1`，没有建表或写入操作。
只有查询成功才返回 HTTP 200：

```json
{"code":200,"message":"success","data":{"status":"UP"}}
```

MySQL 不可用时返回 HTTP 503 和 `{"code":503,"message":"Database unavailable","data":null}`，
详细异常只记入后端日志，不发给前端。应用启动本身不等于数据库已连通，以 Health API 的 UP 为准。

## Frontend

另开终端，从仓库根目录执行：

```bash
cd frontend
npm install
npm run dev
```

打开 [首页](http://localhost:5173)，页面自动调用后端并显示 `Backend Status: UP`。
连接失败时显示 DOWN 和提示；修复后点击“重新检查”。
导航栏可进入分类管理、商品管理、仓库管理、采购入库和库存查询，商品行内进入 SKU 管理。首次使用先创建分类。
库存没有记录时显示空列表；新建基础资料不会自动生成库存。采购单收货后产生真实库存。
步骤见 [采购入库](docs/purchase-inbound.md#3-实际操作)，原只读查询演示见 [隔离验收模式](docs/inventory.md#4-自动验收与浏览器演示vs-code)。
`package-lock.json` 固定安装结果，后续可使用 `npm ci` 重现依赖。

默认 API 地址是 `http://localhost:8080/api`。需要修改时，将 `frontend/.env.example`
复制为 `frontend/.env.local`，修改 `VITE_API_BASE_URL`，然后重启 Vite。
`VITE_` 配置会进入浏览器构建产物，不能包含密码或 Token。

前端直接跨域访问后端，Axios 实例统一配置 baseURL 和超时，组件只调用 store / API 模块。
后端仅允许明确配置的来源，不携带 Cookie 凭据；没有通配来源加 credentials 的组合。
请使用 `localhost:5173`，若要用 `127.0.0.1:5173`，同时把该来源加入 `CORS_ALLOWED_ORIGINS`。
Vite 固定端口并启用 strictPort，避免自动换端口后 CORS 配置失效。

Spring Security 放行 Health GET、明确基础资料路径、库存 GET 和采购动作接口，其余路径拒绝。当前无登录，写接口仅用于本机开发。
基础资料和采购 JSON API 豁免 CSRF；它们的 CORS 允许 GET/POST/PUT/PATCH/DELETE，库存 CORS 仅允许 GET，均不携带凭据。后续认证阶段重新设计访问控制。

## Verification

```bash
cd backend
mvn clean test
mvn clean package
```

默认测试验证 HTTP 参数校验、错误响应、Security、CORS 和 Service 规则，不依赖个人数据库。
真实 MySQL 测试默认跳过；先配置上面的 DB 环境变量，再运行：

```powershell
$env:DB_INTEGRATION_TEST = 'true'
mvn clean test
```

Bash 等价命令：`DB_INTEGRATION_TEST=true mvn clean test`。
该连接测试使用实际 MySQL，没有 H2 替代或业务表初始化。
Issue #3 的独立验收另用 `node backend/database/verify-master-data.mjs`，在随机测试库启动真实 HTTP 服务，
验证 65 项升级与业务 API 行为并自动清理；前置条件和命令见 [验收说明](docs/master-data.md#5-自动验收)。

Issue #4 另用 `node backend/database/verify-inventory.mjs`，执行 56 项真实 HTTP/MySQL 检查，使用仅 SELECT 的临时应用账户。
包括筛选、数量计算、大整数精度、唯一键与 CHECK 约束、写接口拒绝；命令与浏览器演示见 [库存验收说明](docs/inventory.md#4-自动验收与浏览器演示vs-code)。

Issue #5 使用 `node backend/database/verify-purchase.mjs` 验证真实 MySQL 状态机、并发防重复、强制失败回滚及流水保护。
配置与隔离浏览器演示见 [采购验收说明](docs/purchase-inbound.md#6-验证方式)。

前端：

```bash
cd frontend
npm install
npm run build
npm run dev
```

最后在浏览器访问首页，确认 UP；仅构建成功不代表数据库和前后端已连通。

数据库结构的独立验收（Node.js + MySQL CLI，无额外 npm 依赖）：

```bash
node backend/database/verify-schema.mjs
```

从仓库根目录运行，先通过环境变量设置 `DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD` 和可选 `MYSQL_BIN`。
本测试需要管理员权限，会创建随机命名的独立测试数据库并在结束时删除它；不修改开发库。
具体配置示例见 [数据库验收步骤](docs/database-design.md#8-可重复验收vs-code)。

## Troubleshooting

- `JAVA_HOME ... not defined correctly`：将 JAVA_HOME 改为实际 JDK 21 根目录，重新打开终端。
- 找不到 `npm` / `mvn` / `mysql`：先确认软件已安装，再将其命令目录加入 PATH。
- HTTP 503 / `Access denied`：核对数据库用户名、密码和用户 host；`Unknown database` 表示尚未创建 stockflow。
- `Connection refused`：确认 MySQL 服务正在运行，DB_HOST/DB_PORT 正确；Windows 可在“服务”中查看 MySQL80。
- 本机非 TLS 的 MySQL 若报 `Public Key Retrieval is not allowed`：仅本地开发可设置
  `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/stockflow?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000`。
  不要把这一设置用于远程数据库；远程连接配置可信 TLS 证书。
- 浏览器跨域失败：核对页面的完整 Origin（协议、主机、端口）是否在 CORS_ALLOWED_ORIGINS 中。
- 端口被占用：关闭占用进程或显式修改端口，并同步修改 API 地址/CORS 配置。
- 构建首次需要联网下载依赖；不要通过关闭 TLS 校验解决下载问题。

构建输出 `target/`、`dist/`、依赖 `node_modules/`、IDE 文件和本地 `.env` 均已忽略。
