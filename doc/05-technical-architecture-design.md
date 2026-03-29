# 技术架构设计

## 1. 架构目标

系统需要支持多角色、多终端和较复杂的业务闭环，因此建议采用前后端分离架构，并复用已有认证授权基础设施。

## 1.1 最终技术栈与架构决策

为避免后续实现摇摆，当前项目技术栈与架构明确如下：

- 后端：Kotlin + Spring Boot + Spring Security
- 认证权限底座：`auth-starter`
- 数据库：PostgreSQL
- 数据访问：Spring JDBC + JdbcTemplate / NamedParameterJdbcTemplate
- 缓存：Redis
- 管理端：React + TypeScript
- Android：原生 Kotlin
- iOS：原生 Swift
- 小程序：原生微信小程序
- 当前部署形态：单体后端 + 独立前端/移动端
- 当前架构模式：前后端分离 + 后端模块化分层

说明：

- 当前阶段不建议拆微服务
- 当前阶段不建议同时维护 H5 与原生 App 双套用户端
- 用户端以原生 App + 微信小程序为主
- 当前阶段不建议默认引入 JPA/Hibernate 作为主数据访问方案
- 数据访问建议保持与 `auth-starter` 一致，优先采用 JDBC 风格

## 2. 整体架构

### 2.0 技术选型建议

- 后端：Kotlin + Spring Boot + Spring Security
- 认证授权底座：`auth-starter`
- 数据库：PostgreSQL
- 数据访问：Spring JDBC + JdbcTemplate / NamedParameterJdbcTemplate
- 前端管理端：React + TypeScript
- 手机 App：原生开发，Android 使用 Kotlin，iOS 使用 Swift
- 微信小程序：原生小程序或单独的小程序端实现
- 缓存：Redis
- 文件存储：本地文件服务或对象存储

数据库访问说明：

- 当前项目推荐使用 JDBC 路线
- 推荐手写 SQL 处理统计、报表、群体分析和复杂查询
- 不建议默认使用 JPA/Hibernate 作为主 ORM 方案

### 2.1 后端

后端负责：

- 对接 `auth-starter`
- 量表管理
- 测评任务管理
- 答卷提交
- 自动评分
- 报告生成
- 预警识别
- 咨询预约
- 干预跟踪
- 统计分析与导出

### 2.2 前端管理端

管理端负责：

- 管理员后台
- 咨询师工作台
- 群体报告与图表
- 预警与预约处理

### 2.3 前端用户端

用户端负责：

- 查看测评任务
- 在线答题
- 查看个人报告
- 查看预约与通知

### 2.4 微信小程序

小程序负责：

- 用户身份接入
- 待办任务查看
- 移动端答题
- 查看个人报告
- 查看预约和提醒

### 2.5 原生手机 App

原生手机 App 负责：

- 用户登录与身份接入
- 查看待办测评任务
- 执行移动端答题
- 查看个人报告
- 查看预约与通知
- 提供更稳定的移动端交互体验

## 3. 权限与认证方案

- 统一使用 `auth-starter` 提供的认证授权能力
- 前端根据角色动态显示菜单和入口
- 后端按角色与权限校验接口访问
- 对敏感数据叠加数据范围限制

## 4. 核心服务划分建议

- 用户与组织服务
- 量表服务
- 测评任务服务
- 评分与报告服务
- 预警与干预服务
- 预约与咨询服务
- 统计与导出服务

说明：

- 当前服务划分优先作为逻辑模块划分
- 课程设计/毕设阶段建议采用单体应用 + 模块化分层实现
- 后续若业务复杂度上升，可再考虑拆分为独立服务

## 4.1 推荐后端模块结构

建议后端按如下逻辑模块划分：

- `auth-integration`
  负责与 `auth-starter` 集成，承接用户、权限、组织上下文
- `scale`
  负责量表、维度、题目、选项、计分规则
- `assessment`
  负责测评任务、任务分配、答卷提交、测评结果
- `report`
  负责系统报告、咨询师补充报告、群体报告
- `warning`
  负责预警生成、预警分配、预警升级、干预状态流转
- `appointment`
  负责咨询师排班、预约、咨询记录
- `notification`
  负责消息通知与投递
- `statistics`
  负责统计看板、群体分析、导出
- `audit`
  负责审计日志与关键业务操作留痕

## 4.2 推荐前端与终端结构

建议终端侧按如下方式拆分：

- `admin-web`
  React 管理端，服务测评管理员、咨询师、学校/企业管理人员、系统管理员
- `android-app`
  Android 原生 App，服务用户端核心链路
- `ios-app`
  iOS 原生 App，服务用户端核心链路
