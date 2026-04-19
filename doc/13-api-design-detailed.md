# 接口设计详细版

## 1. 文档说明

本文档在接口纲要基础上，补充核心接口的路径、方法、用途、主要请求字段、主要响应字段与权限要求，作为后续后端接口开发的直接参考。

后端实现默认基于：

- Kotlin + Spring Boot
- Spring JDBC
- PostgreSQL
- `auth-starter` 认证与权限集成

## 2. 通用约定

### 2.1 路径前缀

- 统一前缀：`/api/v1`

### 2.2 统一响应结构

```json
{
  "code": "0",
  "message": "OK",
  "data": {}
}
```

### 2.3 统一分页参数

- `page`
- `size`

### 2.4 权限说明

- `USER`：被测者端基础权限
- `COUNSELOR`：咨询师权限
- `ASSESSMENT_ADMIN`：测评管理员权限
- `ORG_MANAGER`：学校/企业管理人员权限
- `SYS_ADMIN`：系统管理员权限

## 3. 量表管理接口

### 3.1 查询量表列表

- 方法：`GET`
- 路径：`/api/v1/scales`
- 权限：`ASSESSMENT_ADMIN`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `scaleName` | string | 量表名称模糊查询 |
| `status` | string | 状态 |
| `page` | int | 页码 |
| `size` | int | 分页大小 |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list[].id` | long | 量表 ID |
| `list[].scaleCode` | string | 量表编码 |
| `list[].scaleName` | string | 量表名称 |
| `list[].versionNo` | string | 版本号 |
| `list[].status` | string | 状态 |

### 3.2 创建量表

- 方法：`POST`
- 路径：`/api/v1/scales`
- 权限：`ASSESSMENT_ADMIN`

请求示例：

```json
{
  "scaleCode": "SCL-STRESS-01",
  "scaleName": "大学生压力测评量表",
  "description": "用于压力测评",
  "applicableTarget": "student",
  "versionNo": "v1",
  "anonymousSupported": false
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 新建量表 ID |
| `status` | string | 草稿状态 |

### 3.3 下载量表导入模板

- 方法：`GET`
- 路径：`/api/v1/scales/import-template`
- 权限：`ASSESSMENT_ADMIN`

响应说明：

- 返回模板文件下载流
- 第一版建议提供 `xlsx` 模板

### 3.4 上传并解析量表导入文件

- 方法：`POST`
- 路径：`/api/v1/scales/imports/parse`
- 权限：`ASSESSMENT_ADMIN`

请求方式：

- `multipart/form-data`

请求字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `file` | file | 导入文件，第一版建议仅支持 `xlsx` |
| `importMode` | string | 导入模式，第一版建议仅支持 `CREATE_ONLY` |
| `draftFlag` | boolean | 是否仅导入为草稿 |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `importId` | long | 导入记录 ID |
| `fileName` | string | 原始文件名 |
| `status` | string | `PARSED` / `PARSE_FAILED` |
| `summary.scaleCode` | string | 量表编码 |
| `summary.scaleName` | string | 量表名称 |
| `summary.dimensionCount` | int | 维度数 |
| `summary.questionCount` | int | 题目数 |
| `summary.optionCount` | int | 选项数 |
| `summary.resultRuleCount` | int | 结果规则数 |
| `errorCount` | int | 错误数 |
| `warningCount` | int | 警告数 |
| `errors` | array | 错误明细 |
| `warnings` | array | 警告明细 |

### 3.5 确认执行量表导入

- 方法：`POST`
- 路径：`/api/v1/scales/imports/{id}/confirm`
- 权限：`ASSESSMENT_ADMIN`

请求示例：

```json
{
  "confirmRemark": "导入 PHQ-9 标准版量表"
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `importId` | long | 导入记录 ID |
| `status` | string | `SUCCESS` / `FAILED` |
| `scaleId` | long | 导入成功后的量表 ID |
| `createdDimensionCount` | int | 新增维度数 |
| `createdQuestionCount` | int | 新增题目数 |
| `createdOptionCount` | int | 新增选项数 |
| `createdResultRuleCount` | int | 新增结果规则数 |

### 3.6 查询单次导入结果

- 方法：`GET`
- 路径：`/api/v1/scales/imports/{id}`
- 权限：`ASSESSMENT_ADMIN`

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 导入记录 ID |
| `fileName` | string | 文件名 |
| `status` | string | 当前状态 |
| `importMode` | string | 导入模式 |
| `draftFlag` | boolean | 是否草稿导入 |
| `operatorUserId` | long | 操作人 |
| `parsedAt` | datetime | 解析时间 |
| `confirmedAt` | datetime | 确认时间 |
| `finishedAt` | datetime | 完成时间 |
| `summary` | object | 解析摘要 |
| `errors` | array | 错误明细 |
| `warnings` | array | 警告明细 |

### 3.7 查询导入历史

- 方法：`GET`
- 路径：`/api/v1/scales/imports`
- 权限：`ASSESSMENT_ADMIN`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | string | 文件名模糊查询 |
| `status` | string | 状态筛选 |
| `page` | int | 页码 |
| `size` | int | 分页大小 |

## 4. 测评任务接口

### 4.1 创建测评任务

- 方法：`POST`
- 路径：`/api/v1/tasks`
- 权限：`ASSESSMENT_ADMIN`

请求示例：

```json
{
  "taskName": "2026 春季新生心理普查",
  "scaleId": 2001,
  "taskMode": "screening",
  "anonymousFlag": false,
  "allowSaveFlag": true,
  "allowTimeoutSubmitFlag": false,
  "allowRetakeFlag": false,
  "startTime": "2026-04-01T00:00:00",
  "endTime": "2026-04-15T23:59:59"
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 任务 ID |
| `status` | string | 当前状态 |

### 4.2 按组分配任务

- 方法：`POST`
- 路径：`/api/v1/tasks/{id}/assign-groups`
- 权限：`ASSESSMENT_ADMIN`

请求示例：

```json
{
  "groupIds": [101, 102, 103]
}
```

### 4.3 按个人分配任务

- 方法：`POST`
- 路径：`/api/v1/tasks/{id}/assign-users`
- 权限：`ASSESSMENT_ADMIN`

请求示例：

```json
{
  "userIds": [10001, 10002]
}
```

### 4.4 查询我的任务

- 方法：`GET`
- 路径：`/api/v1/my/tasks`
- 权限：`USER`

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list[].taskId` | long | 任务 ID |
| `list[].taskName` | string | 任务名称 |
| `list[].scaleName` | string | 量表名称 |
| `list[].endTime` | datetime | 截止时间 |
| `list[].status` | string | 任务状态 |

### 4.5 查看任务详情

- 方法：`GET`
- 路径：`/api/v1/tasks/{id}`
- 权限：`ASSESSMENT_ADMIN`

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 任务 ID |
| `taskName` | string | 任务名称 |
| `scaleId` | long | 量表 ID |
| `scaleName` | string | 量表名称 |
| `taskMode` | string | 任务模式 |
| `anonymousFlag` | boolean | 是否匿名 |
| `allowSaveFlag` | boolean | 是否允许暂存 |
| `allowTimeoutSubmitFlag` | boolean | 是否允许超时提交 |
| `allowRetakeFlag` | boolean | 是否允许重测 |
| `startTime` | datetime | 开始时间 |
| `endTime` | datetime | 结束时间 |
| `status` | string | 任务状态 |
| `assignments` | array | 分配对象列表 |
| `closedAt` | datetime | 关闭时间 |
| `closedBy` | long | 关闭人 |
| `closeReason` | string | 关闭原因 |

### 4.6 手工关闭异常任务

- 方法：`POST`
- 路径：`/api/v1/tasks/{id}/close`
- 权限：`ASSESSMENT_ADMIN`

请求示例：
```json
{
  "reason": "超期未回收，手工关闭并等待重新派发"
}
```

响应说明：
- 返回关闭后的任务详情
- 仅允许关闭 `DRAFT` / `IN_PROGRESS` / `OVERDUE` 状态任务
- 不删除已有答卷、结果、报告等历史数据

## 5. 答卷与报告接口

### 5.1 获取答题内容

- 方法：`GET`
- 路径：`/api/v1/my/tasks/{taskId}/questions`
- 权限：`USER`

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | long | 任务 ID |
| `scaleId` | long | 量表 ID |
| `questions` | array | 题目列表 |

### 5.2 暂存答卷

- 方法：`POST`
- 路径：`/api/v1/answer-sheets/save`
- 权限：`USER`

请求示例：

```json
{
  "taskId": 3001,
  "scaleId": 2001,
  "answers": [
    { "questionId": 2201, "optionId": 2302 },
    { "questionId": 2202, "optionId": 2307 }
  ]
}
```

### 5.3 提交答卷

- 方法：`POST`
- 路径：`/api/v1/answer-sheets/submit`
- 权限：`USER`

请求示例：

```json
{
  "taskId": 3001,
  "scaleId": 2001,
  "answers": [
    { "questionId": 2201, "optionId": 2303 },
    { "questionId": 2202, "optionId": 2307 },
    { "questionId": 2203, "optionId": 2311 }
  ]
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `answerSheetId` | long | 答卷 ID |
| `resultId` | long | 结果 ID |
| `reportId` | long | 系统报告 ID |
| `riskLevel` | string | 风险等级 |

### 5.4 结果重新评分

- 方法：`POST`
- 路径：`/api/v1/results/{resultId}/rescore`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `answerSheetId` | long | 原答卷 ID |
| `resultId` | long | 结果 ID |
| `reportId` | long | 新生成的系统报告 ID |
| `totalScore` | number | 重算后的总分 |
| `riskLevel` | string | 重算后的风险等级 |
| `previousRiskLevel` | string | 重算前风险等级 |

### 5.5 查看报告详情

- 方法：`GET`
- 路径：`/api/v1/reports/{id}`
- 权限：`USER` / `COUNSELOR` / 授权角色

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `reportId` | long | 报告 ID |
| `reportType` | string | system/counselor |
| `totalScore` | number | 总分 |
| `riskLevel` | string | 风险等级 |
| `scoreSource` | string | 分数来源（RAW_SCORE / Z_SCORE / T_SCORE） |
| `standardScore` | number | 标准分（按 scoreSource 输出） |
| `zScore` | number | Z 分 |
| `tScore` | number | T 分 |
| `normCode` | string | 命中的常模编码 |
| `highRiskFlag` | boolean | 是否触发高危题预警 |
| `highRiskRuleCode` | string | 命中的高危规则编码 |
| `dimensions` | array | 维度结果 |
| `content` | string | 报告内容 |

### 5.6 按结果查看报告

- 方法：`GET`
- 路径：`/api/v1/reports/by-result/{resultId}`
- 权限：`USER` / `COUNSELOR` / 授权角色

响应说明：
- 返回该测评结果当前关联的报告详情

### 5.7 查看我的报告

- 方法：`GET`
- 路径：`/api/v1/reports/my`
- 权限：`USER`

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list[].reportId` | long | 报告 ID |
| `list[].resultId` | long | 结果 ID |
| `list[].taskId` | long | 任务 ID |
| `list[].taskName` | string | 任务名称 |
| `list[].scaleName` | string | 量表名称 |
| `list[].reportType` | string | 报告类型 |
| `list[].totalScore` | number | 总分 |
| `list[].riskLevel` | string | 风险等级 |
| `list[].scoreSource` | string | 分数来源 |
| `list[].standardScore` | number | 标准分 |
| `list[].zScore` | number | Z 分 |
| `list[].tScore` | number | T 分 |
| `list[].normCode` | string | 常模编码 |
| `list[].highRiskFlag` | boolean | 是否高危题触发 |
| `list[].createdAt` | datetime | 生成时间 |

### 5.8 重新生成系统报告

- 方法：`POST`
- 路径：`/api/v1/reports/{id}/regenerate`
- 权限：`COUNSELOR` / `ASSESSMENT_ADMIN` / `ORG_MANAGER`

响应说明：
- 基于已有测评结果重新生成一版系统报告
- 不覆盖历史报告，返回新生成的报告详情

## 6. 预警与干预接口

### 6.1 查询预警列表

- 方法：`GET`
- 路径：`/api/v1/warnings`
- 权限：`COUNSELOR` / `ASSESSMENT_ADMIN`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `status` | string | 预警状态 |
| `warningLevel` | string | 预警等级 |
| `page` | int | 页码 |
| `size` | int | 分页大小 |

### 6.2 预警接单

- 方法：`POST`
- 路径：`/api/v1/warnings/{id}/claim`
- 权限：`COUNSELOR`

### 6.3 指派责任人

- 方法：`POST`
- 路径：`/api/v1/warnings/{id}/assign`
- 权限：`ASSESSMENT_ADMIN`

请求示例：

```json
{
  "assigneeUserId": 10020
}
```

### 6.4 新增干预记录

- 方法：`POST`
- 路径：`/api/v1/interventions`
- 权限：`COUNSELOR`

请求示例：

```json
{
  "warningId": 5001,
  "planText": "先进行一次面对面访谈，再视情况安排复测"
}
```

### 6.5 干预结案

- 方法：`POST`
- 路径：`/api/v1/interventions/{id}/close`
- 权限：`COUNSELOR`

请求示例：

```json
{
  "closeSummary": "已完成访谈，建议保持观察，无需继续跟进"
}
```

## 7. 预约与咨询接口

### 7.1 查看咨询师可预约时间

- 方法：`GET`
- 路径：`/api/v1/counselors/{id}/schedules`
- 权限：`USER` / `COUNSELOR` / `ASSESSMENT_ADMIN`

### 7.2 创建预约

- 方法：`POST`
- 路径：`/api/v1/appointments`
- 权限：`USER` / `ASSESSMENT_ADMIN`

请求示例：

```json
{
  "counselorUserId": 10020,
  "scheduleId": 6001,
  "warningId": 5001,
  "remark": "希望尽快安排咨询"
}
```

### 7.3 查看我的预约

- 方法：`GET`
- 路径：`/api/v1/appointments/my`
- 权限：`USER`

### 7.4 填写咨询记录

- 方法：`POST`
- 路径：`/api/v1/counseling-records`
- 权限：`COUNSELOR`

请求示例：

```json
{
  "appointmentId": 7001,
  "summaryText": "完成首次访谈，情绪波动主要来自学业压力",
  "suggestionText": "建议一周后复测，并进行规律作息调整",
  "needRetestFlag": true,
  "needTransferFlag": false
}
```

## 8. 通知与消息接口

### 8.1 查看我的通知

- 方法：`GET`
- 路径：`/api/v1/my/notifications`
- 权限：`USER`

### 8.2 标记通知已读

- 方法：`POST`
- 路径：`/api/v1/my/notifications/{id}/read`
- 权限：`USER`

### 8.3 查看我的设备

- 方法：`GET`
- 路径：`/auth/me/devices`
- 权限：`USER`

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list[].id` | long | 设备记录 ID |
| `list[].deviceType` | string | 设备类型 |
| `list[].deviceId` | string | 设备唯一标识 |
| `list[].pushTokenMasked` | string | 脱敏后的 Push Token |
| `list[].appVersion` | string | App 版本 |
| `list[].activeFlag` | boolean | 是否活跃 |
| `list[].lastActiveAt` | datetime | 最近活跃时间 |

### 8.4 登记我的设备

- 方法：`POST`
- 路径：`/auth/me/devices`
- 权限：`USER`

请求示例：
```json
{
  "deviceType": "ANDROID",
  "deviceId": "android-emulator-001",
  "pushToken": "token-demo",
  "appVersion": "1.0.0"
}
```

### 8.5 停用我的设备

- 方法：`DELETE`
- 路径：`/auth/me/devices/{deviceId}/deactivate`
- 权限：`USER`

响应说明：
- 返回停用后的设备摘要

### 8.6 查看通知投递流水

- 方法：`GET`
- 路径：`/api/v1/notifications/{id}/deliveries`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list[].id` | long | 投递记录 ID |
| `list[].notificationId` | long | 通知 ID |
| `list[].receiverUserId` | long | 接收用户 ID |
| `list[].deliveryChannel` | string | 投递渠道 |
| `list[].deliveryStatus` | string | 投递状态 |
| `list[].readFlag` | boolean | 是否已读 |
| `list[].readTime` | datetime | 已读时间 |
| `list[].deviceId` | long | 关联设备 ID |
| `list[].errorMessage` | string | 失败原因 |

### 8.7 查看通知投递运维摘要

- 方法：`GET`
- 路径：`/api/v1/notifications/deliveries/summary`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalPending` | long | 待处理投递总数 |
| `totalProcessing` | long | 处理中投递总数 |
| `totalFailed` | long | 失败投递总数 |
| `oldestPendingCreatedAt` | datetime | 最早待处理投递创建时间 |
| `buckets` | array | 按渠道和状态聚合的统计桶 |
| `buckets[].deliveryChannel` | string | 投递渠道 |
| `buckets[].deliveryStatus` | string | 投递状态 |
| `buckets[].count` | long | 数量 |

### 8.8 重试失败通知

- 方法：`POST`
- 路径：`/api/v1/notifications/{id}/deliveries/retry`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

请求参数：
| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `deliveryChannel` | string | 可选，只重试指定渠道 |

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `notificationId` | long | 通知 ID |
| `deliveryChannel` | string | 重试渠道 |
| `retriedCount` | int | 重试数量 |

## 9. 统计与导出接口

### 9.1 首页统计看板

- 方法：`GET`
- 路径：`/api/v1/statistics/dashboard`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

响应字段补充（仅列新增项）：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recentWarnings[].scoreSource` | string | 分数来源 |
| `recentWarnings[].standardScore` | number | 标准分 |
| `recentWarnings[].zScore` | number | Z 分 |
| `recentWarnings[].tScore` | number | T 分 |
| `recentWarnings[].normCode` | string | 常模编码 |
| `recentWarnings[].highRiskFlag` | boolean | 是否高危题触发 |
| `recentWarnings[].highRiskRuleCode` | string | 高危规则编码 |
| `recentReports[].scoreSource` | string | 分数来源 |
| `recentReports[].standardScore` | number | 标准分 |
| `recentReports[].zScore` | number | Z 分 |
| `recentReports[].tScore` | number | T 分 |
| `recentReports[].normCode` | string | 常模编码 |
| `recentReports[].highRiskFlag` | boolean | 是否高危题触发 |
| `recentReports[].highRiskRuleCode` | string | 高危规则编码 |

### 9.2 群体报告查询

- 方法：`GET`
- 路径：`/api/v1/statistics/group-reports`
- 权限：`COUNSELOR` / `ASSESSMENT_ADMIN` / `ORG_MANAGER`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | long | 任务 ID |
| `groupId` | long | 组织 ID |
| `scaleId` | long | 量表 ID |

响应字段补充（对比用户结果新增项）：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `list[].compareUserResult.scoreSource` | string | 分数来源 |
| `list[].compareUserResult.standardScore` | number | 标准分 |
| `list[].compareUserResult.zScore` | number | Z 分 |
| `list[].compareUserResult.tScore` | number | T 分 |
| `list[].compareUserResult.normCode` | string | 常模编码 |
| `list[].compareUserResult.highRiskFlag` | boolean | 是否高危题触发 |
| `list[].compareUserResult.highRiskRuleCode` | string | 高危规则编码 |

### 9.3 个人与群体对比

- 方法：`GET`
- 路径：`/api/v1/statistics/compare`
- 权限：`COUNSELOR` / `ASSESSMENT_ADMIN`

### 9.4 导出报告

- 方法：`POST`
- 路径：`/api/v1/exports/reports`
- 权限：`ASSESSMENT_ADMIN` / 授权角色

### 9.5 提交异步导出任务

- 方法：`POST`
- 路径：`/api/v1/exports/reports/jobs`
- 权限：`ASSESSMENT_ADMIN` / 授权角色

请求示例：
```json
{
  "reportId": 9001,
  "exportFormat": "PDF"
}
```

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `jobId` | string | 导出任务 ID |
| `status` | string | 初始状态，通常为 `PENDING` |

### 9.6 查询异步导出任务状态

- 方法：`GET`
- 路径：`/api/v1/exports/reports/jobs/{jobId}`
- 权限：`ASSESSMENT_ADMIN` / 授权角色

响应字段：
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `jobId` | string | 导出任务 ID |
| `status` | string | `PENDING` / `PROCESSING` / `DONE` / `FAILED` |
| `reportId` | long | 原始报告 ID |
| `resultId` | long | 原始结果 ID |
| `exportFormat` | string | 导出格式 |
| `localeTag` | string | 生成语言 |
| `fileName` | string | 生成文件名 |
| `contentType` | string | 文件类型 |
| `error` | string | 失败原因 |
| `createdAt` | datetime | 创建时间 |
| `completedAt` | datetime | 完成时间 |

### 9.7 下载异步导出文件

- 方法：`GET`
- 路径：`/api/v1/exports/reports/jobs/{jobId}/download`
- 权限：`ASSESSMENT_ADMIN` / 授权角色

响应说明：
- 当任务状态为 `DONE` 时返回文件流下载

### 9.8 重试失败导出任务

- 方法：`POST`
- 路径：`/api/v1/exports/reports/jobs/{jobId}/retry`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

响应说明：
- 仅允许重试失败导出任务
- 返回重试后的任务 ID 和当前状态

## 10. 后续建议

后续可以继续补充：

- 字段级请求/响应定义
- 错误码清单
- 审计要求标记
- OpenAPI/Swagger 规范版本
## 当前实现补充：量表版本与常模接口

以下接口已在当前后端实现，作为量表维护链路的一部分：

- `POST /api/v1/scales/{id}/versions`：基于已有量表创建新草稿版本。
- `POST /api/v1/scales/{id}/publish`：发布指定量表版本为当前版本。
- `GET /api/v1/scales/{id}/versions`：查询同一版本组下的版本列表。
- `GET /api/v1/scales/{id}/versions/{targetId}/diff`：对比同一版本组下两个量表版本差异。
- `POST /api/v1/scales/{id}/norms/batch`：批量新增常模。
- `GET /api/v1/scales/{id}/norm-coverage`：查询常模覆盖率。

说明：常模和复杂题型已具备第一版维护能力；高危规则读模型、版本 diff 纳入高危规则、以及矩阵题组模型仍属于后续增强项。
## 当前实现补充：认证与自助注册

以下认证侧能力已经在当前后端实现，并通过 `auth-starter` 统一提供：

- `POST /auth/login/password`：账号密码登录
- `POST /auth/token/refresh`：刷新访问令牌
- `POST /auth/logout`：退出登录
- `POST /auth/register`：自助注册
- `GET /auth/register/options`：查询自助注册开关与注册表单约束

### 自助注册开关

- 配置项：`auth-module.registration.self-service-enabled`
- 默认值：`false`
- 作用：
  - `false` 时，前端登录页不显示“注册账号”入口
  - `false` 时，直接调用 `POST /auth/register` 会返回“当前未开放自助注册”
  - `true` 时，前端显示入口，允许匿名用户完成注册

### 认证接口：查询注册选项

- 方法：`GET`
- 路径：`/auth/register/options`
- 权限：匿名可访问

响应示例：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "selfServiceEnabled": false,
    "passwordMinLength": 8
  }
}
```

响应字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `selfServiceEnabled` | boolean | 当前环境是否开放自助注册 |
| `passwordMinLength` | int | 当前密码最小长度要求 |

### 认证接口：自助注册

- 方法：`POST`
- 路径：`/auth/register`
- 权限：匿名可访问，但是否允许注册受配置开关控制

请求示例：

```json
{
  "username": "student001",
  "password": "ChangeMe123",
  "displayName": "张三",
  "email": "student001@example.com",
  "mobile": "13800138000"
}
```

响应示例：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "userId": 101,
    "username": "student001",
    "defaultRoles": ["USER"]
  }
}
```

行为说明：

- 注册成功后默认创建 `sys_user`
- 默认创建密码认证记录 `sys_auth`
- 默认授予基础角色 `USER`
- 若系统中存在默认租户或默认组织，会按 `auth-starter` 现有注册策略自动挂接
- 当前版本不包含短信验证码、邮箱验证码或人工审核流程
