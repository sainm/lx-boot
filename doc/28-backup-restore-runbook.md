# PostgreSQL 备份恢复演练 Runbook

## 当前已验证边界

`scripts/run-backup-restore-rehearsal.sh` 只操作名称严格匹配 `psy_recovery_source_*` 和 `psy_recovery_restore_*` 的隔离数据库，不连接、迁移、清理或重建用户现有 `lx/public`。演练使用与应用相同的 Flyway V1-V23 和开发技术种子；技术种子不代表正式量表、授权、常模或临床内容。

本地 PostgreSQL 18.4 的 2026-08-11 演练结果：

- `pg_dump --format=custom --no-owner --no-acl` 备份为 339,486 字节，SHA-256 为 `005ddf8a86e7f6ec37a3a1f8a88e1c1a4b76806027bc09769ce0931af754557f`。
- 备份耗时 98 ms，恢复耗时 179 ms。
- 从恢复开始，到恢复库应用 ready 并完成认证和业务冒烟，实测 RTO 为 3,788 ms。
- 源库在停止应用写入后生成快照，因此相对该静态快照边界的测量 RPO 为 0 秒；这不是生产持续写入、异地复制或 PITR 的 RPO 结论。
- 源库和恢复库均应为 23 条成功 Flyway 迁移、3 个任务/答卷/结果/报告、2 个预警、1 个干预、1 条安全审计和 3 个数据库内导出文件；V17/V18 的租户约束、V19 导出租约、V20 通知投递租约、V21 历史游标索引、V22 质量留痕结构及 V23 评分轨迹 JSONB 约束均包含在 manifest 对比中。
- 全部 public 表行数、稳定约束属性、索引、序列和规范化 data-only dump 完全一致；核心任务/量表/答卷/结果/报告/预警/干预租户父链无冲突。
- 恢复库使用同一 `bootJar` 启动，默认租户和校园租户分别登录并验证任务、报告列表/详情、安全审计、预警和数据库内导出文件下载；下载内容 SHA-256 与数据库 `file_bytes` 一致。
- 演练结束后源库、恢复库、应用进程和临时成功产物均自动清理。

## 执行

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export GRADLE_USER_HOME=/Users/sainm/.gradle
./scripts/run-backup-restore-rehearsal.sh
```

非默认连接通过专用变量提供，不能复用或覆盖通用 `HOME`、`PGDATABASE` 等环境：

```bash
PSY_RECOVERY_DB_HOST=localhost \
PSY_RECOVERY_DB_PORT=5432 \
PSY_RECOVERY_DB_USERNAME=psy_ci \
PSY_RECOVERY_DB_PASSWORD=psy_ci \
PSY_RECOVERY_BACKEND_PORT=8091 \
./scripts/run-backup-restore-rehearsal.sh
```

脚本会拒绝不符合专用前缀的数据库名、相同的源/目标库、非法端口以及已被占用的应用端口。失败时保留临时 dump、manifest 和应用日志用于排查，但仍默认删除自己创建的隔离数据库。只有显式设置 `PSY_RECOVERY_KEEP_DATABASES=true` 才保留隔离库；人工排查后必须按脚本输出的精确名称删除，禁止使用通配符删除数据库。

## 验收内容

1. 从 `template0` 分别建立源库和空恢复库。
2. 使用同一不可变 `bootJar` 对源库执行全部 Flyway 迁移，并加载技术种子。
3. 通过真实应用接口完成两个租户的登录、任务、报告、预警和导出下载，形成登录及报告查看审计。
4. 停止应用写入后生成 custom-format 备份，记录大小、SHA-256 和耗时。
5. 使用 `pg_restore --exit-on-error --single-transaction --no-owner --no-acl` 恢复到新数据库。
6. 在源库和恢复库执行相同核心 SQL 断言；比较所有表行数、稳定约束属性、索引、序列和数据 dump。
7. 在恢复库启动应用，重新执行租户化业务和文件摘要冒烟，记录恢复开始到业务冒烟完成的时间。
8. 清理仅由本次演练创建的进程、数据库和临时文件。

## 尚未闭环

- 当前只验证 PostgreSQL 全库备份以及存放于 `psy_export_job.file_bytes` 的数据库内文件。文件系统、S3/兼容对象存储、邮件或 Push 供应商数据没有恢复证据。
- 没有配置 WAL 归档、连续备份或时间点恢复，因此不能声称支持 PITR，也不能把静态 RPO 0 秒用于生产承诺。
- 没有演练跨主机、跨可用区、加密备份、密钥恢复、异地保留、备份介质损坏和大规模数据恢复。
- 没有用上一应用版本执行扩展/收缩兼容和制品回滚；当前只证明同一 `bootJar` 可在恢复库启动。
- 当前毫秒结果来自小型技术数据，不能外推生产容量；生产 RPO/RTO 必须在目标基础设施和真实规模脱敏数据上重新测量，并由业务与运维负责人签字。
