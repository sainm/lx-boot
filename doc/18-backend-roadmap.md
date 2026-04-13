# 后端能力待办与演进路线图

> 说明：本文档面向当前仓库的真实后端实现状态，整理“后台程序是否还有功能可以做”的结论、优先级与落地建议。  
> 目标不是重新写一份理想架构，而是帮助后续按阶段继续做功能。

## 1. 当前结论

当前后端已经具备以下 baseline：

- 量表管理、量表导入
- 测评任务、答题、自动评分、系统报告
- 预警、干预、预约、通知
- 群体统计、导出、审计、国际化

但距离“更完整的正式业务系统”仍有几类明显增量空间：

- 业务闭环还可以继续自动化
- Android / 小程序还缺专门的后端支撑能力
- 评分与量表能力还可以继续增强
- 生产级稳定性与运维能力仍可继续补强

## 2. 建议优先级

建议按以下优先级推进：

### 2.1 P0：优先补齐

- 任务逾期自动处理
- 预警升级与催办
- 导出任务持久化
- 移动端推送与深链接口

当前进度（2026-04-12）：以上 4 项已完成第一版实现，可进入“规则配置化、真实渠道接入、跨实例恢复”等增强阶段。

### 2.2 P1：建议随后补齐

- 干预闭环自动生成复测任务
- 通知策略中心
- 量表版本管理
- 草稿与提交幂等控制

当前进度（2026-04-12）：以上 4 项已完成第一版实现，可进入“配置化规则、前端管理界面、历史数据治理”等增强阶段。

### 2.3 P2：中期增强

- 复杂题型与复杂评分算法
- 设备会话管理
- 数据归档与脱敏导出
- 运维后台能力

## 3. 业务闭环增强

### 3.1 任务逾期自动处理

目标：

- 定时扫描已到结束时间但仍未提交的任务
- 自动更新任务状态为 `OVERDUE`
- 若任务允许超时自动提交，则按规则自动补提
- 补发通知给用户与管理员

建议落点：

- 模块：`assessment`
- 可能新增：
  - `assessment/service/TaskDeadlineScheduler`
  - `assessment/repository/AssessmentTaskDeadlineRepository`
  - 定时任务配置

建议新增表字段或状态：

- 任务级逾期扫描时间
- 自动处理时间
- 自动提交来源标记

### 3.2 预警升级与催办

当前状态：

- 已完成第一版
- 高风险预警在超过配置阈值仍未响应时，会自动升级为 `P0`，并记录 `escalated_at`、`escalation_count`
- 已指派或处理中的预警在超过配置阈值仍未结案时，会向最近处理人发送 `WARNING_REMINDER` 催办通知，并记录 `last_reminded_at`
- 支持通过 `psy.warning.escalation-scan-delay-ms`、`psy.warning.unclaimed-escalation-hours`、`psy.warning.processing-reminder-hours` 调整扫描周期和阈值
- 当前仍是第一版规则，尚未做数据库规则表、分级接收人策略和完整催办历史流水

目标：

- 高风险预警长时间未领取时自动升级
- 已领取但长时间未处理时自动催办
- 对接通知模块形成提醒闭环

建议落点：

- 模块：`warning`、`notification`
- 可能新增：
  - `warning/service/WarningEscalationService`
  - `warning/service/WarningReminderService`

建议增加能力：

- 升级规则配置
- 超时阈值配置
- 催办记录留痕

### 3.3 干预后自动生成复测任务

当前状态：

- 已完成第一版
- 干预关闭时支持通过 `needRetest` 自动创建 `RETEST` 任务
- 会回写 `retestTaskId`，并下发 `RETEST_TASK_CREATED` 通知
- 当前策略仍是固定规则：立即开始、默认 7 天截止、默认分配给原被测用户

目标：

- 咨询师在干预结论中勾选“需要复测”时
- 系统自动创建复测任务
- 自动通知被测者

建议落点：

- 模块：`intervention`、`assessment`、`notification`

建议实现方式：

- 在干预结案或新增记录时判断 `needRetest`
- 自动创建与原量表或关联量表对应的 `RETEST` 任务

### 3.4 通知策略中心

当前状态：

