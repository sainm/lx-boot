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
- `V9__append_only_scoring_results.sql`：重新评分改为追加计算版本，保留历史结果，并保证每份答卷只有一个当前结果。
- `V10__scale_content_fingerprint.sql`：保存新发布量表的 SHA-256 内容摘要，并把摘要快照传递到任务和评分结果。
- `V11__safety_response_policy_and_warning_evidence.sql`：增加需双重审批的机构安全响应策略、预警策略快照、联系/安全评估/交接证据、随访与结案检查单。
- `V12__scale_package_governance_and_localization.sql`：增加量表来源/版权/授权、五类三语内容、缺失和质量策略、效度规则、受限算法绑定以及常模来源与审核字段。
- `V13__scale_golden_cases_and_publication_reviews.sql`：增加版本化 Golden Case、不可变运行证据和绑定发布指纹的专业/业务审批记录。
- `V14__single_current_scale_version.sql`：以 PostgreSQL 部分唯一索引保证同一版本组只有一个当前已发布版本；迁移按 `.conf` 非事务并发建索引。
- `V15__warning_contact_outcome_narrative.sql`：将预警联系结果从 `varchar(64)` 放宽为叙事型 `text`；API 和 Web 仍限制每项干预证据最多 2000 字。
- `V16__high_risk_translation_and_report_locale.sql`：新增逐条高风险规则中日英翻译表，并为答卷、报告增加可空的规范语言列；历史数据不猜测回填，后续新提交由应用写入。
- `V17__validate_tenant_ownership_constraints.sql`：在 V16 全量只读归属报告通过后，补齐两条缺失的租户外键，并分开验证 13 条历史租户外键和 16 个 `tenant_id IS NOT NULL` 证明约束。
- `V18__enforce_tenant_ownership_not_null.sql`：利用 V17 已验证的证明约束，对 16 张直接租户业务表快速执行 `tenant_id NOT NULL`；全局通知/策略表、继承租户表以及允许全局 fallback 的安全策略不被错误硬化。
- `V19__export_job_retry_leases.sql`：为异步导出增加持久化处理租约、退避重试和死信状态；迁移会拒绝仍有旧版 `PROCESSING` 行的数据库，要求先停旧 Worker 并人工处置，禁止猜测回收。
- `V20__notification_delivery_processing_lease.sql`：为 Push 投递增加处理租约令牌并约束 `PROCESSING` 状态；迁移同样拒绝遗留处理中行，防止超时 Worker 在新实例恢复后迟到覆盖终态。
- `V21__scale_history_cursor_indexes.sql`：为 Golden Case 修订、运行和发布审批历史增加按量表与 ID 倒序的并发游标索引；不改写历史证据，分页 API 以单页上限保护内存。

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

V4、V8、V14 含 `CREATE INDEX CONCURRENTLY` 非事务迁移。应用内置的 `FlywayPostgresConfiguration` 和仓库 `databaseMigration` CLI 都将 PostgreSQL advisory lock 改为 session 级，仍保证同一 schema 只有一个迁移执行者，但不会让并发索引等待迁移器自己持有的旧事务快照。若绕过这两个受控入口使用独立 Flyway CLI 或其他迁移执行器，必须等价设置：

```bash
export FLYWAY_POSTGRESQL_TRANSACTIONAL_LOCK=false
```

不得通过删除 `CONCURRENTLY` 或启用 `clean` 规避迁移等待。部署时仍应设置数据库级 `lock_timeout`/`statement_timeout` 和外部作业超时，失败后检查 `pg_stat_activity`、`pg_stat_progress_create_index` 与 Flyway history，再决定前滚修复。

结构迁移与种子数据分离。`spring.sql.init` 不再执行冻结的 `schema-psy.sql`，避免在 V8+ 之后重新创建已淘汰的全局索引；正式结构只能由 Flyway 建立。开发演示数据仍需在迁移成功后显式设置 `PSY_SQL_INIT_MODE=always` 按需执行 `data-psy.sql`，且必须同时启用 Flyway；生产不得自动导入演示用户。该脚本已在迁移后的隔离 PostgreSQL schema 连续执行两次，并验证所有租户业务父子链一致；同一 schema 的真实应用双启动证据形成于 V14，V15-V21 已通过空库、baseline 增量、全量测试和恢复演练中的真实应用启动验证。未经版权、常模和专业审核的 `SCL90_TECH_DEMO` 固定保持草稿，仅是技术结构示例。

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

