# Linux 部署文档

适用场景：

- 操作系统：Linux
- 反向代理：Nginx
- 后端：Spring Boot JAR + systemd
- 前端：Vite 构建产物 + Nginx 静态托管
- 依赖：PostgreSQL、Redis

本文默认采用单机部署：

- `Nginx` 对外提供 `80/443`
- `backend` 监听 `127.0.0.1:8090`
- `admin-web` 发布到 `/srv/www/lx-boot-admin`
- `PostgreSQL` 与 `Redis` 同机部署

## 1. 最终目录结构

```text
/srv/lx-boot/
├─ auth-starter/
├─ lx-boot/
├─ backend/
│  ├─ app.jar
│  ├─ application-prod.yml
│  └─ logs/
└─ bootstrap/

/srv/www/lx-boot-admin/
├─ index.html
└─ assets/...
```

说明：

- `backend/settings.gradle.kts` 当前使用 `includeBuild("../../auth-starter")`
- 所以 `auth-starter` 必须和 `lx-boot` 保持同级目录

## 2. 服务器准备

建议最低配置：

- 2 vCPU
- 4 GB RAM
- 40 GB SSD

建议系统：

- Ubuntu 22.04 LTS
- Debian 12
- Rocky Linux 9

以下命令以 Ubuntu 或 Debian 为例。

### 2.1 创建部署用户

```bash
sudo adduser --system --group --home /srv/lx-boot lxboot
sudo mkdir -p /srv/lx-boot /srv/www/lx-boot-admin
sudo chown -R lxboot:lxboot /srv/lx-boot /srv/www/lx-boot-admin
```

### 2.2 安装系统依赖

```bash
sudo apt update
sudo apt install -y nginx postgresql redis-server unzip curl git rsync
```

### 2.3 安装 JDK 21 和 Node.js 24.14.1

仓库当前与本地开发环境对齐为 `JDK 21` 和 `Node.js 24.14.1`。

```bash
sudo apt install -y openjdk-21-jdk
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt install -y nodejs
sudo npm install -g n
sudo n 24.14.1
hash -r
```

校验版本：

```bash
java -version
node -v
npm -v
```

预期 `node -v` 返回 `v24.14.1`。

## 3. PostgreSQL 初始化

完整 SQL 来源、推荐顺序和升级修复建议，统一参考 [23-database-init-guide.md](./23-database-init-guide.md)。

执行顺序：

1. 创建数据库用户
2. 创建数据库
3. 导入 `auth-starter` 运行时结构
4. 可选导入 `auth-starter` demo 种子
5. 导入 `lx-boot` 业务表 DDL
6. 可选导入 `lx-boot` 业务种子
7. 初始化超级管理员
8. 校验表和账号

### 3.1 创建数据库用户

```bash
sudo -u postgres psql
```

```sql
CREATE USER lx WITH LOGIN PASSWORD 'lx';
```

### 3.2 创建数据库

```sql
CREATE DATABASE lx
  WITH OWNER = lx
       ENCODING = 'UTF8'
       TEMPLATE = template0;
\q
```

### 3.3 导入认证基础表

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/auth-starter/doc/schema-postgresql.sql
```

### 3.4 可选导入认证 demo 种子

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/auth-starter/auth-demo/src/main/resources/data.sql
```

### 3.5 导入业务表

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/lx-boot/backend/src/main/resources/schema-psy.sql
```

说明：
- `auth-starter/doc/schema-postgresql.sql` 是认证运行时 PostgreSQL 结构权威入口，不要再把 `auth-demo/schema.sql` 当成当前运行时唯一基准。
- `schema-psy.sql` 是当前业务表结构的唯一正式 DDL 入口。
- 早期 DDL/种子草案已从仓库移除（历史版本仍可在 git 历史中查到），当前结构入口只有 Flyway 迁移和上述两份权威 schema。
- `application.yml` 当前默认 `spring.sql.init.mode: never`；即使手工开启，它也只会执行 `backend/src/main/resources` 里的 SQL，不会自动初始化认证结构。

### 3.6 可选导入业务种子

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/lx-boot/backend/src/main/resources/data-psy.sql
```

### 3.7 初始化 `SYS_ADMIN`

模板文件：

- [init-sys-admin.sql](/D:/source/lx-boot/doc/templates/init-sys-admin.sql)

创建本地初始化脚本：

```bash
mkdir -p /srv/lx-boot/bootstrap
cat >/srv/lx-boot/bootstrap/init-sys-admin.local.sql <<'SQL'
\set admin_username 'sysadmin'
\set admin_display_name 'System Administrator'
\set admin_password_hash '{noop}ChangeMe123'
\set admin_tenant_id 1

\i /srv/lx-boot/lx-boot/doc/templates/init-sys-admin.sql
SQL
```