- `wechat-miniapp`
  微信小程序，服务轻量用户端链路

## 5. 部署架构建议

- 当前阶段建议采用单体部署
- 可使用 Docker 进行标准化部署
- 开发环境可采用本地单机部署
- 正式演示环境可采用应用服务 + PostgreSQL + Redis 的基础部署形态

## 6. 终端适配要求

- 管理端优先适配 PC
- 原生手机 App 需支持主要用户操作链路
- 微信小程序需支持轻量化用户操作链路
- 图表在移动端和小程序端可采用简化展示

## 7. 接口设计要求

- 保持统一业务 API
- 管理端与用户端共享核心后端能力
- 对小程序和 H5 提供轻量化返回结构
- 对导出、预约、预警处理等敏感操作增加审计

## 8. 登录接入建议

后续可根据场景支持：

- 账号密码登录
- 手机号登录
- 微信登录或身份绑定
- 与学校/企业统一身份认证集成

## 9. 非功能建议

- 数据脱敏
- 操作审计
- 异常日志
- 接口限流
- 数据备份与恢复

## 10. 非功能指标建议

后续进入实现阶段时，建议明确基础非功能指标，例如：

- 同时在线答题用户数目标
- 常规页面响应时间目标
- 提交答卷到完成预警识别的延迟目标
- 导出任务可接受耗时
- 关键接口可用性目标

可先给出初版约束，例如：

- 普通查询接口响应目标小于 1 秒
- 答卷提交后同步完成自动评分，预警识别可同步完成或在短时异步任务内完成，整体目标小于 3 秒

## 11. 容灾、日志与监控建议

- 数据定期备份
- 关键数据支持异地备份或离线备份
- 重要接口和任务链路配置监控告警
- 关键错误日志集中留存
- 明确基础 SLA 目标

## 12. 接口治理建议

- 制定 API 版本管理策略
- 建立统一错误码规范
- 区分用户端与管理端接口命名和权限
- 对敏感接口增加审计与幂等控制

## 13. 测试策略建议

建议至少覆盖以下测试层次：

- 单元测试
- 集成测试
- 接口测试
- 压力测试
- 安全测试

安全测试建议重点关注：

- XSS
- SQL 注入
- 越权访问
- 文件上传安全
- 微信小程序域名与鉴权配置
## 14. Admin Auth And Audit Addendum

- The admin web restores session state through `GET /api/v1/auth/me`.
- Access tokens are tracked locally with an expiry timestamp and refreshed proactively before expiry.
- The HTTP layer follows the flow `401 -> refresh token -> retry original request`; if refresh fails, the app clears local session state and redirects to `/login`.
- The admin web exposes a session detail page at `/session` to inspect:
  - current session source and health
  - access token / refresh token expiry and remaining time
  - token use and last sync timestamp
- The admin web exposes an auth audit page at `/auth-audit` backed by `auth-starter`:
  - `GET /auth/login-logs`
  - `GET /auth/security-events`
- The auth audit page is limited to `ORG_MANAGER` and `SYS_ADMIN`.

## 15. Mobile Session Model Addendum

- Android、iOS、微信小程序统一复用 `auth-starter` 的登录、刷新、退出和 `/api/v1/auth/me` 契约。
- 移动端统一采用 `accessToken + refreshToken` 双 token 模型。
- 移动端统一遵守以下会话策略：
  - 本地记录 token 生命周期
  - access token 接近过期时提前刷新
  - `401 -> refresh -> retry`
  - 会话失效后回登录页，并尽量保留原始返回路径
- 弱网场景下，token 刷新失败与答题暂存、恢复提交流程需要协同设计。
- 移动端只消费用户身份与用户侧业务接口，不开放管理侧认证审计接口。
- `/auth-audit` 仅管理端组织管理与系统管理角色可见。

## 16. Business Security Audit Addendum

- 业务侧高敏动作继续复用 `auth-starter` 的 `AuditEventPublisher`，不复制认证审计基础设施。
- 当前 backend 已覆盖的业务安全事件包括：
  - `PSY_REPORT_VIEWED`
  - `PSY_REPORT_EXPORTED`
  - `PSY_WARNING_CLAIMED`
  - `PSY_WARNING_ASSIGNED`
  - `PSY_INTERVENTION_CREATED`
  - `PSY_INTERVENTION_CLOSED`
- 业务安全事件与认证审计事件统一落到同一条审计链路，便于后续集中查询和展示。
- 事件细节建议至少包含：
  - 业务主键
  - 风险等级
  - 导出格式或访问路径
  - 当前租户、组织、角色快照