- 已完成第一版
- 已在 `NotificationDispatchService` 中沉淀任务分配、任务逾期、报告生成、预约创建、预警领取、预警分派、干预创建、干预关闭、复测任务创建等统一通知入口
- 业务服务层已不再直接散落拼装主要通知类型，后续扩展渠道和模板时有统一落点
- 已补充站内信与 PUSH 渠道投递流水，通知记录会保留 `payload_json`、`deep_link` 和投递状态
- 新增 `GET /api/v1/notifications/{id}/deliveries` 和 `POST /api/v1/notifications/{id}/deliveries/retry`，支持运维查看投递明细并将失败/跳过的投递重置为 `PENDING`
- 新增 `NotificationDeliveryWorker` 定时扫描 `PUSH` 渠道的 `PENDING` 投递，先将状态推进为 `PROCESSING`，再根据网关结果更新为 `SENT` 或 `FAILED`
- 当前默认接入 `SimulatedPushDeliveryGateway` 作为占位实现，先完成后台状态流转与失败留痕，后续可替换为 FCM / 厂商 Push 适配器
- 新增 `GET /api/v1/notifications/deliveries/summary`，运维端可直接查看待处理、处理中、失败总量以及按渠道/状态分桶的积压概况
- 目前仍属于“代码级策略中心”，配置化规则和真实渠道投递网关还未继续展开

目标：

- 不同事件使用统一规则决定通知对象、渠道和频率
- 后续方便接 App Push / 短信 / 邮件

建议落点：

- 模块：`notification`

建议新增：

- 通知模板分类
- 通知触发规则
- 失败重试机制
- 渠道枚举与投递记录

## 4. Android 与移动端支撑能力

### 4.1 设备绑定与推送 Token 管理

当前状态：

- 已完成第一版
- 新增 `psy_user_device` 表，用于保存用户设备类型、设备 ID、Push Token、App 版本、活跃状态和最近活跃时间
- 新增 `GET /api/v1/my/notifications/devices`、`POST /api/v1/my/notifications/devices`、`DELETE /api/v1/my/notifications/devices/{deviceId}`，支持移动端登记、查看和停用设备
- 通知创建时会根据活跃设备额外生成 `PUSH` 投递流水，投递状态先标记为 `PENDING`
- 当前尚未对接真实 Android 厂商 Push / FCM，只完成了设备与投递流水基础设施

目标：

- 为 Android App 保存设备标识和推送 token
- 支持按用户、按设备发送通知

建议落点：

- 模块：`notification`、`auth`

建议新增表：

- `psy_user_device`
  - user_id
  - device_type
  - device_id
  - push_token
  - app_version
  - last_active_at

### 4.2 深链与通知跳转上下文

当前状态：

- 已完成第一版
- `psy_notification` 增加 `target_type`、`target_id`、`deep_link`、`payload_json` 字段
- 统一通知入口会把业务类型、业务 ID、目标路径和 payload 写入通知记录，为移动端点击通知后跳转任务、报告、预警等页面提供上下文
- 当前 `deep_link` 暂与 Web/前端 `target_path` 保持一致，后续可扩展为 Android App Scheme / Universal Link

目标：

- 通知点击后直接进入任务、报告、预约页面
- 保留业务主键和来源标记

建议落点：

- 模块：`notification`

建议增强：

- 通知数据结构增加 `target_type`、`target_id`、`deep_link`
- 统一生成用户端跳转参数

### 4.3 草稿版本与幂等提交

当前状态：

- 已完成第一版
- 答卷草稿表已具备 `version_no`，保存草稿和正式提交时会按版本号做乐观并发控制，版本不一致返回 `ANSWER_SHEET_VERSION_CONFLICT`
- 正式提交已支持 `submitToken` 幂等键，重复提交同一 token 时会返回已有结果，避免弱网重试重复生成报告
- 当前进展（2026-04-13）：提交接口同时支持请求头 `Idempotency-Key`，body 未传 `submitToken` 时会自动使用该 header；Web 端提交时也会同步携带该 header，便于移动端/网关统一处理
- 当前进展（2026-04-13）：已新增旧草稿定时清理，默认保留 30 天未更新的 `DRAFT` 答卷；支持通过 `psy.assessment.draft-retention-days` 和 `psy.assessment.draft-cleanup-scan-delay-ms` 调整策略，并已接入 Redis 定时任务锁和调度指标
- 当前进展（2026-04-13）：已新增 `uk_psy_answer_sheet_submit_token_user_task` 局部唯一索引，对已提交且 `user_id`、`submit_token` 非空的答卷按 `(task_id, user_id, submit_token)` 做数据库级幂等约束

目标：

- 移动端弱网或重复点击时不产生脏数据
- 草稿保存、正式提交具备版本控制

建议落点：

- 模块：`assessment`

建议增强：

- 增加端到端并发提交压测/集成测试
- 匿名答卷或跨设备客户端维度的幂等键冲突策略继续细化

### 4.4 设备会话管理

目标：

- 支持查看当前用户登录设备
- 支持踢下线、单设备或多设备策略

建议落点：

