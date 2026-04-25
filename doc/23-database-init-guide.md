# 数据库初始化与升级说明

适用仓库：
- `D:\source\auth-starter`
- `D:\source\lx-boot`

这份文档只回答 3 个问题：
- 应该跑哪几份 SQL
- 应该按什么顺序跑
- 哪些脚本不能当作当前运行时的权威 DDL

## 1. 权威 SQL

当前 PostgreSQL 基准结构以这两份为准：
- 认证基础结构：[schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)
- 心理业务结构：[schema-psy.sql](/D:/source/lx-boot/backend/src/main/resources/schema-psy.sql)

当前本地开发常用种子数据以这两份为准：
- 认证 demo 种子：[data.sql](</D:/source/auth-starter/auth-demo/src/main/resources/data.sql>)
- 心理业务种子：[data-psy.sql](/D:/source/lx-boot/backend/src/main/resources/data-psy.sql)

## 2. 不要混用的脚本

下面这些文件不要再当作“当前运行时唯一正确入口”：
- `D:\source\auth-starter\auth-demo\src\main\resources\schema.sql`
  说明：它适合 demo 场景，但可能落后于当前 auth runtime 需要的列。
- `D:\source\lx-boot\doc\11-database-ddl-draft.sql`
- `D:\source\lx-boot\doc\12-database-init-and-seed.sql`
  说明：这两份属于历史草案，不应替代正式初始化脚本。

一个已经遇到过的例子：
- `auth-demo/schema.sql` 里的 `sys_user_session` 没有 `device_id`
- 当前 auth runtime 登录时会写 `device_id`
- 结果就是 `column "device_id" of relation "sys_user_session" does not exist`

所以认证库的 PostgreSQL 结构升级，应该优先补跑 [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)。

## 3. 推荐执行顺序

### 3.1 本地开发新库

按这个顺序执行：
1. [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)
2. [data.sql](</D:/source/auth-starter/auth-demo/src/main/resources/data.sql>)
3. [schema-psy.sql](/D:/source/lx-boot/backend/src/main/resources/schema-psy.sql)
4. [data-psy.sql](/D:/source/lx-boot/backend/src/main/resources/data-psy.sql)

原因：
- `data-psy.sql` 依赖 `sys_tenant`、`sys_group`、`sys_user`、`sys_role` 等认证表
- 认证基础表必须先于业务表和业务种子
- 认证种子数据通常也应先于业务种子

### 3.2 生产或最小初始化

如果你只需要结构，不需要 demo 数据：
1. [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)
2. [schema-psy.sql](/D:/source/lx-boot/backend/src/main/resources/schema-psy.sql)

然后按需手工初始化管理员、租户、组织和业务基础数据。

### 3.3 已有库升级

如果数据库已经存在，优先补跑：
1. [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)
2. [schema-psy.sql](/D:/source/lx-boot/backend/src/main/resources/schema-psy.sql)

这两份脚本都大量使用了 `create ... if not exists` 和 `alter table ... add column if not exists`，适合做增量补齐。

## 4. 和 Spring SQL Init 的关系

[application.yml](/D:/source/lx-boot/backend/src/main/resources/application.yml) 当前默认配置是：

```yaml
spring:
  sql:
    init:
      mode: ${PSY_SQL_INIT_MODE:never}
      schema-locations: classpath:schema-psy.sql
      data-locations: classpath:data-psy.sql
```

这意味着：
- 默认不会在应用启动时自动执行 SQL
- 即使手工开启了 `PSY_SQL_INIT_MODE=always`，它也只会执行 `lx-boot` 里的 `schema-psy.sql` 和 `data-psy.sql`
- 它不会自动执行 `auth-starter/doc/schema-postgresql.sql`

所以如果你是从空库启动，不能只靠 `spring.sql.init`，必须先初始化 `auth-starter` 的认证结构。

## 5. Windows 示例

```powershell
psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/auth-starter/doc/schema-postgresql.sql"

psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/auth-starter/auth-demo/src/main/resources/data.sql"

psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/lx-boot/backend/src/main/resources/schema-psy.sql"

psql "postgresql://lx:lx@127.0.0.1:5432/lx" `
  -f "D:/source/lx-boot/backend/src/main/resources/data-psy.sql"
```

## 6. Linux 示例

```bash
psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/auth-starter/doc/schema-postgresql.sql

psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/auth-starter/auth-demo/src/main/resources/data.sql

psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/lx-boot/backend/src/main/resources/schema-psy.sql

psql "postgresql://lx:lx@127.0.0.1:5432/lx" \
  -f /srv/lx-boot/lx-boot/backend/src/main/resources/data-psy.sql
```

## 7. 常见问题

### 7.1 登录时报 `sys_user_session.device_id does not exist`

原因：
- 认证库结构停留在旧版
- 跑过了 `auth-demo/schema.sql`
- 没跑或没补跑 [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)

修复：
- 补跑 [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)

### 7.2 只跑了 `schema-psy.sql`，启动时报认证表不存在

原因：
- `schema-psy.sql` 只负责心理业务表
- 认证表不在 `lx-boot` 仓库里初始化

修复：
- 先跑 [schema-postgresql.sql](</D:/source/auth-starter/doc/schema-postgresql.sql>)

### 7.3 只开了 `PSY_SQL_INIT_MODE=always`，还是登录失败

原因：
- Spring 只会执行 `backend/src/main/resources` 里的 SQL
- 不会自动执行 `auth-starter/doc/schema-postgresql.sql`

修复：
- 先手工初始化认证结构，再启动应用