7. 在执行 V9 前只读检查历史答卷是否已经存在多结果或孤儿结果：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/V9__append_only_scoring_results_preflight.sql
```

两项 `issue_count` 都必须为 0。V10 不伪造旧量表的内容摘要：历史已发布量表和既有任务允许保留 `NULL`，应在业务核验后复制为新版本、重新发布，再用于要求强追溯的新任务。

8. 在执行 V11 前盘点所有历史预警；非零结果不是自动回填依据，必须由机构责任人逐条确认策略和历史结案档案：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/V11__safety_response_policy_preflight.sql
```

V11 不写入默认临床 SLA、责任人或紧急联系方式。迁移后，新预警若找不到同租户（或全局）已审批且启用的风险策略，会以 `policy_resolution_status=MISSING` 创建并进入升级候选；必须先由机构管理员和不同的有效咨询师在“安全响应策略”页面完成双重审批。

9. 在执行 V12 前盘点全部量表版本和常模。非零结果表示必须补录并由专业人员、业务负责人或法务审核，不是可以自动生成的数据：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/V12__scale_package_governance_preflight.sql
```

V12 不回填或猜测来源、版权、授权、翻译、常模样本和专业审核状态。新字段默认保持 `PENDING_REVIEW`；旧量表必须复制为新草稿版本、补齐治理信息并通过后续发布门禁后，才能宣称为正式多语言 ScalePackage。

10. 在启用 V13 发布门禁前执行只读检查。已有发布版本没有 Golden Case 是治理待办，不得自动生成答案或期望分数：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/V13__scale_publication_readiness_preflight.sql
```

V13 只创建证据结构，不写入 Golden Case 或审批。新草稿必须通过 API 创建六类样例、使用生产计分器实际运行、由不同用户完成专业与业务审批后才允许发布。

11. V14 建索引前必须确认没有同一版本组的多个当前已发布版本：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -f backend/src/main/resources/db/preflight/V14__single_current_scale_version_preflight.sql
```

脚本必须返回 0 行。若有结果，应由业务负责人确认真正的当前版本后修正标记，不得按最大 ID 自动猜测。

重复、非法和跨租户冲突项必须为 0；任何 `*_unmapped` 非 0 都必须先完成业务确认、回填或隔离，不允许把未知归属自动写入某个租户。

12. 已升级到 V16、准备执行 V17/V18 的数据库，必须先运行全量只读归属报告。脚本覆盖全部 46 张 `psy_*` 表，区分直接租户、父链继承和明确的全局表，并检查关键父子对象与人员归属：

```bash
psql -v ON_ERROR_STOP=1 -d lx \
  -c 'set search_path to public' \
  -f backend/src/main/resources/db/preflight/V16__tenant_ownership_hardening_preflight.sql
