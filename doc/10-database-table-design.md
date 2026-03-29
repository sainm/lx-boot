# 数据库表结构设计

## 1. 文档目的

本文档在已有数据模型设计基础上，进一步细化心理测评系统的数据库表结构，便于后续进行 ER 图绘制、SQL 建表、后端实体设计与接口实现。

## 2. 设计原则

- 基础认证与权限表复用 `auth-starter`
- 心理业务表统一采用 `psy_` 前缀
- 优先使用逻辑删除或状态控制，不直接物理删除关键业务数据
- 关键状态、风险等级、预约状态等建议结合字典表管理
- 数据访问层建议采用 Spring JDBC 风格，与 `auth-starter` 保持一致

## 2.1 数据访问技术结论

当前项目数据库访问技术建议明确为：

- 使用 JDBC
- 基于 Spring JDBC 封装
- 推荐使用 `JdbcTemplate` 或 `NamedParameterJdbcTemplate`
- SQL 以手写为主
- 不建议默认引入 JPA/Hibernate 作为主数据访问方案

这样更适合本项目中的：

- 复杂统计查询
- 群体分析查询
- 多表关联报表
- 导出场景
- 与 `auth-starter` 的一致性集成

## 3. 基础复用表

以下表由 `auth-starter` 提供，本项目直接复用：

- `sys_user`
- `sys_auth`
- `sys_role`
- `sys_permission`
- `sys_user_role`
- `sys_role_permission`
- `sys_tenant`
- `sys_group`
- `sys_login_log`
- `sys_security_event`

## 4. 业务表结构设计

### 4.1 用户业务扩展表 `psy_user_profile`

用途：

- 存储心理测评系统特有的用户业务资料

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `user_id` | bigint | 关联 `sys_user.id` |
| `user_type` | varchar(32) | 用户类型，如 student、employee、counselor |
| `student_no` | varchar(64) | 学号 |
| `employee_no` | varchar(64) | 工号 |
| `college_name` | varchar(128) | 学院名称 |
| `class_name` | varchar(128) | 班级名称 |
| `department_name` | varchar(128) | 部门名称 |
| `position_name` | varchar(128) | 岗位名称 |
| `focus_flag` | boolean | 是否重点关注对象 |
| `latest_assessment_time` | timestamp | 最近一次测评时间 |
| `latest_risk_level` | varchar(32) | 最近一次风险等级 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.2 量表主表 `psy_scale`

用途：

- 存储量表基础信息

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `scale_code` | varchar(64) | 量表编码 |
| `scale_name` | varchar(255) | 量表名称 |
| `description` | text | 量表简介 |
| `applicable_target` | varchar(128) | 适用对象 |
| `version_no` | varchar(32) | 版本号 |
| `status` | varchar(32) | 草稿、已发布、已停用 |
| `anonymous_supported` | boolean | 是否支持匿名 |
| `report_template` | text | 默认报告模板说明 |
| `created_by` | bigint | 创建人 |
| `created_at` | timestamp | 创建时间 |
| `updated_by` | bigint | 更新人 |
| `updated_at` | timestamp | 更新时间 |

### 4.3 量表维度表 `psy_scale_dimension`

用途：

- 存储量表维度定义

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `scale_id` | bigint | 关联 `psy_scale.id` |
| `dimension_code` | varchar(64) | 维度编码 |
| `dimension_name` | varchar(255) | 维度名称 |
| `description` | text | 维度说明 |
| `sort_no` | int | 排序 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.4 量表题目表 `psy_scale_question`

用途：

- 存储量表题目

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `scale_id` | bigint | 关联 `psy_scale.id` |
| `dimension_id` | bigint | 关联 `psy_scale_dimension.id` |
| `question_no` | int | 题号 |
| `question_title` | text | 题干 |
| `question_type` | varchar(32) | 单选、多选、量表题等 |
| `required_flag` | boolean | 是否必答 |
| `reverse_score_flag` | boolean | 是否反向计分 |
| `weight_value` | numeric(10,2) | 单题权重 |
| `sort_no` | int | 排序 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.5 量表选项表 `psy_scale_option`