- 模块：`auth`

说明：

- 该能力与 `auth-starter` 联动更合适
- 心理业务仓库主要负责对接展示与策略落地

## 5. 量表与评分能力增强

### 5.1 多题型支持

当前现状：

- 当前主链路以单选题为主

建议扩展：

- 多选题
- 矩阵题
- 量表滑杆题
- 文本 + 选项组合题

涉及模块：

- `scale`
- `assessment`
- `report`

### 5.2 更复杂的评分算法

当前现状：

- 已支持 `SIMPLE_SUM / REVERSE_SUM / WEIGHTED_SUM`

建议扩展：

- 常模换算
- T 分 / Z 分
- 年龄段、性别、组织类型分层常模
- 高危题项单独触发预警

涉及模块：

- `assessment/service/ScoreCalculator`
- `scale`
- `warning`

### 5.3 量表版本管理

当前状态：

- 已完成第一版
- `psy_scale` 增加版本族字段 `version_group_id` 和 `current_version_flag`
- 新增 `POST /api/v1/scales/{id}/versions`，可从已有量表复制维度、题目、选项和评分规则，创建新的 `DRAFT` 版本
- 新增 `POST /api/v1/scales/{id}/publish`，可将目标版本发布为当前版本，并自动取消同版本族其他版本的当前版本标记
- 新增 `GET /api/v1/scales/{id}/versions/{targetId}/diff`，可对比同一版本族下两个版本在基本信息、维度、题目、选项和评分规则上的新增、删除、修改差异
- 测评任务创建时会写入 `scale_version_no` 和 `scale_version_group_id`，用于锁定当时绑定的量表版本信息
- 当前仍是“同表版本族”方案，尚未拆分独立量表主表/版本表，也还没有做前端版本管理界面

目标：

- 量表改版后不影响历史任务和历史报告
- 新任务可绑定新版本量表

建议落点：

- 模块：`scale`、`assessment`

建议新增：

- 量表主表与版本表拆分，或补充 `version_no + parent_scale_id`
- 任务与答卷固定绑定版本

## 6. 生产级能力与稳定性

### 6.1 导出任务持久化

当前现状：

- 已完成第一版
- 新增 `psy_export_job` 表，导出任务会持久化 `PENDING / PROCESSING / DONE / FAILED` 状态、文件名、内容类型、文件字节、错误信息和完成时间
- `ExportJobStore` 在 Spring 运行时优先使用 `NamedParameterJdbcTemplate` 写入 DB，单测或无 DB 场景仍保留内存兜底
- 已保留导出结果大小限制和过期任务清理，避免导出文件长期占用存储
- 已补充导出任务原始请求上下文：`report_id`、`result_id`、`export_format`、`locale_tag`
- 新增 `POST /api/v1/exports/reports/jobs/{jobId}/retry`，管理员可将失败导出任务重置为 `PENDING` 并使用原始请求重新执行
- 当前进展（2026-04-13）：已支持 DB 记录元数据 + 文件落本地目录，新增 `file_path`、`file_size`，下载时兼容旧的 DB `file_bytes`；定时清理会同步删除落盘文件
- 当前进展（2026-04-13）：已新增卡在 `PROCESSING` 的导出任务超时恢复，超过 `psy.export.jobs.processing-timeout-minutes` 后会标记为 `FAILED`，便于管理员通过已有 retry 接口重试
- 当前仍未接对象存储，也还没有完整的跨实例任务抢占 / 自动重放调度

建议增强：

- 对象存储适配
- 多实例任务抢占与自动重放调度

建议落点：

- 模块：`export`
- 优先级：P0

### 6.2 Redis 真正承接后台状态

当前现状：

- 技术架构中已引入 Redis 口径
- 当前进展（2026-04-13）：已新增 `SchedulerLockService`，在 Redis 可用时为定时任务提供跨实例抢占锁；Redis 不可用时降级为本实例执行，避免影响本地开发和基础运行
- 当前进展（2026-04-13）：任务逾期扫描、预警升级扫描、通知 PUSH 投递扫描、导出任务清理与超时恢复已接入该锁
- 但仍可进一步承担更多后台状态职责

适合 Redis 承担的内容：

- 导出任务进度缓存
- 通知幂等键
- 短期会话 / 刷新黑名单
- 限流计数器
- 分布式锁（定时任务第一版已接入）
- 定时任务抢占锁

### 6.3 定时任务体系

建议统一建设以下任务：

- 任务逾期扫描（已接入 Redis 定时任务锁）
- 预警升级扫描（已接入 Redis 定时任务锁）
- 通知失败重试（PUSH 投递扫描已接入 Redis 定时任务锁）
- 导出超时清理（已接入 Redis 定时任务锁，并支持 `PROCESSING` 超时恢复）
- 数据归档

