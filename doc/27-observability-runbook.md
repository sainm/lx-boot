# 可观测性与告警 Runbook

## 当前边界

- `/actuator/prometheus` 由现有 Spring Security 保护，不对匿名请求开放。采集端必须使用受控服务账号取得的短期 Bearer Token；不得使用个人管理员 Token，也不得把 Token 写入仓库或 Prometheus 规则文件。
- 指标标签只允许固定枚举，不包含 tenant ID、user ID、task ID、answer sheet ID、量表代码、提交 Token 或敏感答案。
- `X-Correlation-Id` 只接受 1–64 位字母、数字、点、下划线和连字符。非法值由服务端替换为 UUID；响应头和日志 `correlation_id` 使用同一个值。
- 服务端使用 W3C trace context 和 Micrometer Brave bridge；本地结构化日志包含 `trace_id`/`span_id`，默认采样率为 `0.1`，可通过 `PSY_TRACING_SAMPLING_PROBABILITY` 调整。当前没有外部 trace 存储或导出器；对象存储的手工 Java `HttpClient` 尚未完成自动跨服务传播，因此本地字段存在不等于分布式追踪闭环。
- [Prometheus 规则](../ops/prometheus/psy-alert-rules.yml) 已通过配置测试，但尚未接入真实 Prometheus/Alertmanager，也未配置机构值班人。规则通过不等于告警闭环。

## 通用处置

1. 保存告警开始时间、实例、环境、规则名和当前值。
2. 使用 correlation id 关联 API 响应、应用日志和安全审计；禁止在工单中复制答卷、Token、密码或通知载荷。
3. 确认影响租户时只记录租户内部编号或经批准的运维标识，不在低基数指标中加入租户标签。
4. 记录缓解动作、恢复时间、根因和是否需要数据修复；心理安全预警不得仅因系统指标恢复而自动关闭。

## Backend high 5xx rate

检查 `http_server_requests_seconds_count` 的 URI、状态和实例分布，再检查 Hikari、JVM、PostgreSQL 连接/锁和最近部署。需要回滚时先停止新迁移和异步发布操作，保留追加式结果与审计数据。

## Database pool saturation

检查活动、空闲、等待和最大连接数，结合 PostgreSQL 活跃查询和锁等待判断是慢查询、事务过长还是容量不足。不得只提高连接池大小；先保存相同 Case 的查询和资源证据。

## Assessment submission failures

按 correlation id 检查稳定业务错误码，区分合法版本冲突、输入校验、数据库错误和评分失败。确认幂等唯一约束仍保证每次提交只有一个当前结果，不得手工覆盖已有结果。

## Scoring failure

立即暂停受影响量表版本的新任务或发布，保存量表版本、内容摘要、算法版本和 Golden Case 结果。不得降级为简单求和；修复后使用新计算版本重评分并保留历史。

## Overdue safety warning

这是临床安全告警。按预警不可变策略快照联系机构责任人和备份角色，记录联系、安全评估、责任交接和随访。监控恢复不能替代人工处置和关闭检查单。

## Notification dead letter

在通知运维页检查脱敏错误、重试次数和下一次重试时间。确认供应商幂等键后再人工重放；不得把 Token、密码、答卷内容写入失败原因。

## Notification backlog

检查 Worker 是否运行、原子领取是否推进、是否存在 `PROCESSING` 超时回收，以及外部网关延迟。超过最大次数的记录必须进入死信并由授权人员重放。

## Scheduler failure

检查分布式锁、上一轮开始/完成时间和失败异常。若 Worker 在领取后崩溃，验证超时回收后再恢复实例，避免并行手工重复执行。

## Export failure

检查作业状态、存储超时和脱敏错误。确认目标报告租户与创建者权限后再重试；禁止从数据库直接导出匿名身份映射或敏感答卷。

## 上线前仍需完成

- 在目标环境配置 Prometheus 服务账号、抓取间隔、TLS 和凭据轮换。
- 选择并配置受控的 trace 后端与导出协议，定义保留期、访问权限、敏感属性白名单和采样策略；为手工 Java `HttpClient` 对象存储调用补 trace context 传播，并用真实下游验证父子 span。
- 由业务负责人、心理安全责任人和运维确定 severity、值班路由、静默规则和升级时限。
- 使用受控故障注入逐条触发告警，保存 Alertmanager 接收、通知、确认和恢复证据。
- 将仪表盘、SLO、部署版本和 PostgreSQL/Redis 指标纳入同一演练记录。
