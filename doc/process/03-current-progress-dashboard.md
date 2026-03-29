# 当前项目进度面板

## 1. 使用说明

本面板用于快速查看当前项目推进状态，建议每完成一个阶段后及时更新。

状态说明：
- `未开始`
- `进行中`
- `已完成`
- `阻塞`

## 2. 总体进度

| 阶段 | 名称 | 状态 | 说明 |
| --- | --- | --- | --- |
| 0 | 总纲理解 | 已完成 | 已完成技术栈、架构、工程组织、主链路和推进顺序统一确认 |
| 1 | 架构与实现规划 | 已完成 | 已确认 MVP 范围、模块边界和 4~6 周推进顺序 |
| 2 | 数据库设计确认 | 已完成 | 已确认 MVP 主链路表结构、索引建议与第二阶段数据边界 |
| 3 | 后端核心主链路 | 已完成 | 已确认后端模块结构、`auth-starter` 集成思路和主链路实现顺序 |
| 4 | 后端扩展能力 | 已完成 | 已确认预约、通知、统计、导出、审计等第二阶段扩展边界 |
| 5 | React 管理端 | 已完成 | 已确认项目结构、路由权限方案、组件库与页面优先级 |
| 6 | Android 原生 App | 已完成 | 已确认工程结构、主链路页面流和弱网恢复策略 |
| 7 | iOS 原生 App | 已完成 | 已确认工程结构、主链路页面流和 iOS 特有合规要求 |
| 8 | 微信小程序 | 已完成 | 已确认轻量用户链路、订阅消息方案和分包策略 |
| 9 | 测试与交付 | 已完成 | 已确认测试分层、联调顺序、CI/CD 建议和发布检查清单 |

## 2.1 Baseline 状态

- 当前状态：`已形成`
- 基线说明文档：[04-Baseline 闭环说明](./04-baseline-closure.md)
- 当前基线包含：认证登录、管理端主链路、报告导出、过程文档

## 3. 当前建议优先推进

当前建议优先推进以下工作：
1. 搭建 `backend/` 后端工程骨架
2. 搭建 `admin-web/` React 管理端工程骨架
3. 按 MVP 顺序实现后端主链路
4. 接入 React 管理端基础壳和主链路页面

## 3.1 当前已完成准备工作

- 已完成需求总文档整理
- 已完成结构化设计文档拆分
- 已完成数据库设计、DDL 草案、种子数据草案
- 已完成接口详细文档与 OpenAPI 初稿
- 已完成 Prompt 分阶段体系
- 已完成 process 推进管理体系
- 已完成阶段 0 至阶段 9 的执行记录

## 4. 当前已确认基础结论

- 架构：前后端分离
- 后端：单体应用 + 模块化分层
- 工程组织：单仓库，多端独立工程，后端单体单工程
- 后端技术栈：`Kotlin + Spring Boot + Spring Security + Spring JDBC + PostgreSQL + Redis`
- 权限底座：`auth-starter`
- 管理端：`React + TypeScript`
- Android：原生 `Kotlin`
- iOS：原生 `Swift`
- 小程序：原生微信小程序

## 5. 当前风险关注点

- `auth-starter` 与心理业务系统的集成边界需要在真实工程中尽快落地验证
- 数据库结构虽然已设计完成，但还未经过代码实现与联调验证
- 多端并行开发前，后端接口和权限边界必须先稳定
- React 管理端、Android、iOS、小程序都依赖统一 API 设计

## 5.1 当前阶段建议动作

- 立即开始后端工程骨架搭建
- 同步开始 React 管理端工程骨架搭建
- 先实现后端量表、任务、答题、评分、报告、预警主链路
- 再进入 React 管理端联调

## 5.2 当前已确认的 MVP 边界

第一阶段 MVP 只覆盖：
- 后端主链路
- React 管理端基础能力

当前不纳入第一阶段主目标：
- Android 原生实现
- iOS 原生实现
- 微信小程序实现
- 深度统计分析
- 完整预约与咨询闭环

## 5.3 当前已确认的数据边界

第一阶段重点使用：
- 量表相关表
- 测评任务相关表
- 答卷与结果相关表
- 报告表
- 预警主表

第二阶段再正式启用：
- 预约相关表
- 通知相关表
- 审计相关表
- 干预状态流转相关表

## 5.4 当前已确认的后端实现边界

第一阶段后端只聚焦：
- 量表模块
- 测评任务模块
- 答卷模块
- 评分与报告模块
- 预警生成模块

当前暂不进入第一阶段代码主目标：
- 预约模块
- 通知模块
- 导出模块
- 审计模块

## 5.5 当前已确认的第二阶段扩展边界

第二阶段重点包括：
- 预警分配与状态流转
- 咨询预约与咨询记录
- 站内消息通知
- 基础统计看板与群体报告
- 导出与审计

## 5.6 当前已确认的管理端边界

当前管理端第一阶段重点：
- 登录与应用壳
- 角色驱动菜单
- 量表管理
- 测评任务管理
- 基础看板

当前不强求第一阶段一次性做完：
- 复杂统计图表
- 深度群体分析
- 全量预约与咨询工作台

## 6. 下一步入口

