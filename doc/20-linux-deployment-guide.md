# Linux 部署文档

适用场景：

- 部署系统：Linux
- 反向代理：Nginx
- 后端：Spring Boot JAR + systemd
- 前端：Vite 构建产物 + Nginx 静态托管
- 依赖：PostgreSQL、Redis

本文档默认采用单机部署：

- `Nginx` 对外提供 `80/443`
- `backend` 监听 `127.0.0.1:8090`
- `admin-web` 构建结果部署到 `/srv/www/lx-boot-admin`
- `PostgreSQL` 和 `Redis` 部署在本机

## 1. 最终部署结构

```text
/srv/lx-boot/
├─ backend/
│  ├─ app.jar
│  ├─ application-prod.yml
│  └─ logs/
└─ auth-starter/

/srv/www/lx-boot-admin/
├─ index.html
└─ assets/...
```

说明：

- 当前 `backend/settings.gradle.kts` 使用 `includeBuild("../../auth-starter")`
- 所以后端构建时需要能找到同级目录的 `auth-starter`
- 如果你不想保留源码级 composite build，需要先改造成发布到 Maven 私服或本地仓库的依赖方式

## 2. 服务器准备

建议机器配置：

- 2 vCPU
- 4 GB 内存
- 40 GB SSD

建议系统：

- Ubuntu 22.04 LTS
- Debian 12
- Rocky Linux 9

本文以下命令以 Ubuntu/Debian 为例。

### 2.1 创建部署用户

```bash
sudo adduser --system --group --home /srv/lx-boot lxboot
sudo mkdir -p /srv/lx-boot /srv/www/lx-boot-admin
sudo chown -R lxboot:lxboot /srv/lx-boot /srv/www/lx-boot-admin
```

### 2.2 安装系统依赖

```bash
sudo apt update
sudo apt install -y nginx postgresql redis-server unzip curl git
```

### 2.3 安装 JDK 21 和 Node.js 24

仓库根目录提供了 `.java-version` 和 `.nvmrc`，当前按本地开发环境 `Node.js 24.14.1` 对齐，建议开发机、CI 与部署机构建环境保持一致。

```bash
sudo apt install -y openjdk-21-jdk
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt install -y nodejs
```

检查版本：

```bash
java -version
node -v
npm -v
```

## 3. PostgreSQL 初始化

这一段建议严格按顺序执行：

1. 创建数据库用户
2. 创建数据库
3. 导入 `auth-starter` 认证基础表 DDL
4. 导入 `lx-boot` 心理业务表 DDL
5. 校验核心表是否已创建

说明：

- `lx-boot` 的业务表依赖 `auth-starter` 里的 `sys_user`、`sys_role`、`sys_permission` 等基础表
- 所以不能只导入 `backend/src/main/resources/schema-psy.sql`
- 如果先缺少 `auth-starter` 基础表，业务表里的外键会失败

### 3.1 创建数据库用户

先进入 PostgreSQL：

```bash
sudo -u postgres psql
```

执行：

```sql
CREATE USER auth_starter_app WITH LOGIN PASSWORD 'PleaseChangeThisPassword';
```

### 3.2 创建数据库

继续在 `psql` 里执行：

```sql
CREATE DATABASE auth_starter
  WITH OWNER = auth_starter_app
       ENCODING = 'UTF8'
       TEMPLATE = template0;
\q
```

### 3.3 导入认证基础表 DDL

当前仓库结构要求 `auth-starter` 与 `lx-boot` 同级，因此推荐直接使用 `auth-starter` 仓库中的 PostgreSQL 建表脚本：

```bash
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" \
  -f /srv/lx-boot/auth-starter/doc/schema-postgresql.sql
```

如果你是在本地 Windows 环境联调，对应路径可替换为：

```text
D:\source\auth-starter\doc\schema-postgresql.sql
```

这一步会创建认证与权限相关基础表，例如：

- `sys_user`
- `sys_auth`
- `sys_tenant`
- `sys_group`
- `sys_role`
- `sys_permission`
- `sys_user_role`

### 3.4 导入心理业务表 DDL

导入 `lx-boot` 当前真实生效的业务表脚本：

```bash
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" \
  -f /srv/lx-boot/lx-boot/backend/src/main/resources/schema-psy.sql
```

说明：

