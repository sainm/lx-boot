# PostgreSQL 初始化、升级与回滚手册

## 1. 权威入口

应用结构从本次改造起由 Flyway 管理：

- `V1__application_baseline.sql`：认证模块与心理业务的冻结基线。
- `V2__business_data_guards.sql`：关键状态、时间范围和非负值约束。
- `V3__notification_delivery_retry_state.sql`：通知重试、超时恢复和死信字段。
- `V4__notification_delivery_retry_indexes.sql`：PostgreSQL 并发索引；按相邻 `.conf` 以非事务方式执行。
- `V5__assessment_integrity_guards.sql`：答卷幂等、分配、预警和报告唯一性与类型约束。
- `V6__tenant_ownership_columns.sql`：核心业务表租户归属、回填、索引与 `NOT VALID` 外键。
- `V7__scale_average_score_methods.sql`：增加平均分、加权平均分计分方式并保持数据库白名单。
- `V8__tenant_scoped_scale_identity.sql`：把量表编码版本唯一性调整为租户内唯一，并补量表导入租户索引和外键。

已经提交或在任一环境执行的迁移文件禁止修改。后续变更必须新增版本。

`backend/src/main/resources/schema-psy.sql` 和 `auth-starter/doc/schema-postgresql.sql` 仅作为 V1 的历史来源，不再作为生产升级入口。`spring.sql.init` 默认保持关闭。

## 2. 新数据库

先创建空数据库并授予应用用户 DDL 权限，然后执行：

```bash
cd backend
PSY_DB_URL='jdbc:postgresql://127.0.0.1:5432/lx' \
PSY_DB_USERNAME='lx' \
PSY_DB_PASSWORD='***' \
PSY_FLYWAY_OPERATION=migrate \
./gradlew databaseMigration
```

也可以在首次启动时显式设置 `PSY_FLYWAY_ENABLED=true`。默认值为 `false`，防止旧环境在未审核时被自动迁移。

结构迁移与种子数据分离。开发演示数据仍需在迁移成功后按需执行 `data-psy.sql`；生产不得自动导入演示用户。

## 3. 已有数据库 baseline

已有数据库不能自动 baseline。必须按顺序执行：

1. 停止写入或进入维护窗口。
2. 完成 PostgreSQL 物理快照或 `pg_dump --format=custom` 备份，并验证备份可读。
3. 记录数据库名、应用版本、V1 文件校验和和备份位置。
4. 运行只读预检查：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/existing-database-baseline.sql
```

5. 在执行 V5-V7 前运行数据完整性和租户归属预检查：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/V5_V7__integrity_and_tenant_preflight.sql
```

6. 在执行 V8 前运行量表身份冲突预检查（脚本按 V6 相同规则推导租户，可在 baseline 前执行）：

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f backend/src/main/resources/db/preflight/V8__tenant_scoped_scale_identity_preflight.sql
```

重复、非法和跨租户冲突项必须为 0；任何 `*_unmapped` 非 0 都必须先完成业务确认、回填或隔离，不允许把未知归属自动写入某个租户。

7. 预检查通过后，显式确认并写入 V1 baseline：

```bash
cd backend
PSY_DB_URL='jdbc:postgresql://127.0.0.1:5432/lx' \
PSY_DB_USERNAME='lx' \
PSY_DB_PASSWORD='***' \
PSY_FLYWAY_OPERATION=baseline \
PSY_FLYWAY_BASELINE_APPROVED=YES \
./gradlew databaseMigration
```

8. 先查看计划，再迁移：

```bash
PSY_FLYWAY_OPERATION=info ./gradlew databaseMigration
PSY_FLYWAY_OPERATION=migrate ./gradlew databaseMigration
PSY_FLYWAY_OPERATION=validate ./gradlew databaseMigration
```

9. 启动一个实例做健康检查和核心 Case 冒烟，通过后再逐步恢复流量。

本地 `lx` 当前有 46 张 public 表，而当前 V1 定义 44 张表；额外表不会被删除，但必须在 baseline 审批记录中注明。禁止通过清空或重建本地库来消除差异。

## 4. 数据影响与锁

| 版本 | 数据影响 | 主要锁风险 | 控制方式 |
|---|---|---|---|
| V1 | 仅新库创建完整结构 | 新库无历史流量 | 已有库只 baseline，不执行 V1 |
| V2 | 新写入受 CHECK 保护 | 添加 `NOT VALID` 约束需要短时元数据锁 | 不扫描历史大表；先预检查，再另版 VALIDATE |
| V3 | 通知表新增 4 列 | 常量默认列为元数据变更；仍需维护窗口监控 | 小批发布，观察锁等待 |
| V4 | 新增 3 个部分索引 | 普通建索引可能阻塞写入 | `CREATE INDEX CONCURRENTLY` 且非事务执行 |
| V5 | 新增部分唯一索引和 `NOT VALID` CHECK | 唯一索引会扫描表，脏数据会失败 | 先运行 V5-V7 预检查并清理重复数据 |
| V6 | 新增租户列、回填、索引、外键 | 回填产生行更新；索引创建持有锁 | 维护窗口执行；先核对映射率和冲突，禁止猜测归属 |
| V7 | 替换计分方式 CHECK | 短时元数据锁 | 先确认没有白名单外计分字符串 |
| V8 | 并发替换量表唯一索引并增加租户索引/外键 | 不阻塞普通读写，但索引构建消耗 I/O；外键需要短时元数据锁 | 先运行 V8 预检查；低峰执行并监控索引进度 |

V2/V3 的 `NOT VALID` 约束会立即拒绝新的非法数据，但不会在部署事务中扫描全部历史行。历史数据清理完成后，应新增独立迁移执行 `VALIDATE CONSTRAINT`，不要回改 V2/V3。

## 5. 回滚

- 应用回滚：回退到兼容旧 API 的上一构建；V2-V8 均保持已有列和 API 可兼容。
- V4 失败：保留已成功的并发索引，修复原因后重跑 Flyway repair/migrate；不要盲目删除未知索引。
- 数据库回滚：优先前滚修复。只有明确验证不可兼容时，进入维护窗口并从迁移前备份恢复到新数据库，再切换连接。
- 严禁使用 `flyway clean`。应用配置和命令行均设置 `cleanDisabled=true`。

## 6. CI 验证

GitHub Actions 使用 PostgreSQL 17.6，并执行两个隔离 schema Case：

- 空 schema 执行 V1 到最新版本。
- 先建立 V1 结构，显式 baseline 到 V1，再执行增量迁移。

测试结束后只删除自己创建的随机 schema，不操作共享数据库。H2 测试仍可用于纯单元级反馈，但不能作为 PostgreSQL 迁移和专属 SQL的验收依据。
