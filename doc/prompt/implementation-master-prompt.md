# 高校/企业心理测评与预警系统实现主 Prompt

> 说明：本文件是总纲 Prompt，用于规划和设计输入，不代表仓库当前已经完成了其中全部内容。当前实际实现状态请结合 `doc/process/03-current-progress-dashboard.md` 与 `doc/process/04-baseline-closure.md` 阅读。

## 使用说明

- 本 Prompt 是完整版总纲，适合一次性长会话、项目总览讨论、统一架构理解场景
- 如果你的目标是实际分阶段推进开发，请优先使用 `doc/prompt/01-09` 子 Prompt
- 本 Prompt 与子 Prompt 不是冲突关系：
  - 本 Prompt 负责总览和统一约束
  - 子 Prompt 负责按阶段落地执行
- 使用本 Prompt 后，建议把输出结果作为后续分阶段 Prompt 的统一上下文摘要

## 预期产出物

- 项目整体理解摘要
- 总体实现顺序
- 核心模块划分
- 主链路优先级
- 风险清单
- 下一步代码任务总表

## 最终技术栈与架构结论

- 后端：Kotlin + Spring Boot + Spring Security
- 认证权限底座：`auth-starter`
- 数据库：PostgreSQL
- 数据访问：Spring JDBC + JdbcTemplate / NamedParameterJdbcTemplate
- 缓存：Redis
- 管理端：React + TypeScript
- Android：原生 Kotlin
- iOS：原生 Swift
- 小程序：原生微信小程序
- 当前整体架构：前后端分离
- 当前后端形态：单体应用 + 模块化分层
- 当前工程组织方式：单仓库，多端独立工程，后端单体单工程
- 当前用户端策略：以原生 App + 微信小程序为主，不再以 H5 作为主要用户端
- 当前数据库访问策略：以 JDBC 为主，不默认引入 JPA/Hibernate

```text
你现在是一个资深全栈架构师和开发工程师，请基于当前项目文档，为“高校/企业心理测评与预警系统”进行实现设计与代码落地。

项目文档位置：
- doc/00-document-index.md
- doc/01-project-overview-and-scope.md
- doc/02-role-and-permission-design.md
- doc/03-business-process-design.md
- doc/04-data-model-design.md
- doc/05-technical-architecture-design.md
- doc/06-page-and-module-design.md
- doc/07-data-privacy-and-security.md
- doc/08-acceptance-test-matrix.md
- doc/09-api-design-outline.md
- doc/10-database-table-design.md
- doc/11-database-ddl-draft.sql
- doc/12-database-init-and-seed.sql
- doc/13-api-design-detailed.md
- doc/14-erd-design.md
- doc/15-openapi-draft.yaml

必须遵守的前提：
1. 用户管理、认证、权限、组织能力基于 auth-starter 基础工程扩展实现，不要重复造认证体系。
2. 心理业务表统一采用 psy_ 前缀。
3. 系统采用前后端分离，后端建议 Kotlin + Spring Boot + PostgreSQL，前端管理端使用 React + TypeScript，手机端采用原生开发，Android 使用 Kotlin，iOS 使用 Swift，并支持微信小程序。
4. 系统必须围绕完整业务闭环实现：
   量表配置/导入 -> 测评任务 -> 在线答题 -> 自动评分 -> 系统报告 -> 预警 -> 接单/跟进 -> 咨询预约 -> 咨询记录 -> 复测/结案 -> 群体报告
5. 被测者只能看到自己的任务、报告、预约、通知；管理者只能看到对应管理模块；敏感数据必须受权限与审计控制。
6. 匿名测评默认不进入个体预警和个体干预流程。
7. 所有设计和代码实现都要尽量与现有文档保持一致，如果发现冲突，先指出再给出建议方案。

你的任务目标：
1. 先阅读并总结文档中的核心约束、核心模块、角色权限、主流程。
2. 给出推荐的项目落地顺序，按 MVP 优先。
3. 设计后端模块结构、包结构、核心实体、DTO、Service、Controller、Repository。
4. 基于 DDL 草案检查数据表设计是否足够支撑主流程，如有必要提出小范围修正建议。
5. 按接口文档输出后端接口实现计划。
6. 优先实现主链路功能：
   - 量表管理
   - 测评任务管理
   - 在线答题与提交
   - 自动评分
   - 系统报告生成
   - 预警生成
7. 第二阶段实现：
   - 咨询师补充报告
   - 预警分配与干预记录
   - 咨询预约与咨询记录
   - 群体报告与统计看板
   - 通知提醒
8. 输出时要尽量结构化，包含：
   - 系统理解
   - 模块拆分
   - 表与实体映射
   - 接口实现顺序
   - 风险点
   - 下一步代码任务清单

输出要求：
- 不要只讲概念，要尽量面向实现。
- 优先从可开发、可测试、可迭代的角度输出。
- 如果要生成代码，请从主链路开始，逐步推进。
- 如果发现文档还缺少某些实现细节，请列出“最小必要补充项”，不要泛泛而谈。
```

## 认证会话基线补充

- 所有终端统一采用 `accessToken + refreshToken` 双 token 模型。
- 所有终端统一支持：
  - `POST /auth/login/password`
  - `POST /auth/token/refresh`
  - `POST /auth/logout`
  - `GET /api/v1/auth/me`
- 所有终端统一遵守：
  - 本地记录 token 生命周期
  - access token 接近过期时提前刷新
  - `401 -> refresh -> retry`
  - 会话失效后统一回登录页
- 诊断信息能力保留给管理端和开发调试场景，移动端只做轻量会话状态展示。
- `/auth-audit` 仅管理端 `ORG_MANAGER` / `SYS_ADMIN` 可见，移动端与小程序不实现管理审计页面。