文档规划阶段已经全部完成，下一步直接进入代码实现阶段：

1. 搭建 `backend/`
2. 搭建 `admin-web/`
3. 先做后端主链路
4. 再做 React 管理端联调
## 2026-03-27 Incremental Update

- 认证与会话基线继续完善：
  - access token / refresh token 生命周期可见
  - 提前刷新与手动刷新会话已接通
  - 会话失效后统一回到登录页
- 管理端新增 `/session` 会话详情页
- 管理端新增 `/auth-audit` 认证审计页
- 认证审计页已支持：
  - 登录日志查看
  - 安全事件查看
  - 基础筛选与分页切换

## 2026-03-28 Incremental Update

- 后端新增业务侧安全审计服务 `SecurityAuditService`
- 已接入的高敏动作：
  - 报告查看
  - 报告导出
  - 预警认领 / 指派
  - 干预创建 / 结案
- 管理端 `认证审计` 页后续可直接查看这些业务安全事件

## 2026-03-28 Auth Audit UI Incremental Update

- 管理端 `认证审计` 页已支持 `parsedDetail` 结构化消费
- 已支持 `PSY_*` 快捷筛选
- 已支持“认证事件 / 业务事件”当前页区分展示
- 已支持从事件详情中直接提取关键摘要字段
## 2026-03-28 Audit Page Update

- `auth-audit` 页面已支持结构化 `detailJson` 展示
- 已支持常用 `PSY_*` 业务事件快捷筛选
- 已支持当前页认证事件 / 业务事件口径区分

## 2026-03-28 Audit Drilldown Update

- `auth-audit` 页面已支持原始事件明细抽屉查看
- 已支持基于当前页数据的 `riskLevel` / `reportType` 细筛
## 2026-03-28 Audit Detail Workflow Tuning

- Clicking a structured detail tag now narrows the audit view to the matching business-event filters.
- Active filter chips stay visible and closable for faster drilldown and reset.
- The detail drawer supports copying both raw JSON and structured detail snapshots.

## 2026-03-28 Next Audit Drilldown Idea

- A good next step is user-focused drilldown.
- If we implement it later, clickable user/event tags or user filter chips will let operators follow the same user's related audit trail more quickly.

## 2026-03-28 Respondent Web Baseline

- A baseline respondent-facing web flow now exists inside the current React app.
- `USER` accounts now land on `/my/tasks` after sign-in.
- The baseline includes task list, questionnaire page, report jump-after-submit, notifications, and session detail access.

## 2026-03-28 Respondent Draft And Landing Update

- The questionnaire page now restores and updates a local browser draft by `taskId`.
- Submitting a questionnaire clears the local task draft.
- The respondent landing page now includes quick summary cards and shortcuts for tasks, reports, notifications, and appointments.

## 2026-03-28 Respondent Report Entry Update

- Backend now exposes `GET /api/v1/reports/my` for respondent-facing report history.
- The React app now includes a `My Reports` page for `USER` accounts.
- Completed-task shortcuts now route into the dedicated report list.

## 2026-03-28 Respondent My Reports Update

- A respondent-facing `My Reports` page now lists the user's own reports.
- The backend now exposes `GET /api/v1/reports/my` for the respondent portal.
- The reports shortcut on the respondent landing page now routes to `/my/reports`.

## 2026-03-28 Respondent Report Detail And Appointment UX Update

- The report detail page now sends `USER` accounts back to `My Reports`.
- The appointment page now has a cleaner respondent-facing view while keeping staff-only counseling actions restricted by role.

## 2026-03-28 Respondent Shortcut And Appointment Flow Update

- Completed tasks now link directly into `My Reports` filtered by `taskId`.
- `My Reports` supports clearing the task filter and shows a task-scoped empty state.
- The respondent appointment form now reuses counselor selection to drive schedule lookup.

## 2026-03-28 Respondent Report Reading Mode Update

- The report detail page now shows a respondent-friendly reading mode for `USER` accounts.
- The user view emphasizes system conclusion, risk level, score snapshot, and next-step guidance before the raw report content.
- Staff loading and export behavior remains unchanged for authorized non-user roles.

## 2026-03-28 Respondent Appointment Success Flow Update

- The respondent appointment page now shows a booking success summary after create.
- The success state includes appointment id, counselor id, selected schedule summary, and optional remark.
- The page now provides direct follow-up shortcuts into notifications and reports after booking.

## 2026-03-28 Notification Return Flow Update

- The notification page now provides workflow shortcuts instead of only read/unread operations.
- Appointment notifications now route back into the appointment flow, and staff-facing warning/intervention items can jump back into warning handling.
- Triggering a linked workflow from an unread notification marks it as read first.

## 2026-03-28 Notification Target Path Update

- Backend notifications now include an explicit `targetPath` for stable deep links.
- Appointment, warning, and intervention notifications now point back to their main workflow pages without relying only on type inference.
- The frontend still keeps fallback routing so older notifications remain usable.

## 2026-03-28 Notification Payload And Focus Update

- Notifications now also include `payloadJson` for business-context deep links.
- Appointment and warning notifications now open with focused query parameters instead of only returning to a broad list page.
- The linked pages now show a focus banner so users know which record the notification referred to.
