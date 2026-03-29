# Baseline 闭环说明

## 1. 目标

当前 baseline 的目标是形成一条可以演示、可以继续开发、也可以作为后续迭代基准的最小闭环：

- 认证登录闭环
- 管理端主链路闭环
- 报告导出闭环
- 文档与进度闭环

## 2. 当前已形成的闭环

### 2.1 认证闭环

- 后端复用 `auth-starter` 的真实认证能力
- 前端已接入 `POST /auth/login/password`
- 前端请求自动带 `Authorization: Bearer <accessToken>`
- `401` 时优先尝试 `POST /auth/token/refresh`
- 刷新失败后清理本地会话
- 支持 `POST /auth/logout`
- 支持读取当前用户 `GET /api/v1/auth/me`

对应代码：

- [AuthProfileController.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/auth/api/AuthProfileController.kt)
- [api.ts](/d:/source/lx-boot/admin-web/src/auth/api.ts)
- [session.tsx](/d:/source/lx-boot/admin-web/src/auth/session.tsx)
- [token.ts](/d:/source/lx-boot/admin-web/src/auth/token.ts)
- [http.ts](/d:/source/lx-boot/admin-web/src/services/http.ts)
- [LoginPage.tsx](/d:/source/lx-boot/admin-web/src/pages/LoginPage.tsx)

### 2.2 管理端主链路闭环

当前管理端已具备以下主链能力：

- 量表管理
- 测评任务管理
- 任务分配
- 预警列表
- 报告详情
- 干预记录
- 预约与咨询记录
- 群体报告与统计看板
- 通知消息

### 2.3 导出闭环

- 保留 `POST /api/v1/exports/reports` JSON 导出接口
- 新增 `GET /api/v1/exports/reports/download` 正式下载流
- `TEXT` 支持文本文件下载
- `PDF` 支持真实 PDF 文件生成与下载
- 前端优先走下载流，失败后可回退旧接口

对应代码：

- [ExportController.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/export/api/ExportController.kt)
- [ExportService.kt](/d:/source/lx-boot/backend/src/main/kotlin/org/sainm/psy/export/service/ExportService.kt)
- [api.ts](/d:/source/lx-boot/admin-web/src/features/exports/api.ts)
- [ExportReportDialog.tsx](/d:/source/lx-boot/admin-web/src/components/ExportReportDialog.tsx)

### 2.4 文档闭环

- 需求文档已拆分完成
- 设计文档已拆分完成
- Prompt 体系已拆分完成
- Process 管理文档已形成
- 当前 baseline 已有独立说明文档

## 3. 当前 baseline 的验证结果

已完成验证：

- [backend](/d:/source/lx-boot/backend) `gradle test` 通过
- [admin-web](/d:/source/lx-boot/admin-web) `npm run build` 通过

## 4. 当前 baseline 的边界

当前 baseline 已经可以作为“第一版可继续开发基线”，但还不是最终交付版。

当前仍属于后续迭代项的内容：

- refresh token 失败后的完整重登 UX
- 更严格的会话失效提示与跳转策略
- PDF 内置字体资源兜底
- 更大文件场景下的异步导出 / 文件存储
- Android / iOS / 小程序端代码实现
- 更完整的测试用例与自动化流水线

## 5. 建议作为 baseline 锁定的内容

建议将以下内容视为当前基线，不随意回退：

- 后端技术栈与单体模块化架构
- `auth-starter` 作为统一认证权限底座
- React 管理端的权限与会话模型
- 当前数据模型与核心接口路径
- 当前导出接口双轨方案

## 6. 下一步建议

在当前 baseline 上，优先继续这 3 件事：

1. 完成真实登录后的 UX 收口
2. 补强 PDF 字体与跨环境稳定性
3. 开始按业务优先级补测试与页面细节
## 7. 2026-03-27 Auth Session Update

- Anonymous access is no longer treated as development mode by default.
- Development mode must be explicitly enabled from the login page.
- The admin root layout is now protected by a session gate.
- When the session expires, the app redirects back to `/login` and preserves the original return path.

## 8. 2026-03-27 Auth Audit And Session Operations

- 管理端新增 `认证审计` 页面：`/auth-audit`
- 支持查看 `auth-starter` 提供的登录日志与安全事件
- 支持基础筛选：
  - 登录日志：`principal`、`result`
  - 安全事件：`eventType`
- 支持基础分页切换，当前采用前端 `slice` 风格的上一页/下一页判断
- 管理端新增 `会话详情` 页面：`/session`
- 支持查看 access token / refresh token 生命周期、剩余时间、tokenUse 与最近同步时间
- 支持手动刷新会话
- 支持复制诊断信息，便于联调和排查登录失效问题

## 9. 2026-03-28 Business Security Audit Events

- 心理业务 backend 新增轻量 `SecurityAuditService`
- 统一复用 `auth-starter` 的 `AuditEventPublisher`
- 已接入安全事件的高敏动作包括：
  - 报告查看
  - 报告导出
  - 预警认领
  - 预警指派
  - 干预创建
  - 干预结案
- 这些事件统一进入 `security-events` 查询链路，前端认证审计页可直接消费

## 9. 2026-03-28 Business Security Audit Baseline

- backend 已接入业务侧安全事件发布，继续复用 `auth-starter` 的 `AuditEventPublisher`
- 当前已覆盖的高敏动作：
  - 报告查看
  - 报告导出
  - 预警接单
  - 预警指派
  - 干预创建
  - 干预结案
