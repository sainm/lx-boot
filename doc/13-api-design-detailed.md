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

### 3.3 导入量表

- 方法：`POST`
- 路径：`/api/v1/scales/import`
- 权限：`ASSESSMENT_ADMIN`

请求方式：

- `multipart/form-data`

请求字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `file` | file | Excel/CSV 导入文件 |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `successCount` | int | 导入成功条数 |
| `errorCount` | int | 导入失败条数 |
| `errors` | array | 错误明细 |

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

### 5.4 查看报告详情

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
| `dimensions` | array | 维度结果 |
| `content` | string | 报告内容 |

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

## 9. 统计与导出接口

### 9.1 首页统计看板

- 方法：`GET`
- 路径：`/api/v1/statistics/dashboard`
- 权限：`ASSESSMENT_ADMIN` / `ORG_MANAGER`

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

### 9.3 个人与群体对比

- 方法：`GET`
- 路径：`/api/v1/statistics/compare`
- 权限：`COUNSELOR` / `ASSESSMENT_ADMIN`

### 9.4 导出报告

- 方法：`POST`
- 路径：`/api/v1/exports/reports`
- 权限：`ASSESSMENT_ADMIN` / 授权角色

## 10. 后续建议

后续可以继续补充：

- 字段级请求/响应定义
- 错误码清单
- 审计要求标记
- OpenAPI/Swagger 规范版本