用途：

- 存储题目选项及分值

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `question_id` | bigint | 关联 `psy_scale_question.id` |
| `option_code` | varchar(64) | 选项编码 |
| `option_label` | varchar(255) | 选项内容 |
| `score_value` | numeric(10,2) | 选项分值 |
| `sort_no` | int | 排序 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.6 量表计分规则表 `psy_scale_scoring_rule`

用途：

- 存储量表计分逻辑

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `scale_id` | bigint | 关联 `psy_scale.id` |
| `rule_type` | varchar(64) | 规则类型，如 sum、weighted、formula |
| `dimension_id` | bigint | 可为空，表示作用于某维度 |
| `expression` | text | 规则表达式 |
| `weight_value` | numeric(10,2) | 权重值 |
| `enabled_flag` | boolean | 是否启用 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.7 量表结果规则表 `psy_scale_result_rule`

用途：

- 存储风险分级与结果解释规则

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `scale_id` | bigint | 关联 `psy_scale.id` |
| `dimension_id` | bigint | 可为空，表示维度级规则 |
| `risk_level` | varchar(32) | 风险等级 |
| `score_min` | numeric(10,2) | 最小分值 |
| `score_max` | numeric(10,2) | 最大分值 |
| `result_title` | varchar(255) | 结果标题 |
| `result_description` | text | 结果说明 |
| `suggestion_text` | text | 建议内容 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.8 测评任务表 `psy_assessment_task`

用途：

- 存储测评任务主信息

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `task_name` | varchar(255) | 任务名称 |
| `scale_id` | bigint | 关联 `psy_scale.id` |
| `task_mode` | varchar(32) | 普查、复测、随访等 |
| `anonymous_flag` | boolean | 是否匿名 |
| `allow_save_flag` | boolean | 是否允许暂存 |
| `allow_timeout_submit_flag` | boolean | 是否允许超时提交 |
| `allow_retake_flag` | boolean | 是否允许重做 |
| `start_time` | timestamp | 开始时间 |
| `end_time` | timestamp | 截止时间 |
| `status` | varchar(32) | 草稿、进行中、已结束 |
| `created_by` | bigint | 创建人 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.9 测评任务分配表 `psy_assessment_task_assignment`

用途：

- 存储任务分配目标

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `task_id` | bigint | 关联 `psy_assessment_task.id` |
| `target_type` | varchar(32) | group 或 user |
| `target_id` | bigint | 对应 group_id 或 user_id |
| `assigned_by` | bigint | 分配人 |
| `assigned_at` | timestamp | 分配时间 |

### 4.10 答卷主表 `psy_assessment_answer_sheet`

用途：

- 存储一次完整作答记录

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `task_id` | bigint | 关联 `psy_assessment_task.id` |
| `scale_id` | bigint | 关联 `psy_scale.id` |
| `user_id` | bigint | 关联 `sys_user.id` |
| `answer_status` | varchar(32) | 暂存、已提交、已超时 |
| `start_time` | timestamp | 开始时间 |
| `submit_time` | timestamp | 提交时间 |
| `duration_seconds` | int | 作答时长 |
| `anonymous_token` | varchar(128) | 匿名任务场景标识 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.11 答卷明细表 `psy_assessment_answer_item`

用途：

- 存储每题作答内容

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `answer_sheet_id` | bigint | 关联 `psy_assessment_answer_sheet.id` |
| `question_id` | bigint | 关联 `psy_scale_question.id` |
| `option_id` | bigint | 关联 `psy_scale_option.id` |
| `answer_text` | text | 作答文本，必要时使用 |
| `score_value` | numeric(10,2) | 本题得分 |
| `created_at` | timestamp | 创建时间 |