- 事件统一写入认证审计体系，不额外拆新表
- 新增事件细节包含：
  - `reportType`
  - `riskLevel`
  - `exportFormat`
  - `exportChannel`
  - `counselorUserId`
  - 当前用户角色快照

## 10. 2026-03-28 Auth Audit Page Consumption Update

- 管理端 `认证审计` 页已支持消费结构化 `parsedDetail`
- 已支持 `PSY_*` 快捷筛选
- 已支持按“认证事件 / 业务事件”做当前页区分展示
- 安全事件表格已支持展示结构化摘要，而不只是原始 `detailJson`
## 10. 2026-03-28 Audit Page Consumption Update

- The admin `auth-audit` page now consumes parsed `detailJson` as structured fields.
- The page supports quick filters for common `PSY_*` business events.
- The page distinguishes current-page `AUTH` and `BUSINESS` security events for faster triage.

## 11. 2026-03-28 Audit Page Drilldown Update

- The admin `auth-audit` page now supports raw detail drilldown for each security event.
- The page supports current-page field filters for `riskLevel` and `reportType`.
- The page is ready to extend with additional field filters such as `exportFormat`, `warningId`, and `interventionId`.

## 12. 2026-03-28 Audit Page Operator Workflow Update

- The admin `auth-audit` page now supports clickable structured detail tags that apply business filters directly.
- Active business filters are shown as closable chips for fast drilldown and reset.
- The detail drawer supports copying both raw JSON and structured detail snapshots.

## 13. 2026-03-28 Next Audit Drilldown Idea

- The next useful improvement is user-focused drilldown.
- If implemented, the audit page should support clickable user/event tags or user filter chips so an operator can jump from one event to the same user's related activity faster.

## 14. 2026-03-28 Respondent Web Baseline

- A baseline respondent-facing web flow now exists inside the current React app.
- The flow covers sign-in reuse, `My Tasks`, questionnaire answering, report jump-after-submit, notifications, and session detail.
- User routing now defaults authenticated `USER` accounts to `/my/tasks`.

## 15. 2026-03-28 Respondent Draft And Landing Update

- The questionnaire page now restores a local browser draft by `taskId` and keeps local draft cache updated while the user types.
- Submitting a questionnaire clears the local draft cache for that task.
- The respondent landing page now includes quick summary cards for pending tasks, completed tasks, unread notifications, and appointment/report shortcuts.

## 16. 2026-03-28 Respondent Report Entry Update

- Backend now provides a respondent-facing `GET /api/v1/reports/my` list endpoint.
- The React app now includes a `My Reports` page for `USER` accounts.
- Respondent landing shortcuts now route completed work into the dedicated report list instead of relying only on submit-time redirects.

## 16. 2026-03-28 Respondent My Reports Update

- A respondent-facing `My Reports` page now exists and lists the user's own reports.
- The backend exposes a minimal `GET /api/v1/reports/my` endpoint backed by `answer_sheet -> result -> report` joins.
- The respondent landing page routes the reports shortcut to `/my/reports`.

## 17. 2026-03-28 Respondent Report Detail And Appointment UX Update

- The report detail page now includes a respondent-friendly back action that returns `USER` accounts to `My Reports`.
- The appointment page now presents a cleaner `USER` view for booking review and schedule lookup while keeping staff-only counseling-record actions behind role checks.

## 18. 2026-03-28 Respondent Shortcut And Appointment Flow Update

- Completed tasks now offer a direct shortcut into `My Reports` filtered by the related `taskId`.
- The `My Reports` page supports clearing that task filter and shows a task-scoped empty state when needed.
- The respondent appointment form now auto-links counselor selection to the schedule lookup context and resets schedule choice when the counselor changes.

## 19. 2026-03-28 Respondent Report Reading Mode Update

- The report detail page now renders a respondent-friendly reading mode for `USER` accounts.
- The user view highlights system conclusion, risk level, score snapshot, and next-step guidance before the raw content block.
- Staff behavior remains unchanged: direct report loading and export controls still stay available to authorized non-user roles.

## 20. 2026-03-28 Respondent Appointment Success Flow Update

- The appointment page now shows a respondent-facing success banner after a booking is created.
- The success state includes appointment id, counselor id, selected schedule summary, and optional remark.
- Respondents now get direct next-step shortcuts into notifications and reports after booking.

## 21. 2026-03-28 Notification Return Flow Update

- The notification page now acts as a lightweight return hub instead of a read-only message list.
- Notifications now show action buttons that route users back into appointments, while staff can jump into warning-related workspaces.
- Opening a linked workflow from an unread notification also marks that notification as read first.

## 22. 2026-03-28 Notification Target Path Update

- Backend notifications now persist an explicit `targetPath` instead of relying only on `notificationType` and `bizType`.
- Appointment, warning, and intervention notifications now carry stable workflow return paths such as `/appointments` and `/warnings`.
- The React notification page now prefers `targetPath` when present and falls back to the old type-based routing only for older messages.

## 23. 2026-03-28 Notification Payload And Focus Update

- Backend notifications now persist `payloadJson` so linked workflows can carry business context instead of only a route string.
- Appointment, warning, and intervention notifications now include focused query parameters such as `appointmentId` and `warningId`.
- The appointment page and warning page now show a lightweight focus banner when opened from a notification deep link.
