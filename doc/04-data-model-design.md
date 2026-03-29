# 数据模型设计

## 1. 设计原则

- 用户管理复用 `auth-starter`
- 业务表与认证底座解耦
- 支持量表维度化和可扩展计分规则
- 支持任务分配、自动报告、预警、预约、干预闭环
- 业务表统一采用 `psy_` 前缀命名

## 2. 基础数据复用

基于 `auth-starter` 复用的基础表包括：

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

## 3. 建议新增业务实体

### 3.1 用户业务扩展

- `psy_user_profile`
  - 关联 `sys_user.id`
  - 业务身份类型
  - 学号/工号
  - 学院/班级/部门/岗位
  - 重点关注标记

### 3.2 量表相关

- `psy_scale`
  - 量表主表
  - 建议字段：名称、编码、简介、适用对象、版本、状态、发布标记
- `psy_scale_dimension`
  - 量表维度表
- `psy_scale_question`
  - 题目表
- `psy_scale_option`
  - 选项表
- `psy_scale_scoring_rule`
  - 计分规则表
- `psy_scale_result_rule`
  - 结果解释和风险分级规则表

### 3.3 任务相关

- `psy_assessment_task`
  - 测评任务主表
- `psy_assessment_task_assignment`
  - 任务分配表
  - 支持 `group` / `user` 两类目标

### 3.4 答卷与结果相关

- `psy_assessment_answer_sheet`
  - 答卷主表
- `psy_assessment_answer_item`
  - 题目作答明细
- `psy_assessment_result`
  - 测评结果表
- `psy_assessment_result_dimension`
  - 维度结果表

### 3.5 报告相关

- `psy_report`
  - 统一报告表
  - 通过 `report_type` 区分系统自动报告和咨询师补充报告

### 3.6 预警与干预相关

- `psy_warning_record`
  - 预警记录
- `psy_warning_assignment`
  - 预警责任人分配
- `psy_intervention_record`
  - 干预记录
- `psy_intervention_status_log`
  - 干预状态流转记录

### 3.7 预约与咨询相关

- `psy_counselor_schedule`
  - 咨询师排班
- `psy_appointment_record`
  - 预约记录
- `psy_counseling_record`
  - 咨询记录

### 3.8 审计与字典相关

- `psy_audit_log`
  - 审计日志
- `psy_operation_record`
  - 关键业务操作记录
- `sys_dict_type`
  - 字典类型
- `sys_dict_item`
  - 字典项

### 3.9 通知与消息相关

- `psy_notification`
  - 通知消息主表
- `psy_notification_delivery`
  - 通知投递与阅读状态表

## 4. 核心关系说明

### 4.1 用户与业务扩展

- `psy_user_profile.user_id -> sys_user.id`

### 4.2 量表结构

- `psy_scale_dimension.scale_id -> psy_scale.id`
- `psy_scale_question.scale_id -> psy_scale.id`
- `psy_scale_question.dimension_id -> psy_scale_dimension.id`
- `psy_scale_option.question_id -> psy_scale_question.id`

### 4.3 任务与分配

- `psy_assessment_task_assignment.task_id -> psy_assessment_task.id`
- 分配目标可指向 `sys_group.id` 或 `sys_user.id`

### 4.4 答卷与结果

- `psy_assessment_answer_sheet.task_id -> psy_assessment_task.id`
- `psy_assessment_answer_sheet.scale_id -> psy_scale.id`
- `psy_assessment_answer_sheet.user_id -> sys_user.id`
- `psy_assessment_answer_item.answer_sheet_id -> psy_assessment_answer_sheet.id`
- `psy_assessment_result.answer_sheet_id -> psy_assessment_answer_sheet.id`
- `psy_assessment_result_dimension.result_id -> psy_assessment_result.id`

### 4.5 报告关系

- `psy_report.result_id -> psy_assessment_result.id`
- 一次测评结果可对应多份报告，通过 `report_type` 区分系统报告与咨询师补充报告

### 4.6 预警与干预关系

- `psy_warning_record.result_id -> psy_assessment_result.id`
- `psy_warning_assignment.warning_id -> psy_warning_record.id`
- `psy_intervention_record.warning_id -> psy_warning_record.id`
- `psy_intervention_status_log.intervention_id -> psy_intervention_record.id`

### 4.7 预约与咨询关系

- `psy_appointment_record.user_id -> sys_user.id`
- `psy_appointment_record.counselor_user_id -> sys_user.id`
- `psy_appointment_record.warning_id -> psy_warning_record.id`
- `psy_counseling_record.appointment_id -> psy_appointment_record.id`

### 4.8 通知关系

- `psy_notification_delivery.notification_id -> psy_notification.id`
- `psy_notification_delivery.receiver_user_id -> sys_user.id`

## 5. 关键字段建议

### 5.1 测评任务

- 任务名称
- 量表 ID
- 任务模式
- 实名/匿名标记
- 开始时间
- 截止时间
- 状态

### 5.2 答卷

- 用户 ID
- 任务 ID
- 量表 ID
- 开始时间
- 提交时间
- 作答时长
- 提交状态

### 5.3 测评结果

- 总分
- 风险等级
- 是否预警
- 最近处理状态

### 5.4 预约记录

- 咨询师 ID
- 预约人 ID
- 预约时间段
- 状态
- 来源类型

## 6. 状态与字典建议

建议将以下枚举值抽象为字典或统一枚举管理：

- `risk_level`
- `intervention_state`
- `task_status`
- `appointment_status`
- `answer_status`
- `warning_status`
- `warning_priority`

## 7. 量表计分规则扩展建议

计分规则模型建议支持以下能力：

- 单题权重
- 维度加权
- 反向计分
- 区间分级
- 自定义公式扩展

后续字段设计时可重点考虑：

- 规则类型
- 规则表达式
- 作用范围
- 权重值
- 阈值上下限

## 8. 数据一致性建议

对于 `psy_warning_record` 与 `psy_assessment_result` 的关系，建议明确：

- 测评结果默认不物理删除，优先软删
- 已生成预警的结果不允许直接硬删除
- 风险等级更新时需评估是否同步更新预警状态
- 审计日志不随业务软删而丢失

并发场景下还应考虑：

- 同一答卷只允许成功提交一次
- 评分过程需具备幂等控制
- 预警生成需避免并发重复创建

## 9. 建模补充建议

- 匿名任务的答卷与结果可不直接暴露个人身份
- 风险等级、干预状态、预约状态建议采用字典表或枚举表管理
- 量表规则应尽量可配置，不建议写死在代码中