```

所有 `issue_count` 必须为 0。非零行必须先导出具体记录并由业务负责人决定正确租户或隔离区；禁止仅凭创建人、最大 ID 或默认租户猜测。该脚本只读，不执行回填。V17 执行受控扫描并验证约束，V18 才在独立迁移中取得短时表锁设置 `NOT NULL`；任一步触发 5 秒锁超时或数据冲突都必须停止发布并前滚处理，不能绕过约束。

13. 预检查通过后，显式确认并写入 V1 baseline：

```bash
cd backend
PSY_DB_URL='jdbc:postgresql://127.0.0.1:5432/lx' \
PSY_DB_USERNAME='lx' \
PSY_DB_PASSWORD='***' \
PSY_FLYWAY_OPERATION=baseline \
PSY_FLYWAY_BASELINE_APPROVED=YES \
./gradlew databaseMigration
```

14. 先查看计划，再迁移：

```bash
PSY_FLYWAY_OPERATION=info ./gradlew databaseMigration
PSY_FLYWAY_OPERATION=migrate ./gradlew databaseMigration
PSY_FLYWAY_OPERATION=validate ./gradlew databaseMigration
```

15. 启动一个实例做健康检查和核心 Case 冒烟，通过后再逐步恢复流量。

本地 `lx` 当前有 46 张 public 表（31 张 `psy_*`、15 张 `sys_*`），关键业务表当前均为 0 行，且没有 `flyway_schema_history`。2026-08-11 已在只读模式通过 baseline、V5/V7、V8、V9、V11、V12、V14 预检；随后使用 custom-format dump 克隆到专用演练库，显式 baseline V1 并执行 V2-V18 成功。原 `lx/public` 未创建 Flyway 表、未迁移且仍为 31 张心理表。空数据结果不能代替其他环境的历史脏数据审查；禁止通过清空或重建本地库来消除差异。

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
| V9 | 移除答卷结果单行唯一约束，增加计算版本、当前标记、自引用和两个唯一索引 | 唯一索引扫描结果表，DDL 需要短时元数据锁 | 先执行 V9 预检查；维护窗口执行；上线应用前完成迁移 |
| V10 | 增加量表摘要、任务摘要和结果摘要列，并仅回填已有非空摘要 | 常量默认列为元数据变更；两个部分索引扫描相应表 | 旧记录不猜测摘要；低峰执行并监控索引进度 |
| V11 | 新增 4 张安全响应证据表、策略字段和开放预警索引；旧预警仅规范优先级，不伪造策略/证据 | 新表低风险；预警表加列/索引需要短时元数据锁并扫描开放预警索引 | 先运行 V11 预检查；备份；低峰迁移；迁移后人工处理 `MISSING` |
| V12 | 新增 9 张 ScalePackage 治理和翻译表，并为常模增加 8 个来源/版本/审核字段；不自动生成治理数据 | 新表低风险；常模表加列和约束需要短时元数据锁 | 先运行 V12 预检查；低峰迁移；将历史内容保持为 `PENDING_REVIEW` 并通过新版本补录 |
| V13 | 新增 3 张 Golden Case、运行证据和发布审批表；不生成样例或审批 | 新表低风险；不会扫描历史答卷或量表内容 | 先运行 V13 预检查；应用回滚时停止发布和修改新证据，保留审计记录 |
| V14 | 新增版本组当前发布版本部分唯一索引 | `CREATE UNIQUE INDEX CONCURRENTLY` 扫描量表表并消耗 I/O；重复数据会使迁移失败 | 先运行 V14 预检查；低峰执行并监控索引进度 |
| V15 | 将 `contact_outcome` 由 `varchar(64)` 放宽为 `text` | PostgreSQL 放宽类型通常是元数据变更，仍需要短时表锁 | 低峰执行；回退缩窄前必须确认所有值长度不超过 64 |
| V16 | 新增高风险规则翻译表；答卷和报告增加可空语言列与 `NOT VALID` CHECK | 仅新增表/列；`ALTER TABLE` 仍获取短时锁，不回填历史数据 | 低峰执行；先确认没有同名对象。应用回退可保留新增结构，禁止删除已生成的语言证据 |
| V17 | 补两条租户外键、增加 16 个非空证明 CHECK，并验证共 13 条历史租户外键及全部证明约束 | `VALIDATE CONSTRAINT` 扫描目标表但不阻塞普通读写；脏数据会使事务失败 | 先保存 V16 只读报告；设置 5 秒锁超时和 10 分钟语句超时；失败后修复/隔离数据再前滚 |
| V18 | 16 张直接租户表的 `tenant_id` 设为 `NOT NULL` | 已验证 CHECK 避免重复全表扫描，但每张表仍需短时 `ACCESS EXCLUSIVE` 锁 | 与 V17 分批部署；5 秒内无法取得锁则整批回滚，重新选择维护窗口，不删除证明约束 |
| V19 | 导出任务增加租约令牌、重试次数/时间、死信时间和待重试部分索引 | 加列为兼容性变更；状态约束验证会扫描任务表；任何遗留 `PROCESSING` 行会明确阻断迁移 | 部署前停止旧 Worker，保存并人工检查处理中任务；迁移后再启动新 Worker。应用回滚前确认旧制品不会写入 `DEAD_LETTER` |
| V20 | Push 通知增加处理租约令牌及状态一致性约束 | 加列兼容；约束验证扫描通知投递表；遗留 Push `PROCESSING` 行会阻断迁移 | 部署前停止旧通知 Worker 并人工处置处理中投递；迁移后启动新 Worker。旧制品不写租约，不能在 V20 上继续处理 Push |
| V21 | Golden Case 运行历史和发布审批历史增加按 ID 倒序的游标索引 | 仅新增索引，不修改历史证据；使用 PostgreSQL 并发建索引降低表锁影响 | 部署前确认磁盘空间和索引构建窗口；失败时只重跑 V21，不修改 V1-V21 | 旧应用可继续读旧 `/history`；新分页 API 在 V21 后启用 |

V2/V3 的 `NOT VALID` 约束会立即拒绝新的非法数据，但不会在部署事务中扫描全部历史行。历史数据清理完成后，应新增独立迁移执行 `VALIDATE CONSTRAINT`，不要回改 V2/V3。

## 5. 回滚

- 应用回滚：回退到兼容旧 API 的上一构建；V2-V16 主要增加或放宽结构，V17/V18 则要求所有直接租户写入显式携带 `tenant_id`。V19 后旧应用不认识导出 `DEAD_LETTER`，V20 后旧通知 Worker 又无法写入处理租约；回滚前必须暂停两类 Worker，并确认没有 `PROCESSING`/`DEAD_LETTER` 遗留任务或投递。当前上一版本代码路径已由完整回归和开发种子验证会写租户，但任何更旧制品必须先做兼容性测试；不满足时不得在 V18 后回滚。回退期间不得触发重新评分；V11 回退应用后暂停高风险结案；V12/V13/V16 回退应用后停止编辑治理证据并暂停量表发布。已验证的外键、`NOT NULL`、V14 索引、V15 类型和 V16 新结构优先保留，不在故障窗口做破坏性降级。
- V4 失败：保留已成功的并发索引，修复原因后重跑 Flyway repair/migrate；不要盲目删除未知索引。
- 数据库回滚：优先前滚修复。只有明确验证不可兼容时，进入维护窗口并从迁移前备份恢复到新数据库，再切换连接。
- 严禁使用 `flyway clean`。应用配置和命令行均设置 `cleanDisabled=true`。

## 6. CI 验证

GitHub Actions 使用 PostgreSQL 17.6，并执行 14 个隔离 PostgreSQL Case，其中包括：

- 空 schema 执行 V1 到最新版本。
- 先建立 V1 结构，显式 baseline 到 V1，再执行增量迁移。
- 通知重试/死信、导出租约/退避/死信、追加式重评分、量表版本复制、ScalePackage、Golden Case 历史与审批幂等、单一当前发布版本约束。
- 草稿首次原子创建、同一受测者写锁和乐观版本 CAS。
- 最新 schema 上开发种子连续执行两次，以及量表、任务、答卷、预警、干预、预约、通知和导出父子租户一致性。
- V16 全量租户归属预检在最新 schema 上覆盖全部 46 张 `psy_*` 表，且开发种子的未映射、孤儿租户和父子冲突均为 0；V17/V18 进一步验证外键并将 16 张直接租户表硬化为非空。
- 完整后端回归后执行独立源库到新恢复库的 custom-format 备份恢复演练，并验证全部表行数、稳定约束属性、索引、序列、核心业务父链、数据库内导出文件摘要及恢复库应用冒烟。

测试连接会对每个新连接显式设置随机 schema；测试结束后只删除自己创建的 schema，不操作 `public`。本机 PostgreSQL 18.4 已实际完成 383 个后端测试、0 失败、0 错误、0 跳过；其中 15 个 PostgreSQL Case 验证 V1-V21 空库、V1 baseline 预检后执行 V2-V21、V15 列类型、V16 翻译/语言元数据、V17 外键验证、V18 的 16 张直接租户表非空、V19 导出重试结构、V20 通知处理租约、V21 历史游标索引、46 张心理业务表归属报告、草稿首次原子创建/同受测者写锁/版本 CAS、预警队列统计，以及通知投递原子领取、超时回收、租约 fencing、callback 状态门禁、批量重放租户隔离、死信和人工重放；导出重放/下载的租户门禁、强制审计和审计失败回滚也在真实 PostgreSQL 中验证。导出与通知恢复脚本均在随机隔离 schema 中让真实 JVM 阻塞于外部 HTTP 调用后执行 `SIGKILL`：导出恢复为 `DONE`，Push 投递恢复为 `SENT`，两者均为 `retry_count=1`、租约清空且数据库仍只有一行。重新评分现在也通过集中 `TenantAccessPolicy` 获取租户过滤：无租户非全局角色在查询结果前被拒绝，合法全局例外走统一审计。正式 `databaseMigration` CLI 的 baseline 入口使用同一 PostgreSQL dollar-quoted 预检批次，且 standalone CLI 已显式使用 session advisory lock；克隆旧库实跑证明不会再被 PL/pgSQL 分号截断或在 V4 并发索引上自阻塞。Flyway 10.20.1 对 PostgreSQL 18 会输出“最新已测试版本为 17”的警告，因此 PostgreSQL 18 生产上线仍需完成兼容性确认或受控升级 Flyway。H2 测试仍可用于纯单元级反馈，但不能作为 PostgreSQL 迁移和专属 SQL 的验收依据。

本机隔离数据库备份恢复的实际步骤、RPO/RTO 和未覆盖边界见 [PostgreSQL 备份恢复演练 Runbook](28-backup-restore-runbook.md)。该结果不能替代目标环境 PITR、外部对象存储或应用版本回滚演练。