### 4.12 测评结果表 `psy_assessment_result`

用途：

- 存储一次测评结果汇总

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `answer_sheet_id` | bigint | 关联 `psy_assessment_answer_sheet.id` |
| `total_score` | numeric(10,2) | 总分 |
| `risk_level` | varchar(32) | 风险等级 |
| `warning_flag` | boolean | 是否触发预警 |
| `result_summary` | text | 结果摘要 |
| `scored_at` | timestamp | 评分时间 |
| `created_at` | timestamp | 创建时间 |

### 4.13 维度结果表 `psy_assessment_result_dimension`

用途：

- 存储各维度测评结果

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `result_id` | bigint | 关联 `psy_assessment_result.id` |
| `dimension_id` | bigint | 关联 `psy_scale_dimension.id` |
| `dimension_score` | numeric(10,2) | 维度得分 |
| `risk_level` | varchar(32) | 维度风险等级 |
| `summary_text` | text | 维度解释 |

### 4.14 报告表 `psy_report`

用途：

- 统一存储系统自动报告和咨询师补充报告

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `result_id` | bigint | 关联 `psy_assessment_result.id` |
| `report_type` | varchar(32) | system、counselor |
| `author_user_id` | bigint | 生成人或填写人 |
| `report_title` | varchar(255) | 报告标题 |
| `report_content` | text | 报告内容 |
| `version_no` | int | 版本号 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.15 预警记录表 `psy_warning_record`

用途：

- 存储预警主记录

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `result_id` | bigint | 关联 `psy_assessment_result.id` |
| `warning_level` | varchar(32) | 关注、高风险等 |
| `warning_priority` | varchar(32) | 优先级 |
| `warning_reason` | text | 触发原因 |
| `status` | varchar(32) | 待接单、处理中、已结案等 |
| `deadline_time` | timestamp | 应处理时间 |
| `first_response_time` | timestamp | 首次响应时间 |
| `closed_time` | timestamp | 结案时间 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.16 预警分配表 `psy_warning_assignment`

用途：

- 存储预警责任人分配

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `warning_id` | bigint | 关联 `psy_warning_record.id` |
| `assignee_user_id` | bigint | 责任人 |
| `assigned_by` | bigint | 指派人 |
| `assigned_at` | timestamp | 指派时间 |
| `claim_time` | timestamp | 接单时间 |

### 4.17 干预记录表 `psy_intervention_record`

用途：

- 存储预警后的干预主记录

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `warning_id` | bigint | 关联 `psy_warning_record.id` |
| `counselor_user_id` | bigint | 咨询师 |
| `current_status` | varchar(32) | 当前干预状态 |
| `plan_text` | text | 干预计划 |
| `close_summary` | text | 结案说明 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.18 干预状态日志表 `psy_intervention_status_log`

用途：

- 存储状态流转记录

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `intervention_id` | bigint | 关联 `psy_intervention_record.id` |
| `from_status` | varchar(32) | 原状态 |
| `to_status` | varchar(32) | 新状态 |
| `remark` | text | 说明 |
| `changed_by` | bigint | 变更人 |
| `changed_at` | timestamp | 变更时间 |

### 4.19 咨询师排班表 `psy_counselor_schedule`

用途：

- 存储咨询师可预约时间

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `counselor_user_id` | bigint | 咨询师用户 ID |
| `schedule_date` | date | 日期 |
| `start_time` | timestamp | 开始时间 |
| `end_time` | timestamp | 结束时间 |
| `quota_count` | int | 可预约名额 |
| `status` | varchar(32) | 可预约、停约、请假 |
| `created_at` | timestamp | 创建时间 |

### 4.20 预约记录表 `psy_appointment_record`

用途：

