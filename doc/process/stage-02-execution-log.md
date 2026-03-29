# 阶段 2 执行记录

阶段名称：数据库设计确认
执行日期：2026-03-27
使用 Prompt：`doc/prompt/02-database-design-prompt.md`

输入文档：
- `doc/04-data-model-design.md`
- `doc/10-database-table-design.md`
- `doc/11-database-ddl-draft.sql`
- 

阶段目标：
- 确认数据库结构可支撑业务闭环
- 确认字段、索引与状态设计

本阶段输出：
- 数据库审查结论
- MVP 主链路数据结构确认
- 索引与状态字典建议
- 第二阶段表范围边界

确认结论：
- 当前数据库结构足以支撑 MVP 主链路
- MVP 主链路核心表已明确：
  `psy_scale`
  `psy_scale_dimension`
  `psy_scale_question`
  `psy_scale_option`
  `psy_scale_scoring_rule`
  `psy_scale_result_rule`
  `psy_assessment_task`
  `psy_assessment_task_assignment`
  `psy_assessment_answer_sheet`
  `psy_assessment_answer_item`
  `psy_assessment_result`
  `psy_assessment_result_dimension`
  `psy_report`
  `psy_warning_record`
- 第二阶段扩展表已明确：
  `psy_warning_assignment`
  `psy_intervention_record`
  `psy_intervention_status_log`
  `psy_counselor_schedule`
  `psy_appointment_record`
  `psy_counseling_record`
  `psy_notification`
  `psy_notification_delivery`
  `psy_audit_log`
  `psy_operation_record`
- 当前业务表统一采用 `psy_` 前缀，命名规则已稳定
- 数据访问路线已确认使用 Spring JDBC，不以 JPA/Hibernate 为主

数据库审查结论：

1. 当前表结构已经能覆盖第一阶段 MVP 的完整后端主链路。
2. 量表、任务、答卷、结果、报告、预警这条链路的数据承载已经完整。
3. 第二阶段扩展能力所需的预约、通知、审计表也已预留，不会阻塞后续扩展。
4. 现阶段不需要再新增主表，重点转为代码实现验证。

索引建议：

- 保持当前已设计的核心索引：
  `psy_scale.scale_code`
  `psy_assessment_task.scale_id`
  `psy_assessment_task_assignment.task_id`
  `psy_assessment_answer_sheet.task_id`
  `psy_assessment_answer_sheet.user_id`
  `psy_assessment_result.answer_sheet_id`
  `psy_warning_record.result_id`
  `psy_warning_record.status`
- 当前 MVP 阶段不必引入复杂分区策略
- 待真实数据量上升后，再考虑按任务、时间或组织维度分区

状态字典建议：

- 当前建议优先稳定以下字典：
  `risk_level`
  `task_status`
  `answer_status`
  `warning_status`
  `warning_priority`
- `appointment_status`
  可保留到第二阶段真正进入预约功能时启用

MVP 与第二阶段数据边界：

1. 第一阶段只要求实现主链路所需表和最小必要字段。
2. 第二阶段再正式启用预约、咨询记录、通知、审计、导出相关表。
3. 虽然第二阶段表已经设计，但第一阶段代码不需要全部实现。

发现问题：
- `psy_report` 当前采用统一报告表方案，后续代码实现时要严格通过 `report_type` 区分系统报告与咨询师补充报告
- 第二阶段表虽然已经设计，但当前不应过早进入实现，否则会分散主链路开发资源
- 当前 DDL 仍是草案，真正进入后端实现时还需结合 migration 工具再整理一次

需要回写的文档：
- `doc/process/03-current-progress-dashboard.md`
- `doc/process/01-prompt-driven-task-plan.md`

是否进入下一阶段：
- 是，完成阶段 2 后进入阶段 3

下一阶段准备事项：
- 执行 `doc/prompt/03-backend-core-prompt.md`
- 确认后端包结构、auth-starter 集成方式和主链路实现顺序