执行初始化：

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/bootstrap/init-sys-admin.local.sql
```

### 3.8 校验结果

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" -c "\dt sys_*"
psql "postgresql://lx:lx@127.0.0.1:5432/lx" -c "\dt psy_*"
psql "postgresql://lx:lx@127.0.0.1:5432/lx" -c "select id, username, tenant_id, status, deleted from sys_user where username = 'sysadmin';"
psql "postgresql://lx:lx@127.0.0.1:5432/lx" -c "select r.role_code from sys_user_role ur join sys_user u on u.id = ur.user_id join sys_role r on r.id = ur.role_id where u.username = 'sysadmin';"
```

若最后一条查询包含 `SYS_ADMIN`，说明初始化成功。

## 4. Redis 初始化

```bash
sudo systemctl enable redis-server
sudo systemctl restart redis-server
redis-cli ping
```

预期输出：

```text
PONG
```

## 5. 获取代码

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot
git clone <your-auth-starter-repo> auth-starter
git clone <your-lx-boot-repo> lx-boot
```

说明：

- `backend/settings.gradle.kts` 通过 `includeBuild("../../auth-starter")` 引入兄弟仓库，两个仓库必须保持同级目录检出，否则后端无法编译
- 两个仓库的分支必须**成对**发布：`lx-boot` 的 SSO/社交登录流程依赖 `auth-starter` 侧的配套修复，混用分支会得到行为不一致的制品；建议用 tag 或在部署脚本里同时锁定两者的分支名

## 6. 构建后端

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot/backend
./gradlew clean bootJar
mkdir -p /srv/lx-boot/backend/logs
BOOT_JAR="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
install -m 644 "$BOOT_JAR" /srv/lx-boot/backend/app.jar
```

说明：

- 不要使用 `cp build/libs/*.jar`，否则可能误选 `-plain.jar`
- 如果产物命名策略变更，只需要保证排除 `*-plain.jar`

## 7. 构建前端

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot/admin-web
npm install
npm run build
rsync -av --delete dist/ /srv/www/lx-boot-admin/
```

构建前必须提供前端构建期变量（Vite 在构建时把值内联进产物，缺失只会静默降级）：

```bash
export VITE_WECHAT_APP_ID='<公众号 AppID>'
export VITE_SSO_OIDC_ENABLED=true      # 需要 OIDC 单点登录时
export VITE_SSO_CAS_ENABLED=false      # 需要 CAS 单点登录时
```

也可以复制仓库内的 `admin-web/.env.example` 为 `.env.local` 后再构建。未设置 `VITE_WECHAT_APP_ID` 时微信入口会显示错误页而不是跳转授权；两个 SSO 开关都为 `false`（默认）时登录页不显示 SSO 按钮。

## 8. 后端生产配置

创建：

```text
/srv/lx-boot/backend/application-prod.yml
```

示例：

```yaml
server:
  port: 8090
  forward-headers-strategy: framework

spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/lx
    username: lx
    password: lx
  sql:
    init:
      mode: never
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0

auth-module:
  security:
    jwt:
      secret: "please-change-to-a-very-long-random-secret"
  device-governance:
    device-stale-days: 30
    session-stale-days: 7
    required-push-token-device-types:
      - ANDROID
      - IOS

psy:
  export:
    jobs:
      file-storage-enabled: true
      storage-dir: /srv/lx-boot/backend/export-files
      artifact-storage:
        mode: LOCAL_PATH
        base-dir: /srv/lx-boot/backend/export-artifacts
        key-prefix: export-artifacts
        bucket: psy-export-artifacts
  notification:
    push:
      http:
        enabled: false
        endpoint-url: ""
        authorization-token: ""
        provider-name: http
      fcm:
        enabled: false
        project-id: ""
        service-account-json: ""
        service-account-json-path: ""
```

配置说明：
- `spring.sql.init.mode: never` 表示生产环境以第 3 节手工导入 DDL 为准；如果你希望应用启动时自动执行 `schema-psy.sql`，可删除该配置，但不要同时把两套流程都当作必做步骤。
- `auth-module.registration.self-service-enabled` 用于控制是否开放匿名自助注册；生产环境建议默认关闭，只在明确需要开放注册时改为 `true`。
- `auth-module.device-governance.*` 用于设备画像、异常设备自动处置和 Push Token 必填策略。
- `psy.notification.push.http.*` 适合接入自建 Push 代理或第三方网关。
- `psy.notification.push.fcm.*` 适合直接接入 Firebase Cloud Messaging；如使用 `service-account-json`，请通过外部安全配置注入，不建议提交到仓库。

补充目录：

```bash
sudo mkdir -p /srv/lx-boot/backend/export-files
sudo mkdir -p /srv/lx-boot/backend/export-artifacts
sudo chown -R lxboot:lxboot /srv/lx-boot/backend
```

## 9. systemd 配置

文件：

```text
/etc/systemd/system/lx-boot-backend.service
```

内容：

```ini
[Unit]
Description=lx-boot backend
After=network.target postgresql.service redis-server.service
Wants=postgresql.service redis-server.service

