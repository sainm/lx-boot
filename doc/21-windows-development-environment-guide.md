# Windows 开发环境部署文档

适用场景：

- 操作系统：Windows 11 / Windows 10
- 目标：在本机搭建 `lx-boot` 开发环境
- 后端：Spring Boot + Gradle Wrapper
- 前端：Vite + React + TypeScript
- 依赖：PostgreSQL、Redis
- Shell：PowerShell

本文档面向“开发环境”，不是生产部署文档。

与生产部署的主要差异：

- 默认直接在本机启动 `backend` 和 `admin-web`
- 默认不依赖 `spring.sql.init` 自动初始化数据库，推荐先手工导入 SQL
- PostgreSQL 与 Redis 推荐使用本机服务或 Docker Desktop
- 不涉及 Nginx、systemd、HTTPS 证书和外网发布

## 1. 目录结构

当前仓库的后端使用 Gradle composite build。

[backend/settings.gradle.kts](/D:/source/lx-boot/backend/settings.gradle.kts) 中通过 `includeBuild("../../auth-starter")` 引入兄弟仓库，所以目录结构必须保持为：

```text
<workspace>\
├─ auth-starter\
└─ lx-boot\
   ├─ backend\
   ├─ admin-web\
   └─ doc\
```

如果缺少同级的 `auth-starter` 仓库，`backend` 无法完成编译和启动。

## 2. 环境要求

建议版本：

- JDK 21
- Node.js 24.14.1
- npm 11
- PostgreSQL 15 或 16
- Redis 7
- Git

本文以 PowerShell 为例。

## 3. 安装基础工具

推荐使用 `winget` 安装。

### 3.1 安装 Git

```powershell
winget install --id Git.Git -e
```

### 3.2 安装 JDK 21

推荐使用 Temurin 21：

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
```

安装后验证：

```powershell
java -version
```

预期主版本为 `21`。

### 3.3 安装 Node.js

仓库当前约定版本为 `24.14.1`，根目录已提供 [.nvmrc](/D:/source/lx-boot/.nvmrc)。

如果你使用 `nvm-windows`：

```powershell
winget install --id CoreyButler.NVMforWindows -e
nvm install 24.14.1
nvm use 24.14.1
node -v
npm -v
```

如果你不使用 `nvm-windows`，也可以直接安装 Node.js 24：

```powershell
winget install --id OpenJS.NodeJS -e
```

说明：

- 推荐 `nvm-windows`，这样更容易和仓库版本保持一致
- 预期 `node -v` 返回 `v24.14.1`

### 3.4 安装 PostgreSQL

```powershell
winget install --id PostgreSQL.PostgreSQL.16 -e
```

### 3.5 安装 Redis

Windows 开发环境推荐二选一：

1. 使用 Docker Desktop 跑 Redis
2. 在 WSL 中安装 Redis

#### 方案 A：Docker Desktop

如果本机已安装 Docker Desktop，最简单的方式是：

```powershell
docker run -d --name lx-boot-redis -p 6379:6379 redis:7
```

如果容器已存在，可改用：

```powershell
docker start lx-boot-redis
```

#### 方案 B：WSL 安装 Redis

如果你还没有安装 WSL，可以先执行：

```powershell
wsl --install
```

安装完成后重启系统，并确认 WSL 可用：

```powershell
wsl -l -v
```

本文以 Ubuntu 为例。

进入 WSL：

```powershell
wsl
```

在 WSL 中安装 Redis：

```bash
sudo apt update
sudo apt install -y redis-server
```

建议把 Redis 监听地址改为：

```text
127.0.0.1
```

编辑配置：

```bash
sudo nano /etc/redis/redis.conf
```

确认至少包含：

```conf
bind 127.0.0.1
port 6379
protected-mode yes
```

启动 Redis：

```bash
sudo service redis-server start
```

验证：

```bash
redis-cli ping
```

预期输出：

```text
PONG
```

说明：

- 对大多数 WSL 2 开发环境，Windows 侧可以直接通过 `127.0.0.1:6379` 访问 WSL 中暴露的 Redis
- 如果你的机器网络策略比较特殊，也可以在 Windows 侧额外执行 `Test-NetConnection 127.0.0.1 -Port 6379` 确认端口是否可达

## 4. 刷新环境变量

安装完 JDK 或 Node.js 后，如果当前 PowerShell 还没拿到新环境变量，建议重新打开终端。

如果希望在当前会话刷新，可执行：

```powershell
$machine = [System.Environment]::GetEnvironmentVariables('Machine')
$user = [System.Environment]::GetEnvironmentVariables('User')
foreach ($key in $machine.Keys) { Set-Item -Path "Env:$key" -Value $machine[$key] }
foreach ($key in $user.Keys) { Set-Item -Path "Env:$key" -Value $user[$key] }
$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')

