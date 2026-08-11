# 性能与容量基线（非生产容量承诺）

## 当前结论

2026-08-11 已在本机 PostgreSQL 18.4 的一次性 `psy_perf_*` schema 上完成可重复的 1x/10x 技术基线：默认分别生成 100/1,000 个已提交任务，并额外生成答卷保存/提交用的 live task。基线使用当前不可变 `bootJar`，真实登录后调用后端 HTTP，而不是只执行 Repository 单测。

本次实跑结果：所有 HTTP Case 0 错误；每个读 Case 15 次、2 次 warm-up；保存和提交评分各使用 15 个独立 live task。脚本输出了每个 Case 的 p50/p95/p99、串行客户端吞吐、错误率、Hikari/JVM/CPU Actuator 快照、数据库连接/锁/块读写快照，以及任务列表、报告列表、预警列表三类 `EXPLAIN (ANALYZE, BUFFERS)`。一次实际产物位于 `/tmp/psy-perf-final-20260811211202`；临时 schema 已由脚本清理。

默认 100/1,000 任务实跑的代表性结果如下（单位 ms；串行客户端吞吐不是并发容量）：

| Case | 1x p50/p95/p99 | 10x p50/p95/p99 |
|---|---:|---:|
| 任务列表 | 4.035 / 6.137 / 6.137 | 2.346 / 4.081 / 4.081 |
| 报告列表 | 9.179 / 12.861 / 12.861 | 8.083 / 12.893 / 12.893 |
| 群体统计 | 13.508 / 20.309 / 20.309 | 8.859 / 9.642 / 9.642 |
| 答卷保存 | 4.879 / 34.902 / 34.902 | 3.610 / 5.515 / 5.515 |
| 提交与自动评分 | 8.475 / 35.324 / 35.324 | 5.543 / 7.425 / 7.425 |

这些数字是本机、串行客户端、技术夹具和当前代码的比较基线，不是生产 SLO、并发吞吐或容量承诺。真实容量仍需专用 PostgreSQL、代表性脱敏数据、并发压测、资源隔离、连接池/JVM/磁盘监控和业务负责人批准的压测窗口。

## 如何运行

```bash
cd /Users/sainm/work/github/lx-boot
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
GRADLE_USER_HOME=/Users/sainm/.gradle \
./scripts/run-performance-baseline.sh
```

可通过环境变量调整，但仍只能指向一次性技术 schema：

- `PSY_PERF_DB_HOST/PORT/NAME/USERNAME/PASSWORD`：隔离 PostgreSQL 连接。
- `PSY_PERF_TARGET_1X`、`PSY_PERF_TARGET_10X`：目标任务行数，10x 必须大于 1x。
- `PSY_PERF_REQUESTS`、`PSY_PERF_WARMUPS`：读 Case 样本和预热次数。
- `PSY_PERF_OUTPUT_DIR`：原始 JSON、EXPLAIN、Actuator 和摘要输出目录；默认在 `/tmp`。
- `PSY_PERF_SCHEMA`：可选 schema 名，必须匹配 `psy_perf_*`。

脚本会：

1. 构建当前 `bootJar`，创建 `psy_perf_*` schema，让 Flyway V1-V21 在该 schema 执行。
2. 载入明确标注为技术夹具、非临床、无版权效力的 ScalePackage 示例。
3. 先测量 1x，再向同一个 schema 增量到 10x，避免把两套不相同的初始化过程混在比较中。
4. 真实调用任务/报告/预警/统计/群体统计/导出/通知/预约/被测者列表、题目查询、答卷保存和提交评分。
5. 保存 `EXPLAIN (ANALYZE, BUFFERS)`，通过受保护 Actuator 读取 Hikari/JVM/CPU，并读取 PostgreSQL 连接、块读写、锁和临时空间快照。
6. 退出时停止本次后端并删除该 schema；失败时保留输出目录和后端日志，便于审查。

脚本不会自动创建或启用 `pg_stat_statements`，也不会修改 PostgreSQL server 配置。2026-08-11 的实际检查为：`shared_preload_libraries` 为空、扩展未安装、`track_io_timing=off`。因此 `pg_stat_statements` 仍是外部基础设施待办，不能伪称已完成。

## Case 与证据边界

| Case | 当前脚本证据 | 尚未证明 |
|---|---|---|
| 任务/报告/预警列表 | 1x/10x HTTP + EXPLAIN | 并发用户容量、深度分页长期趋势 |
| 答卷保存 | 独立 live task 的真实 POST | 多实例并发压力、移动端网络条件 |
| 答卷提交与自动评分 | 独立 live task 的真实 POST，包含报告/通知/风险计算路径 | 正式授权量表的临床算法性能 |
| 统计/群体统计/趋势 | 真实 HTTP，含 GROUP assignment 和相关子查询 | 生产级群体规模、长时间窗口聚合 |
| 导出任务、通知、预约 | 真实列表查询 | 大文件流式生成、真实供应商吞吐和限流 |
| Hikari/JVM/CPU | 受保护 Actuator 快照 | 长时间 GC、峰值连接池、磁盘和容器资源 |
| PostgreSQL | 资源快照和三类 `EXPLAIN (ANALYZE, BUFFERS)` | `pg_stat_statements`、并发锁争用、PITR/生产备份窗口 |

只有在相同数据、相同 Case、相同并发模型下保存优化前后结果，才能把某项优化写成性能改善。性能基线本身不授权新增索引；索引和流式导出改动必须另开小批次并保留前后报告。

Android 按当前范围明确延期；本脚本只验证后端和 PostgreSQL，不把 Android 未运行状态标记为通过。