- `backend/src/main/resources/schema-psy.sql` 是当前代码实际跟随演进的业务表脚本
- `doc/11-database-ddl-draft.sql` 更适合作为设计草案阅读，不建议替代正式初始化脚本
- 如果只是本地演示或联调，才额外考虑 `doc/12-database-init-and-seed.sql`

### 3.5 校验建表结果

先确认认证基础表已经存在：

```bash
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" -c "\dt sys_*"
```

再确认心理业务表已经存在：

```bash
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" -c "\dt psy_*"
```

建议至少检查下面这些表：

- `sys_user`
- `sys_role`
- `sys_permission`
- `psy_scale`
- `psy_assessment_task`
- `psy_assessment_answer_sheet`
- `psy_assessment_result`
- `psy_warning`
- `psy_notification_delivery`
- `psy_export_job`

### 3.6 初始化超级管理员账号

`lx-boot` 业务侧统一使用 `SYS_ADMIN` 作为最高管理角色。

说明：

- `auth-starter` 底层仍兼容 `ADMIN`、`SUPER_ADMIN` 等历史角色语义
- 但 `lx-boot` 自身的管理端、菜单、页面路由、角色展示和初始化流程，统一以 `SYS_ADMIN` 收口
- 新环境部署时，建议第一时间初始化一个 `SYS_ADMIN` 账号，作为首个可登录管理账号

仓库已提供初始化脚本模板：

- [doc/templates/init-sys-admin.sql](D:/source/lx-boot/doc/templates/init-sys-admin.sql)

这个脚本只依赖以下 4 张核心表：

- `sys_user`
- `sys_auth`
- `sys_role`
- `sys_user_role`

执行前，先复制一份并替换参数：

- `admin_username`
- `admin_display_name`
- `admin_password_hash`
- `admin_tenant_id`

推荐做法：

1. 首次引导可临时使用 `{noop}ChangeMe123`
2. 首次登录后立刻通过密码修改能力改成正式高强度密码
3. 更稳妥的生产做法是直接写入 `{bcrypt}` 前缀的哈希值

如果你先用临时明文前缀方式，可以新建文件 `/srv/lx-boot/bootstrap/init-sys-admin.local.sql`：

```sql
\set admin_username 'sysadmin'
\set admin_display_name 'System Administrator'
\set admin_password_hash '{noop}ChangeMe123'
\set admin_tenant_id 1

\i /srv/lx-boot/lx-boot/doc/templates/init-sys-admin.sql
```

执行：

```bash
mkdir -p /srv/lx-boot/bootstrap
vi /srv/lx-boot/bootstrap/init-sys-admin.local.sql

psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" \
  -f /srv/lx-boot/bootstrap/init-sys-admin.local.sql
```

执行完成后，建议验证：

```bash
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" -c "select id, username, tenant_id, status, deleted from sys_user where username = 'sysadmin';"
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" -c "select a.identity_type, a.principal_key, a.enabled from sys_auth a where a.principal_key = 'sysadmin';"
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" -c "select r.role_code from sys_user_role ur join sys_user u on u.id = ur.user_id join sys_role r on r.id = ur.role_id where u.username = 'sysadmin';"
```

如果返回里包含 `SYS_ADMIN`，说明管理员账号初始化成功。

### 3.7 可选：导入演示数据

仅在本地演示、联调环境使用：

```bash
psql "postgresql://auth_starter_app:PleaseChangeThisPassword@127.0.0.1:5432/auth_starter" \
  -f /srv/lx-boot/lx-boot/doc/12-database-init-and-seed.sql
```

不建议在生产环境导入这份脚本，因为它包含示例字典和演示量表数据。

## 4. Redis 初始化

默认本机 Redis 已经够用，先确认启动：

```bash
sudo systemctl enable redis-server
sudo systemctl restart redis-server
redis-cli ping
```

预期返回：

```text
PONG
```

## 5. 获取代码

建议放到 `/srv/lx-boot` 下，并保持 `auth-starter` 与 `lx-boot` 同级：

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot
git clone <你的-auth-starter-仓库地址> auth-starter
git clone <你的-lx-boot-仓库地址> lx-boot
```

目录应为：

```text
/srv/lx-boot/
├─ auth-starter/
└─ lx-boot/
```

## 6. 构建后端

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot/backend
./gradlew clean bootJar
```

构建产物默认在：

```text
build/libs/
```

复制到运行目录：