建议落点：

- `common` 或各模块各自 scheduler

### 6.4 监控与告警

当前进展（2026-04-13）：

- 已新增 `PsyMetrics`，集中封装 Micrometer 指标埋点，避免业务层直接散落指标代码
- 已记录定时任务运行指标：`psy.scheduler.runs`、`psy.scheduler.duration`，按 `job` 和 `outcome` 区分成功、失败、跳过
- 已记录异步导出任务指标：`psy.export.jobs`、`psy.export.job.file.bytes`，按导出格式和状态分桶
- Actuator 已暴露 `metrics` 端点，便于后续接入监控采集

建议重点监控：

- 答卷提交失败率
- 报告生成失败
- 导出任务堆积
- 通知投递失败
- 高风险预警未处理超时

## 7. 数据与管理能力

### 7.1 运维后台能力

当前状态：

- 已完成第一版通知投递运维能力
- 可查看单条通知的站内信/PUSH 投递流水
- 可对 `FAILED / SKIPPED` 的投递记录执行重试重置，让投递状态回到 `PENDING`
- 已完成第一版导出失败重试能力，可对失败的导出任务按原始请求上下文重新执行
- 已完成第一版报告重新生成能力：`POST /api/v1/reports/{id}/regenerate` 会基于已有结果追加新的系统报告版本，并记录 `PSY_REPORT_REGENERATED` 审计事件
- 已完成第一版手工关闭异常任务能力：`POST /api/v1/tasks/{id}/close` 可将 `DRAFT / IN_PROGRESS / OVERDUE` 任务关闭为 `CLOSED`，并记录关闭人、关闭时间和原因；普通答题者任务列表会排除已关闭任务
- 已完成第一版重新评分能力：`POST /api/v1/results/{resultId}/rescore` 会基于已有答卷答案重新计算当前结果、重写维度分数，并追加新的系统报告版本，同时记录 `PSY_ASSESSMENT_RESULT_RESCORED` 审计事件
- 当前仍未做完整运维后台 UI，重新评分也尚未做历史结果快照和预警状态联动重算

建议新增后台运维操作：

- 重发通知
- 重试导出

### 7.2 数据归档与脱敏导出

当前进展（2026-04-13）：

- 已新增 `DataMaskingService`，集中处理结构化敏感文本脱敏
- 报告导出已支持请求级 `desensitized` 参数，默认开启脱敏；同步 JSON 导出、文件下载、异步导出任务和 retry 会保持同一脱敏策略
- 第一版脱敏覆盖手机号、身份证号、邮箱；姓名等自由文本字段暂不做猜测式脱敏，避免误伤报告正文

建议能力：

- 历史答卷与报告归档
- 统计导出时对姓名、手机号、证件号脱敏（报告导出结构化敏感文本脱敏已完成第一版）
- 提供角色分级导出策略

### 7.3 统计口径中心

当前状态：

- 已完成第一版
- 新增 `StatisticsMetricPolicy`，集中管理统计计算口径
- 完成率统一按百分比输出，保留两位小数，例如 `40 / 50 = 80.00`
- 对比用户分差统一按 `用户总分 - 群体平均分` 计算，并保留四位小数
- 风险分布统一过滤 0 值分桶，避免不同接口展示空分布项
- 当前仍是代码级口径中心，尚未做数据库配置化指标口径或指标元数据管理

目标：

- 避免多个统计接口口径漂移
- 统一任务完成率、风险分布、预警处理率的计算规则

建议落点：

- 模块：`statistics`

## 8. 推荐实施顺序

建议按以下顺序推进：

1. 导出任务持久化
2. 任务逾期自动处理
3. 预警升级与催办
4. 移动端设备绑定与推送 token
5. 深链接口与通知上下文
6. 草稿版本与幂等提交
7. 干预后自动复测任务
8. 量表版本管理
9. 更复杂题型与评分算法
10. 运维后台与数据归档

## 9. 对当前仓库最值得先做的 4 件事

如果只选最值得马上继续做的四件，我建议是：

- `export`：导出任务持久化到 DB / Redis
- `assessment`：任务逾期自动处理
- `warning`：预警升级与催办
- `notification` + `auth`：Android 推送设备绑定与深链接口

## 10. 备注

本文档强调的是“当前后台程序还有哪些功能值得做”，不是说这些能力都必须一次性完成。  
更合适的策略是：

- 先补用户侧闭环和移动端支撑
- 再补后台自动化
- 最后做生产级治理与运维增强
