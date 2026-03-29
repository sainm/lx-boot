# 接口设计纲要

## 1. 文档目的

本文档用于后续正式接口清单设计前的结构化占位，便于将需求文档逐步收敛为可开发接口。

## 2. 接口分组建议

- 认证与用户接口
- 量表管理接口
- 测评任务接口
- 答卷与报告接口
- 预警与干预接口
- 预约与咨询接口
- 统计与导出接口

## 3. 统一规范建议

- API 版本前缀，例如 `/api/v1`
- 统一响应结构
- 统一错误码规范
- 统一分页参数
- 统一审计标记规则

## 4. 后续建议补充内容

- 每个接口的路径
- 请求方法
- 请求参数
- 返回字段
- 权限要求
- 审计要求

## 5. 核心接口骨架

### 5.1 认证与用户接口

- `POST /api/v1/auth/login`：用户登录
- `GET /api/v1/auth/me`：获取当前登录用户信息
- `GET /api/v1/users/profile`：获取当前用户业务资料

### 5.2 量表管理接口

- `GET /api/v1/scales`：查询量表列表
- `POST /api/v1/scales`：创建量表
- `POST /api/v1/scales/import`：导入量表
- `GET /api/v1/scales/{id}`：查看量表详情
- `PUT /api/v1/scales/{id}`：更新量表

### 5.3 测评任务接口

- `GET /api/v1/tasks`：查询任务列表
- `POST /api/v1/tasks`：创建测评任务
- `POST /api/v1/tasks/{id}/assign-groups`：按组分配任务
- `POST /api/v1/tasks/{id}/assign-users`：按个人分配任务
- `GET /api/v1/my/tasks`：查看我的任务

### 5.4 答卷与报告接口

- `GET /api/v1/my/tasks/{taskId}/questions`：获取答题内容
- `POST /api/v1/answer-sheets/save`：暂存答卷
- `POST /api/v1/answer-sheets/submit`：提交答卷
- `GET /api/v1/my/reports`：查看我的报告列表
- `GET /api/v1/reports/{id}`：查看报告详情

### 5.5 预警与干预接口

- `GET /api/v1/warnings`：查询预警列表
- `POST /api/v1/warnings/{id}/claim`：预警接单
- `POST /api/v1/warnings/{id}/assign`：指派责任人
- `POST /api/v1/interventions`：新增干预记录
- `POST /api/v1/interventions/{id}/close`：干预结案

### 5.6 预约与咨询接口

- `GET /api/v1/counselors/{id}/schedules`：查看咨询师可预约时间
- `POST /api/v1/appointments`：创建预约
- `GET /api/v1/appointments/my`：查看我的预约
- `POST /api/v1/counseling-records`：填写咨询记录

### 5.7 通知与消息接口

- `GET /api/v1/my/notifications`：查看我的通知
- `POST /api/v1/my/notifications/{id}/read`：标记已读

### 5.8 统计与导出接口

- `GET /api/v1/statistics/dashboard`：首页统计看板
- `GET /api/v1/statistics/group-reports`：群体报告查询
- `GET /api/v1/statistics/compare`：个人与群体对比
- `POST /api/v1/exports/reports`：导出报告