- 存储咨询预约信息

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `user_id` | bigint | 预约人 |
| `counselor_user_id` | bigint | 咨询师 |
| `warning_id` | bigint | 可为空，关联预警 |
| `schedule_id` | bigint | 关联排班 |
| `appointment_status` | varchar(32) | 待确认、已预约、已取消、已完成、已失约 |
| `source_type` | varchar(32) | 用户发起、管理员发起、预警推荐 |
| `remark` | text | 备注 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.21 咨询记录表 `psy_counseling_record`

用途：

- 存储咨询执行结果

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `appointment_id` | bigint | 关联 `psy_appointment_record.id` |
| `counselor_user_id` | bigint | 咨询师 |
| `summary_text` | text | 咨询摘要 |
| `suggestion_text` | text | 建议内容 |
| `need_retest_flag` | boolean | 是否建议复测 |
| `need_transfer_flag` | boolean | 是否建议转介 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 4.22 通知表 `psy_notification`

用途：

- 存储系统消息主体

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `notification_type` | varchar(32) | 任务提醒、预警提醒、预约提醒、复测提醒 |
| `title` | varchar(255) | 标题 |
| `content` | text | 内容 |
| `biz_type` | varchar(32) | 关联业务类型 |
| `biz_id` | bigint | 关联业务主键 |
| `created_at` | timestamp | 创建时间 |

### 4.23 通知投递表 `psy_notification_delivery`

用途：

- 存储通知接收与阅读状态

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `notification_id` | bigint | 关联 `psy_notification.id` |
| `receiver_user_id` | bigint | 接收人 |
| `read_flag` | boolean | 是否已读 |
| `read_time` | timestamp | 阅读时间 |
| `delivery_channel` | varchar(32) | 站内、小程序、短信等 |
| `created_at` | timestamp | 创建时间 |

### 4.24 审计日志表 `psy_audit_log`

用途：

- 存储敏感操作审计记录

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `operator_user_id` | bigint | 操作人 |
| `action_type` | varchar(64) | 操作类型 |
| `biz_type` | varchar(64) | 业务类型 |
| `biz_id` | bigint | 业务 ID |
| `action_detail` | text | 操作明细 |
| `ip_address` | varchar(64) | IP 地址 |
| `created_at` | timestamp | 操作时间 |

### 4.25 业务操作记录表 `psy_operation_record`

用途：

- 存储关键业务过程日志

建议字段：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `operator_user_id` | bigint | 操作人 |
| `biz_type` | varchar(64) | 业务类型 |
| `biz_id` | bigint | 业务 ID |
| `operation_type` | varchar(64) | 操作类型 |
| `operation_result` | varchar(32) | 成功/失败 |
| `remark` | text | 备注 |
| `created_at` | timestamp | 创建时间 |

## 5. 索引建议

建议重点建立以下索引：

- `psy_scale.scale_code`
- `psy_assessment_task.scale_id`
- `psy_assessment_task_assignment.task_id`
- `psy_assessment_answer_sheet.task_id`
- `psy_assessment_answer_sheet.user_id`
- `psy_assessment_result.answer_sheet_id`
- `psy_warning_record.result_id`
- `psy_warning_record.status`
- `psy_appointment_record.user_id`
- `psy_appointment_record.counselor_user_id`
- `psy_notification_delivery.receiver_user_id`

## 6. 状态字典建议

建议抽象为字典的状态包括：

- 风险等级 `risk_level`
- 干预状态 `intervention_state`
- 任务状态 `task_status`
- 预约状态 `appointment_status`
- 答卷状态 `answer_status`
- 预警状态 `warning_status`
- 预警优先级 `warning_priority`

## 7. 一致性与约束建议

- 同一答卷应限制为单次成功提交
- 自动评分过程应具备幂等控制
- 预警生成应避免并发重复创建
- 已触发预警的结果不允许直接硬删除
- 审计日志独立保存，不随业务删除丢失

## 8. 后续可继续补充内容

- SQL DDL 建表脚本
- 字段长度与默认值细化
- 唯一索引与联合索引设计
- 逻辑删除字段设计
- ER 图输出