```bash
mkdir -p /srv/lx-boot/backend/logs
cp build/libs/*.jar /srv/lx-boot/backend/app.jar
```

## 7. 构建前端

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot/admin-web
npm install
npm run build
rsync -av --delete dist/ /srv/www/lx-boot-admin/
```

## 8. 后端生产配置

创建文件：

```text
/srv/lx-boot/backend/application-prod.yml
```

内容示例：

```yaml
server:
  port: 8090
  forward-headers-strategy: framework

spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/auth_starter
    username: auth_starter_app
    password: PleaseChangeThisPassword
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0

auth-module:
  security:
    jwt:
      secret: "please-change-to-a-very-long-random-secret"

psy:
  scheduler:
    lock:
      enabled: true
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
```

补充目录：

```bash
sudo mkdir -p /srv/lx-boot/backend/export-files
sudo mkdir -p /srv/lx-boot/backend/export-artifacts
sudo chown -R lxboot:lxboot /srv/lx-boot/backend
```

## 9. systemd 配置

创建服务文件：

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

启用并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable lx-boot-backend
sudo systemctl restart lx-boot-backend
sudo systemctl status lx-boot-backend
```

查看日志：

```bash
sudo journalctl -u lx-boot-backend -f
tail -f /srv/lx-boot/backend/logs/backend.out.log
tail -f /srv/lx-boot/backend/logs/backend.err.log
```

## 10. Nginx 配置

创建站点配置：

```text
/etc/nginx/sites-available/lx-boot.conf
```

内容：

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

启用站点：

```bash
sudo ln -sf /etc/nginx/sites-available/lx-boot.conf /etc/nginx/sites-enabled/lx-boot.conf
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx
```

## 11. HTTPS 配置

如果使用公网域名，建议直接配 Let’s Encrypt：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.example.com
```

证书签发后，Nginx 会自动补充 `443` 配置与 `80 -> 443` 跳转。

## 12. 防火墙

如果启用了 `ufw`：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status
```

## 13. 上线检查

### 13.1 后端检查

```bash
curl http://127.0.0.1:8090/actuator/health
```

预期至少返回：

```json
{"status":"UP"}
```

### 13.2 前端检查

```bash
curl -I http://127.0.0.1
```

预期返回 `200 OK`。

### 13.3 API 反代检查

```bash
curl -I http://your-domain.example.com/api/v1/exports/reports/storage
```

如果未登录，返回 `401/403` 也算反代正常，只要不是 `404` 或 `502`。

## 14. 升级发布

以后每次发布，按这个顺序：

### 14.1 更新代码

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot
git pull
cd /srv/lx-boot/auth-starter
git pull
```

### 14.2 重建后端

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot/backend
./gradlew clean bootJar
cp build/libs/*.jar /srv/lx-boot/backend/app.jar
```

### 14.3 重建前端

```bash
sudo -u lxboot -H bash
cd /srv/lx-boot/lx-boot/admin-web
npm install
npm run build
rsync -av --delete dist/ /srv/www/lx-boot-admin/
```

### 14.4 重启服务

```bash
sudo systemctl restart lx-boot-backend
sudo systemctl reload nginx
```

## 15. 常见问题

### 15.1 后端构建时报找不到 `auth-starter`

原因：

- 当前后端采用 Gradle composite build
- `backend/settings.gradle.kts` 写死了 `includeBuild("../../auth-starter")`

解决：

- 按本文档保持 `/srv/lx-boot/auth-starter` 与 `/srv/lx-boot/lx-boot` 同级
- 或者自行改造成 Maven 仓库依赖

### 15.2 Nginx 返回 502

优先检查：

```bash
sudo systemctl status lx-boot-backend
sudo journalctl -u lx-boot-backend -n 100 --no-pager
curl http://127.0.0.1:8090/actuator/health
```

### 15.3 前端路由刷新 404

原因通常是 Nginx 没有配置：

```nginx
try_files $uri $uri/ /index.html;
```

### 15.4 JWT secret 使用默认值

生产环境不要使用默认值：

- `change-me-change-me-change-me-change-me`

必须替换成高强度随机字符串。

## 16. 建议的下一步生产化增强

如果准备正式上线，建议继续补：

1. PostgreSQL 定时备份
2. Nginx access/error 日志轮转
3. 后端日志轮转
4. Prometheus / Grafana 监控
5. 真实对象存储
6. 真实 Push 渠道