java -version
node -v
npm -v
```

## 5. 获取代码

建议工作目录：

```text
D:\source\
```

获取代码：

```powershell
cd D:\
mkdir source -Force
cd .\source

git clone <your-auth-starter-repo> auth-starter
git clone <your-lx-boot-repo> lx-boot
```

确认目录结构：

```text
D:\source\
├─ auth-starter\
└─ lx-boot\
```

## 6. 初始化 PostgreSQL

完整 SQL 来源、推荐顺序和常见坑位，统一参考 [23-database-init-guide.md](./23-database-init-guide.md)。

执行顺序：

1. 创建数据库用户
2. 创建数据库
3. 导入 `auth-starter` 运行时结构
4. 可选导入 `auth-starter` demo 种子
5. 导入 `lx-boot` 业务表 DDL
6. 可选导入 `lx-boot` 业务种子
7. 初始化超级管理员

### 6.1 创建数据库用户和数据库

进入 `psql`：

```powershell
psql -U postgres
```

执行：

```sql
CREATE USER lx WITH LOGIN PASSWORD 'lx';

CREATE DATABASE lx
  WITH OWNER = lx
       ENCODING = 'UTF8'
       TEMPLATE = template0;
```

### 6.2 导入认证基础表

```powershell
psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/auth-starter/doc/schema-postgresql.sql"
```

### 6.3 导入业务表

```powershell
psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/lx-boot/backend/src/main/resources/schema-psy.sql"
```

### 6.3.1 本地开发推荐把种子数据也一并导入

```powershell
psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/auth-starter/auth-demo/src/main/resources/data.sql"

psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/lx-boot/backend/src/main/resources/data-psy.sql"
```

说明：

- 认证 PostgreSQL 结构以 `auth-starter/doc/schema-postgresql.sql` 为准，不要再把 `auth-demo/schema.sql` 当成当前运行时的唯一结构入口
- [schema-psy.sql](/D:/source/lx-boot/backend/src/main/resources/schema-psy.sql) 是当前业务表结构正式入口
- `doc/11-database-ddl-draft.sql` 和 `doc/12-database-init-and-seed.sql` 属于历史草稿，不作为新环境初始化入口
- 推荐执行顺序是：`schema-postgresql.sql -> data.sql -> schema-psy.sql -> data-psy.sql`
- `spring.sql.init` 当前默认是 `never`，即使手工开启，也只会覆盖 `backend/src/main/resources` 下的 SQL，不会替你初始化认证结构

### 6.4 初始化 `SYS_ADMIN`

模板文件：

- [init-sys-admin.sql](/D:/source/lx-boot/doc/templates/init-sys-admin.sql)

在本机创建一个临时 SQL 文件，例如：

```powershell
@'
\set admin_username 'sysadmin'
\set admin_display_name 'System Administrator'
\set admin_password_hash '{noop}ChangeMe123'
\set admin_tenant_id 1

\i D:/source/lx-boot/doc/templates/init-sys-admin.sql
'@ | Set-Content -Path "D:\source\lx-boot\.tmp-init-sys-admin.sql" -Encoding UTF8
```

执行：

```powershell
psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/lx-boot/.tmp-init-sys-admin.sql"
```

### 6.5 校验结果

```powershell
psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -c "select id, username, tenant_id, status, deleted from sys_user where username = 'sysadmin';"

psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -c "select r.role_code from sys_user_role ur join sys_user u on u.id = ur.user_id join sys_role r on r.id = ur.role_id where u.username = 'sysadmin';"
```

如果查询结果包含 `SYS_ADMIN`，说明初始化成功。

## 7. 启动 Redis

### 7.1 如果使用 Docker

```powershell
docker start lx-boot-redis
docker ps
```

如果容器首次启动失败，可删除后重建：

```powershell
docker rm -f lx-boot-redis
docker run -d --name lx-boot-redis -p 6379:6379 redis:7
```

### 7.2 如果使用 WSL

```powershell
wsl
sudo service redis-server start
redis-cli ping
```

如果你希望从 Windows 侧验证端口：

```powershell
Test-NetConnection 127.0.0.1 -Port 6379
```

预期 `TcpTestSucceeded` 为 `True`。

## 8. 后端配置

开发环境通常可以直接使用默认配置文件：

- [application.yml](/D:/source/lx-boot/backend/src/main/resources/application.yml)

当前默认值要点：

- `server.port=8090`
- PostgreSQL 默认连接 `127.0.0.1:5432/lx`
- Redis 默认连接 `127.0.0.1:6379`
- `spring.sql.init.mode=never`

如果你希望显式覆盖本地配置，可以在 PowerShell 中设置环境变量：

```powershell
$env:PSY_DB_URL="jdbc:postgresql://127.0.0.1:5432/lx"
$env:PSY_DB_USERNAME="lx"
$env:PSY_DB_PASSWORD="lx"
$env:PSY_REDIS_HOST="127.0.0.1"
$env:PSY_REDIS_PORT="6379"
```

如果需要启用 FCM 或 HTTP Push，也可以按 [application.yml](/D:/source/lx-boot/backend/src/main/resources/application.yml:141) 中的键名配置环境变量。

## 9. 安装前端依赖

```powershell
cd D:\source\lx-boot\admin-web
npm install
```

## 10. 编译与测试

建议先做一次完整验证。

### 10.1 后端

```powershell
cd D:\source\lx-boot\backend
.\gradlew.bat build
```

### 10.2 前端

```powershell
cd D:\source\lx-boot\admin-web
npm test
npm run build
```

## 11. 启动开发环境

建议开两个 PowerShell 窗口。

### 11.1 启动后端

```powershell
cd D:\source\lx-boot\backend
.\gradlew.bat bootRun
```

默认监听：

```text
http://127.0.0.1:8090
```

### 11.2 启动前端

```powershell
cd D:\source\lx-boot\admin-web
npm run dev
```

默认访问地址通常类似：

```text
http://127.0.0.1:5173
```

以终端输出为准。

## 12. 首次登录与验证

建议最少验证以下内容：

1. 打开前端登录页
2. 使用 `sysadmin / ChangeMe123` 登录
3. 进入 Dashboard
4. 打开通知页、量表页、审计页
5. 确认后端接口无 500 错误

## 13. 常见问题

### 13.1 `backend` 编译失败，提示找不到 `auth-starter`

原因：

- 没有把 `auth-starter` 放在 `lx-boot` 同级目录

修复：

- 调整目录结构为：

```text
<workspace>\
├─ auth-starter\
└─ lx-boot\
```

### 13.2 `java -version` 不是 21

原因：

- 当前会话没有刷新环境变量
- 机器里存在多个 JDK，当前命中的是错误版本

修复：

- 重新打开 PowerShell
- 或执行第 4 节的环境变量刷新脚本

### 13.3 PostgreSQL 能连上，但启动时报表不存在

原因通常是：

- 只导入了 `auth-starter` 的表
- 没导入 [schema-psy.sql](/D:/source/lx-boot/backend/src/main/resources/schema-psy.sql)

如果报的是 `sys_user_session.device_id does not exist`，通常是：

- 跑了 `auth-starter/auth-demo/src/main/resources/schema.sql`
- 没跑或没补跑 `auth-starter/doc/schema-postgresql.sql`

这种情况下，补跑认证结构脚本即可，不需要删库重建。

### 13.4 Redis 没启动

现象通常是：

- 后端启动时报 Redis 连接失败

修复：

- 确认 Docker 容器 `lx-boot-redis` 正在运行
- 或确认 WSL 中的 `redis-server` 已启动
- 或检查你本机 Redis 监听地址是否为 `127.0.0.1:6379`

### 13.5 前端能启动但接口报 401/500

排查顺序：

1. 确认后端已经启动
2. 确认数据库和 Redis 正常
3. 确认 `sysadmin` 已初始化
4. 查看后端启动窗口日志

## 14. 相关文档

- [20-linux-deployment-guide.md](./20-linux-deployment-guide.md)
- [23-database-init-guide.md](./23-database-init-guide.md)
- [18-backend-roadmap.md](./18-backend-roadmap.md)
- [process/03-current-progress-dashboard.md](./process/03-current-progress-dashboard.md)
- [process/04-baseline-closure.md](./process/04-baseline-closure.md)
## 当前实现补充：开发环境自助注册开关

如果希望在 Windows 开发环境测试“自助注册”流程，可以在启动后端前设置：

```powershell
$env:PSY_AUTH_SELF_REGISTRATION_ENABLED="true"
```

对应后端配置项为：

```yaml
auth-module:
  registration:
    self-service-enabled: true
```

说明：

- 默认关闭
- 开启后，登录页会显示“注册账号”入口
- 开启后，匿名用户可以调用 `POST /auth/register`
- 关闭时，前端会自动隐藏入口，后端也会拒绝注册请求

## 当前实现补充：注册入口排查

如果登录页看不到“注册账号”，建议按以下顺序检查：

1. 后端是否已重启
2. 当前 PowerShell 会话是否已设置 `$env:PSY_AUTH_SELF_REGISTRATION_ENABLED="true"`
3. 前端访问 `GET /auth/register/options` 时，返回的 `selfServiceEnabled` 是否为 `true`

如果没有开启该配置，这是正常现象。
