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

## Current baseline (Issue #1)

当前只实现前端 → Spring Boot → MySQL 的最小闭环。上面的 MVP 是后续规划，并非已实现功能。
后端是单体应用，没有业务表、登录、JWT、RBAC、库存逻辑、Docker 或中间件。

```text
backend/    Java 21 / Spring Boot 4.1.1 / MyBatis-Plus 3.5.17
frontend/   Vue 3 / Vite / Element Plus / Pinia / Vue Router / Axios
docs/       产品需求
```

后端包：`com.stockflow` 下的 `config`、`controller`、`service`、`mapper`、`dto`、`common`、`exception`。
请求路径为 Controller → Service → Mapper → MySQL。当前没有实体表，暂不创建空的 entity 包。

## Requirements

首次使用请安装并验证：

| 工具 | 要求 | 官方下载 | 验证命令 |
| --- | --- | --- | --- |
| JDK | 21（完整 JDK） | [Adoptium](https://adoptium.net/temurin/releases/?version=21) | `java -version`、`javac -version` |
| Maven | 3.9.x | [Maven](https://maven.apache.org/download.cgi) | `mvn -version` |
| Node.js | 22.12+，推荐 24 LTS | [Node.js](https://nodejs.org/en/download) | `node -v` |
| npm | 随 Node.js 安装 | 同上 | `npm -v` |
| MySQL Server | 8.x | [MySQL Community](https://dev.mysql.com/downloads/mysql/) | `mysql --version` |
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

这里只创建空数据库及开发账户，不创建业务表。Issue #1 只需 SELECT；后续建表和权限变更按对应 Issue 处理。
若数据库或用户已存在，请使用已有配置，不要重复创建或删除重建。

后端通过环境变量配置连接：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL 主机 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `stockflow` | 数据库名 |
| `DB_USERNAME` | `stockflow` | 数据库用户 |
| `DB_PASSWORD` | 空 | 设置为上面创建用户时的密码 |
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
`package-lock.json` 固定安装结果，后续可使用 `npm ci` 重现依赖。

默认 API 地址是 `http://localhost:8080/api`。需要修改时，将 `frontend/.env.example`
复制为 `frontend/.env.local`，修改 `VITE_API_BASE_URL`，然后重启 Vite。
`VITE_` 配置会进入浏览器构建产物，不能包含密码或 Token。

前端直接跨域访问后端，Axios 实例统一配置 baseURL 和超时，组件只调用 store / API 模块。
后端仅允许明确配置的来源，不携带 Cookie 凭据；没有通配来源加 credentials 的组合。
请使用 `localhost:5173`，若要用 `127.0.0.1:5173`，同时把该来源加入 `CORS_ALLOWED_ORIGINS`。
Vite 固定端口并启用 strictPort，避免自动换端口后 CORS 配置失效。

Spring Security 仅放行 `GET /api/health`，其余请求拒绝；关闭表单登录、HTTP Basic 和会话创建，
保留 CSRF 默认保护。后续新增接口必须明确配置访问规则。目前不提供任何登录/权限功能。

## Verification

```bash
cd backend
mvn clean test
mvn clean package
```

默认测试使用 mock mapper 验证 HTTP 响应、错误处理、Security 和 CORS，不依赖个人数据库。
真实 MySQL 测试默认跳过；先配置上面的 DB 环境变量，再运行：

```powershell
$env:DB_INTEGRATION_TEST = 'true'
mvn clean test
```

Bash 等价命令：`DB_INTEGRATION_TEST=true mvn clean test`。
真实测试使用实际 MySQL，没有 H2 替代或业务表初始化。

前端：

```bash
cd frontend
npm install
npm run build
npm run dev
```

最后在浏览器访问首页，确认 UP；仅构建成功不代表数据库和前后端已连通。

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