[Service]
Type=simple
User=lxboot
Group=lxboot
WorkingDirectory=/srv/lx-boot/backend
Environment=SPRING_PROFILES_ACTIVE=prod
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /srv/lx-boot/backend/app.jar --spring.config.additional-location=file:/srv/lx-boot/backend/application-prod.yml
SuccessExitStatus=143
Restart=always
RestartSec=5
StandardOutput=append:/srv/lx-boot/backend/logs/backend.out.log
StandardError=append:/srv/lx-boot/backend/logs/backend.err.log
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

启用：

```bash
sudo systemctl daemon-reload
sudo systemctl enable lx-boot-backend
sudo systemctl restart lx-boot-backend
sudo systemctl status lx-boot-backend
```

## 10. Nginx 配置

文件：

```text
/etc/nginx/sites-available/lx-boot.conf
```

```nginx
server {
    listen 80;
    server_name your-domain.example.com;

    root /srv/www/lx-boot-admin;
    index index.html;
    client_max_body_size 50m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
    }

    location /auth/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        allow 127.0.0.1;
        deny all;
    }
}
```

启用：

```bash
sudo ln -sf /etc/nginx/sites-available/lx-boot.conf /etc/nginx/sites-enabled/lx-boot.conf
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx
```

## 11. HTTPS

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.example.com
```

## 12. 上线检查

```bash
curl http://127.0.0.1:8090/actuator/health
curl -I http://127.0.0.1
curl -I http://your-domain.example.com/api/v1/exports/reports/storage
```

判断标准：

- 健康检查返回 `{"status":"UP"}`
- 首页返回 `200 OK`
- API 反代即使未登录，返回 `401` 或 `403` 也算正常，只要不是 `404` 或 `502`

健康检查说明：

- `/actuator/health` 允许匿名访问，`prometheus` / `metrics` 仍需认证；组件状态通过 `show-components` 暴露，连接明细不暴露。
- 未配置 SMTP 时 mail 健康指示器默认关闭（`PSY_MAIL_HEALTH_ENABLED=false`，见 `management.health.mail.enabled`），不会把实例拖成 `DOWN`。
  **配置真实 SMTP 后请设置 `PSY_MAIL_HEALTH_ENABLED=true`**，让邮件通道故障体现在健康检查中。
- 导出任务保留策略：`PSY_EXPORT_JOB_RETENTION_SECONDS`（默认 900s，DONE/FAILED）、
  `PSY_EXPORT_DEAD_LETTER_RETENTION_SECONDS`（默认 604800s，死信等待人工重放）、
  `PSY_EXPORT_CLEANUP_SCAN_DELAY_MS`（默认 300000ms）。

## 13. 升级发布

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot && git pull
cd /srv/lx-boot/auth-starter && git pull
cd /srv/lx-boot/lx-boot/backend && ./gradlew clean bootJar
BOOT_JAR="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
install -m 644 "$BOOT_JAR" /srv/lx-boot/backend/app.jar
cd /srv/lx-boot/lx-boot/admin-web && npm install && npm run build
rsync -av --delete /srv/lx-boot/lx-boot/admin-web/dist/ /srv/www/lx-boot-admin/
sudo systemctl restart lx-boot-backend
sudo systemctl reload nginx
```

## 14. 常见问题

### 14.5 生产环境需要开放自助注册

如果当前部署环境需要让用户从登录页自行注册账号，请在生产配置中显式打开：

```yaml
auth-module:
  registration:
    self-service-enabled: true
```

或使用环境变量：

```bash
export PSY_AUTH_SELF_REGISTRATION_ENABLED=true
```

说明：

- 未开启时，登录页不会显示“注册账号”入口
- 未开启时，直接调用 `POST /auth/register` 也会返回“当前未开放自助注册”
- 开启后仍建议配合业务侧组织、租户和用户资料策略使用，避免把开放注册误当成完整招生/入驻流程

### 14.1 后端构建报找不到 `auth-starter`

保持：

```text
/srv/lx-boot/auth-starter
/srv/lx-boot/lx-boot
```

为同级目录。

### 14.2 Nginx 返回 502

优先检查：

```bash
sudo systemctl status lx-boot-backend
sudo journalctl -u lx-boot-backend -n 100 --no-pager
curl http://127.0.0.1:8090/actuator/health
```

### 14.3 前端刷新 404

确认 Nginx 包含：

```nginx
try_files $uri $uri/ /index.html;
```

### 14.4 生产环境不要使用默认 JWT Secret

必须替换默认值：

- `change-me-change-me-change-me-change-me`
