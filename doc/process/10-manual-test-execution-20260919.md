# 手动测试执行记录（AI 自测，2026-09-19）

## 1. 执行边界

| 项目 | 内容 |
| --- | --- |
| 代码基线 | 分支 `feature/redo`，HEAD `f178857` |
| 环境 | 本机 PostgreSQL 18.4（`lx/public`，Flyway V1–V28）；后端 `127.0.0.1:8090`（测试前已运行）；Vite 5173（本次启动）；Codex In-app Browser |
| 执行方式 | API + 数据库断言 + 浏览器真实操作；不是只跑自动化测试 |
| 执行范围 | 冒烟、认证/权限/租户隔离、任务管理、作答提交、评分/报告、预警接单、三语、数据库一致性 |
| 未执行 | 视觉/体验走查、Android 设备、真实 SSO/微信/Push/S3、性能与故障恢复演练、PITR、外部治理验收 |
| 用例依据 | [30-manual-test-procedure.md](../30-manual-test-procedure.md) |

## 2. 已通过项

### 2.1 API 与权限

| 检查 | 结果 |
| --- | --- |
| 8 个账号密码登录（sysadmin/org_manager/assessor/counselor/respondent/campus_*/enterprise 部分） | 全部 200，`code=0` |
| 正向量表/任务/仪表盘/导出配置/预警/报告/我的任务/用户管理 | 全部 200 |
| respondent 访问 scales/tasks/warnings/user-admin | 全部 403 `AUTH_403001` |
| counselor 访问 scales、assessor 访问 user-admin、org_manager 访问 scales、counselor 访问通知策略 | 全部 403 |
| 匿名访问 `/api/v1/reports/my`、`/api/v1/my/tasks` | 401 `AUTH_401002` |
| campus_assessor 读取 DEFAULT 租户量表 5 | 404 `SCALE_NOT_FOUND` |
| campus_counselor 认领 ENTERPRISE_DEMO 预警 2 | 404 `WARNING_NOT_FOUND`，未产生副作用 |

### 2.2 任务 → 作答 → 评分 → 报告（API，任务 5）

- 创建任务 5 `MT-AI-E2E-20260919-135212`，状态 `DRAFT`；分配给 `respondent` 成功。
- 被测者拉取题目后任务转 `IN_PROGRESS`；暂存版本 v2 → CAS 暂存 v3 → 提交 v4。
- 提交结果：`resultId=4`、`reportId=4`、`riskLevel=LOW`、`quality_status=VALID`、`scoring_trace_json` 非空。
- 相同 `submitToken` 重放返回同一组 ID；数据库答卷 1 行、结果 1 行、当前结果 1 行、报告 1 行。

### 2.3 浏览器真实操作

| 操作 | 结果 |
| --- | --- |
| 测评管理员登录 | 登录成功，菜单与 route-config 角色矩阵一致 |
| 仪表盘 | 卡片、趋势、分布、最近预警/报告渲染；数据反映新任务与报告 |
| 语言切换 | 中文/日本語切换生效（含日语报告页面） |
| 量表管理 | 2 个量表与导入历史正确显示 |
| 预警列表与接单 | 预警 3 从“待处理”变为“处理中”；DB 写入 `first_response_time`，策略保持 `MISSING` |
| 被测者首页 | 待完成 0、已生成报告 2、未读通知 3；最近报告可进入详情 |
| 报告详情 | 总分/标准分/维度/建议/非诊断声明渲染；参考范围显示“未配置可验证的参考范围”，解读“待心理专业人员审核” |
| 完整作答 UI（任务 6 `MT-AI-UI-20260919-135404`） | 必答校验、上一题/下一题、提交前确认、返回修改、提交成功、跳转报告全部可用 |
| 作答 UI 结果 | C/D/D → 总分 11 → HIGH；DB：答卷 5 `SUBMITTED/VALID`、结果 5 `HIGH/is_current/trace`、报告 1 份；生成预警 4 `HIGH/P1/PENDING/MISSING` |

## 3. 发现的问题

### F-1（P1，已修复）登录后不按角色跳转

- 复现 A：从全新窗口用 `assessor` 登录 → 落在 `/home`（USER 专用）→ 显示“当前角色无权访问此页面”，需手动“返回首页/去仪表盘”。
- 复现 B：在 `/scales` 退出后改用 `respondent` 登录 → 仍停留在 `/scales` → 同样显示无权访问，需手动“返回首页”。
- 根因：[LoginPage.tsx](../../admin-web/src/pages/LoginPage.tsx) 中 `resolveSafeRedirect(from)` 只校验“以 `/` 开头且不是 `/login`”，缺省固定返回 `/home`，未按登录后角色过滤目标路由。
- 影响：每次登录都可能先落到无权页面，误导用户并增加一次无谓跳转；P0 冒烟体验受损。
- 证据：两次浏览器操作的 URL 与 AX 文本；代码位置 `LoginPage.tsx:30-35`、`117-129`。

### F-2（P2，已修复）未登录访问 `/login` 出现 3 条会话错误提示

- 复现：新开标签直接访问 `http://127.0.0.1:5173/login`。
- 现象：页面同时显示 “Authentication is required.”、“Your session could not be refreshed.”、“Your session has expired.”；登录表单仍可正常使用。
- 根因：过期/失效令牌会同时触发会话恢复刷新、定时刷新和 401 拦截器三条路径，各自弹出不同 key 的提示；恢复与定时刷新还会并发请求 refresh。
- 修复：新增 `admin-web/src/auth/sessionFeedback.ts`——登录页抑制认证失败 toast（页面内联提示保留），其他页面统一使用 `auth-required` key 去重；`refreshAuthTokenOnce()` 合并会话恢复、定时刷新与 401 拦截器的 refresh 请求，避免轮换令牌互相失效。
- 证据：首次打开登录页的 AX 文本。

### F-3（P2，已修复）切换语言后后端本地化数据不刷新

- 复现：在仪表盘把语言从 English 切到中文。
- 现象：菜单/标题立即中文，但 6 个指标卡仍为 “Total Scales / Total Tasks / Submitted Sheets / Completion Rate / High-Risk Warnings / Pending Warnings”；刷新页面后全部变为中文。
- 根因方向：指标卡文案由后端按 `Accept-Language` 返回，前端语言切换后未使相关 React Query 缓存失效/重新请求。
- 证据：切换前后与刷新后的 AX 文本；后端 `messages_zh_CN.properties` 已有对应译文。

### F-4（P2，已修复）必答校验产生未处理 Promise 拒绝

- 复现：作答页不选答案直接点“下一题”。
- 现象：UI 正确显示“请回答此题”，但控制台出现 error `Object`（来源 `TaskQuestionPage.tsx`）。
- 根因：[TaskQuestionPage.tsx](../../admin-web/src/pages/TaskQuestionPage.tsx) 的 `handleNext` 直接 `await form.validateFields(...)`，按钮使用 `onClick={() => void handleNext()}`，校验失败时 rejection 未被捕获。
- 影响：功能可用，但破坏“控制台零错误”的验收口径。

### F-5（P2，已修复）AntD 静态 message 警告

- 复现：预警列表接单。
- 现象：控制台 error `Warning: [antd: message] Static function can not consume context like dynamic theme. Please use 'App' component instead.`
- 影响：与 F-4 一起导致页面控制台非零错误。

### F-6（口径待确认）LOW 风险也会生成预警

- 现象：任务 5 提交 LOW（总分 3）后生成 `psy_warning_record` 3 条：级别 `LOW`、优先级 `P3`、状态 `PENDING`、`policy_resolution_status=MISSING`。
- 代码依据：`AnswerSheetService` 在 `riskLevel != NORMAL` 或命中高风险规则时设置 `warningFlag=true`，并调用 `createWarningForSubmission`；因此任何非 NORMAL 结果都会进入预警队列。
- 需确认：产品语义是“所有非正常结果进入跟进队列”，还是“仅高风险/命中高风险规则生成预警”。若前者，需同步手顺与看板文案；若后者，属于逻辑缺陷。

### F-7（P1，文档与实现不一致）健康检查需要认证

- 现象：匿名 `GET http://127.0.0.1:8090/actuator/health` 返回 401 `AUTH_401002`，不是手顺 MT-SMK-001 写的直接返回 `UP`。
- 影响：若生产探针/负载均衡无凭据，健康检查不可用；需确认设计意图，或把手顺改为带认证抓取。

### F-8（P2，已修复文档）默认租户种子预警

- 现象：`assessor`（DEFAULT 租户）在测试前没有任何种子预警；种子预警位于 CAMPUS_DEMO 与 ENTERPRISE_DEMO。
- 影响：手顺 MT-SMK-008 用管理员查看预警会在 DEFAULT 租户失败。
- 处理：手顺 MT-SMK-008 已改为使用 `campus_counselor`/`enterprise_counselor`，MT-SMK-001 也已同步健康检查需要认证的实际行为。

### F-9（P2，新发现）SCHOOL_LEADER 角色没有可访问路由

- 现象：`SCHOOL_LEADER` 出现在角色类型、下拉选项和 `pickPrimaryRole` 优先级中，但 `route-config.tsx` 没有任何路由包含该角色。
- 影响：仅持 `SCHOOL_LEADER` 的账号登录后必然落在无权页面；同时持 `COUNSELOR`/`ASSESSMENT_ADMIN` 的账号会因为 SCHOOL_LEADER 优先级更高而被判为主角色，导致本可访问的页面被拒。
- 状态：本次未修改授权矩阵。需要产品确认校领导允许访问的页面（如仪表盘、群体报告）后，再同步补前端路由角色与后端接口权限。

## 4. 本次产生的测试数据

测试在本地 `lx/public` 留下了带 `MT-AI-` 前缀的数据，未删除：

- 任务 5 `MT-AI-E2E-20260919-135212`、任务 6 `MT-AI-UI-20260919-135404`。
- 答卷/结果/报告 4、5；预警 3（LOW/P3，已接单为 PROCESSING）、预警 4（HIGH/P1，PENDING）。
- 相应通知若干；DEFAULT 租户仪表盘因此显示 1 条待处理预警。

如需清理，应在单独确认后按 `MT-AI-` 前缀精确删除，不得使用无范围删除语句。

## 5. 结论

- 主链路（登录、权限、任务分配、作答、评分、报告、预警接单、三语、数据库一致性）技术可用。
- F-1、F-3、F-4、F-5 已在本记录第 6 节完成修复与回归。
- F-2 会话提示风暴已修复；F-8 的手顺说明已修正。
- 交付前仍需决策：F-7（健康检查口径）、F-6（LOW 是否进入预警队列）、F-9（SCHOOL_LEADER 可访问范围）。
- Android、真实外部渠道、视觉走查、性能与恢复演练仍未执行，不得视为通过。

## 6. 修复与回归记录（2026-09-19）

### 6.1 修复内容

| 问题 | 修改 | 说明 |
| --- | --- | --- |
| F-1 | 新增 `admin-web/src/app/route-access.ts`，`LoginPage` 改为在会话 profile（角色）加载后按 `appRoutes` 角色矩阵解析跳转 | 不再默认写死 `/home`；无权来源路径回退到角色默认页；新增 10 条单测覆盖静态/动态/非法路径 |
| F-3 | `I18nProvider` 在语言切换时调用 `queryClient.invalidateQueries()`；`QueryClientProvider` 上移到 `I18nProvider` 外层 | 后端本地化的指标卡等数据会用新的 `Accept-Language` 重新请求 |
| F-4 | `TaskQuestionPage.handleNext` 捕获 `form.validateFields` 的校验拒绝；`handleSave` 捕获 mutation 拒绝 | 保留“请回答此题”的内联校验提示，不再产生未处理 Promise 拒绝 |
| F-5 | `WarningListPage` 改用 `App.useApp()` 的 `message` | 去掉 AntD 静态 message 的 context 警告 |
| F-2 | 新增 `sessionFeedback.ts`；会话恢复/定时刷新/401 拦截器统一走 `showAuthIssueToast`；新增 `refreshAuthTokenOnce` 合并并发 refresh | 登录页不再堆叠认证失败 toast；其他页面只保留一条可替换提示；并发 refresh 只发一次请求 |

### 6.2 自动化验证

```text
cd admin-web
npm test -- --run   -> 18 test files / 128 tests passed
npm run build       -> tsc -b + vite build 成功
git diff --check    -> 通过
```

### 6.3 浏览器回归

| 场景 | 结果 |
| --- | --- |
| 全新登录页用 `assessor` 登录 | 直接进入 `/dashboard`，不再落到 USER 专用的 `/home` |
| 在 `/scales` 退出后改用 `respondent` 登录 | 直接进入 `/home`，不再停留在 `/scales` |
| 仪表盘从中文切到 English | 指标卡同会话内变为 `Total Scales / Total Tasks / ...`，无需刷新 |
| 任务 7 未作答点击“下一题” | 显示“请回答此题”；控制台 error/warn 计数保持 0 |
| 接单预警 4（HIGH） | 状态变为“处理中”；控制台 error/warn 计数保持 0 |
| 干净退出登录 | 只显示“你已退出登录”，不再出现多条会话失败 warning |
| 退出后重新登录 | 直接进入 `/dashboard`；控制台无新增 error/warn |

### 6.4 回归过程中确认的非缺陷行为

- 预警 4 在创建约 40 秒后被后台扫描升级为 `P0`、`escalation_count=1`。原因是 `findHighRiskWarningsNeedingEscalation` 将 `policy_resolution_status='MISSING'` 作为立即升级条件，属于“缺少已审批策略必须升级”的设计行为，不是优先级显示错误。
- 开发服务器在编辑 `session.tsx` 时发生 Vite HMR 的 context 模块重复，控制台一度出现 `useSession must be used within SessionProvider` 与 `createRoot` 重复调用；整页刷新后消失，干净登录无新增错误。这是开发环境热更新产物，不是生产运行缺陷。

## 7. 手工浏览器执行（2026-09-19，继续）

### 7.1 已实跑的用例

| 模块 | 实际执行 | 结果 |
| --- | --- | --- |
| SMK | MT-SMK-001 ~ 010 | 通过（001：匿名 401、带令牌 `UP`；其余为界面逐页验证） |
| ANS | MT-ANS-001 ~ 006、008 | 通过：问卷加载、必答校验、暂存、双标签页版本冲突、提交前确认、提交成功、同 token 重放、完成后禁止重入 |
| RPT | MT-RPT-001、003、004 | 通过：提交自动生成报告、报告总分/维度/非诊断声明、高风险用户提示 |
| NOTI | MT-NOTI-001 ~ 004 | 通过：通知列表、仅看未读、标记已读（未读 6→5）、跳转预约页 |
| APPT | MT-APPT-001、002、004、005 | 通过：排班查询（3 条）、预约创建（#6 confirmed）、我的预约列表、取消预约（#6→CANCELLED） |
| HOME | MT-HOME-003、004、005 | 通过：资料展示、显示名保存、非法邮箱校验 |
| I18N | MT-I18N-001、002（浏览器）+ 003、007、008（API/静态） | 通过：中文/日语界面切换、后端 `Accept-Language`、494×3 消息键一致 |
| TASK | MT-TASK-001、002、003 | 通过：UI 创建任务、编辑草稿、按个人分配（user 6 分配成功并生成通知） |
| TASK | MT-TASK-005 | API 关闭成功（`CLOSED`），但 UI 无关闭入口，见 F-15 |
| AUTH/User/SEC | 80 条 API 批（49 PASS / 6 FAIL / 3 BLOCKED） | 见 7.2；失败均为真实缺陷或环境阻塞 |

### 7.2 新增缺陷

#### F-10（P0）未配置微信时任意 code 可换取登录令牌

- `auth-module.social.wechat.enabled=false` 时，自动配置注册的是 `MockWechatSocialAuthProvider`；`POST /auth/social/wechat` 只要 `authCode` 非空就返回 200 和 access/refresh token。
- 实测：`{"authCode":"mt-invalid-code"}` → HTTP 200 `code=0` 并签发令牌。
- 对照：同批 Google 无效 code 返回 400，说明微信 Mock Provider 是单独的认证绕过面。
- 证据：`AuthModuleAutoConfiguration.wechatSocialAuthProvider`、`MockWechatSocialAuthProvider.resolve`、API 响应。

#### F-11（P1）禁用账号仍能登录

- `updateStatus(enabled=false)` 后 `sys_user.status=0`（DISABLED），但 `PasswordAuthenticationHandler` 只拒绝 LOCKED/PENDING_EMAIL/PENDING_APPROVAL/REJECTED，没有拒绝 DISABLED。
- 实测：禁用测试用户后仍可用原密码登录成功（MT-USER-005 FAIL）。
- 证据：`PasswordAuthenticationHandler.kt` 状态分支 + 实测响应 + `sys_user.status=0`。

#### F-12（P1）任务时间在 UI 保存时按时区偏移 -8 小时，且每次保存叠加

- 新建任务输入开始 `2026-09-19 13:00:00`、截止 `2026-09-26 23:59:59`，保存后数据库为 `2026-09-19 05:00:00`、`2026-09-26 15:59:59`。
- 不做任何时间修改直接再次编辑保存，数据库进一步变为 `2026-09-18 21:00:00`、`2026-09-26 07:59:59`。
- 结论：前端提交 UTC 时间、后端按本地 `LocalDateTime` 落库，导致每次保存偏移一个时区（Asia/Shanghai -8h）。
- 证据：任务 8 的两次数据库快照 + 列表显示。

#### F-13（P1）认证审计接口缺少授权

- `respondent` 直接访问 `GET /auth/login-logs`、`GET /auth/security-events` 均返回 200；这两个接口没有 `@PreAuthorize`。
- 前端菜单只对 ORG_MANAGER/SYS_ADMIN 展示“认证审计”，但服务端未拦截其他已登录角色。
- 证据：MT-SEC-003 FAIL 记录。

#### F-14（P2）未知 API 路径返回 500 而非 404

- `GET /api/v1/does-not-exist` 返回 `500 INTERNAL_ERROR/服务器异常`，统一错误信封字段存在但没有 404 语义。
- 证据：MT-SEC-011 FAIL 记录。

#### F-15（P2）任务关闭无 UI 入口

- 任务列表对 DRAFT 只显示 编辑/删除/分配任务，对 IN_PROGRESS 只显示 分配任务；没有“关闭任务”按钮。
- API `POST /api/v1/tasks/8/close` 实测 200 且状态 `CLOSED`，说明缺口在前端入口。

#### F-16（P2）唯一键冲突返回 500

- 创建用户时重复 `mobile` / `email` 命中 `uk_sys_user_mobile` / `uk_sys_user_email` 后返回 `500 INTERNAL_ERROR`，而不是可理解的 409/业务码。
- 该现象在修正测试脚本的唯一手机号后不再复现，但错误映射缺口真实存在。

### 7.3 本轮环境阻塞

- MT-AUTH-019 自助注册关闭场景：当前配置为开启，关闭需要重启后端。
- MT-AUTH-030 扫码登录：`auth-module.qr-login.enabled=false`。
- MT-SEC-009 无租户全局管理员：种子管理员均绑定租户，构造 tenantless SYS_ADMIN 需要额外部署配置。
- Android：按用户指示本次不做检查，MT-AND-001 ~ 006 移出本轮执行范围，不计入通过率。

### 7.4 量表管理手工执行（2026-09-19 继续）

已执行并核对：

| 用例 | 结果 |
| --- | --- |
| MT-SCALE-001 | 搜索可用，但大小写敏感：`Stress` 能查到，`STRESS` 查不到，且不按量表编码搜索，见 F-17 |
| MT-SCALE-002 | 通过：UI 创建草稿 `MT_SCALE_UI_1417`，列表与 DB 一致 |
| MT-SCALE-004 | 通过：批量添加 D1/D2/D3，维度表与常模覆盖 0/3 状态正确 |
| MT-SCALE-005 | 通过：添加单选题 + A/B/C/D 选项及 1–4 分，题型表显示权重与维度 |
| MT-SCALE-007 | 部分通过：UI 滑杆题配置字段出现，提交被前端 400 阻断（F-18）；同一 0/10/1 payload 直接调 API 成功创建 question 102，详情页正确显示“滑杆最小值/最大值/步长: 0 / 10 / 1” |
| MT-SCALE-020 | 后端正确阻断：`SCALE_OVERALL_RULE_REQUIRED`；但前端未展示该业务码，按钮一直 loading（F-18） |

#### F-17（P3）量表搜索大小写敏感且不检索编码

- 首页搜索框提示“按量表名称搜索”；输入 `Stress` 命中 `Stress Screening Demo Scale`，输入 `STRESS` 无结果，尽管编码为 `STRESS_DEMO`。
- 影响：测试/运维按编码检索量表不可用，大小写不符合直觉。

#### F-18（P1）量表页 400 错误无用户可见反馈且按钮卡死

- 复现 A：批量添加滑杆题（0/10/1，dimension 22）→ 浏览器控制台 `AxiosError: Request failed with status code 400`；“确定”一直 loading，题目未保存；同样 payload 直接调用 `POST /api/v1/scales/6/questions/batch` 返回 200 并创建 question 102。
- 复现 B：对不完整量表点击“发布为当前版本”→ 后端返回 `400 SCALE_OVERALL_RULE_REQUIRED`（“发布量表前至少需要配置一条总分结果规则。”），页面无任何错误提示，按钮持续 loading，控制台只有 AxiosError。
- 结论：后端业务校验正确，缺陷在前端——批量题目表单组装的请求与后端契约不一致，且 400 分支未把业务码/消息反馈给用户、未复位 loading。

### 7.5 量表导入与发布就绪手工执行（2026-09-19 继续）

| 用例 | 结果 |
| --- | --- |
| MT-IMP-001 | 通过：`GET /api/v1/scales/import-template` 返回 200、xlsx 内容类型、7335 字节；界面有下载模板 |
| MT-IMP-002 | 通过：上传 `mt-import-valid.xlsx` 后解析出摘要（1 维度/2 题目/3 选项/2 规则）且“未发现问题” |
| MT-IMP-003 | 通过：确认导入成功，生成草稿 `MT_IMPORT_VALID`（scale id 7），DB 与详情页一致 |
| MT-IMP-004 | 通过：`scale-import-sample.xlsx` 解析出 11 条错误（重复题号、缺少 slider 范围、缺失维度、重复选项、重复常模），确认导入被禁用 |
| MT-IMP-005 | 通过：导入历史显示 SUCCESS/PARSE_FAILED、错误数、生成量表 ID、创建时间与详情入口 |
| MT-IMP-010 | **失败**：导入后 `psy_scale_governance` 无对应行，`GET /scales/7/package` 的 `governance=null`；发布就绪显示 `GOVERNANCE_MISSING`，见 F-19 |
| MT-PUB-002 | 通过：发布就绪页显示内容摘要、发布指纹、专业/业务审批待处理、37 个阻塞项、发布按钮禁用 |
| MT-PUB-003 | 通过：创建 `MT_PUB_NORMAL` 修订 1（缺失必答 → 运行失败 `MISSING_REQUIRED_ANSWER`）；修订 2 补齐答案后运行通过，差异 `[]`，实际结果含总分 2/HIGH 与完整评分轨迹 |

#### F-19（P2）Excel 导入成功但不创建治理记录

- 现象：合法 Excel 导入后 `psy_scale_governance` 没有任何行（全库 0 行），包接口返回 `governance=null`；发布就绪以 `GOVERNANCE_MISSING` 阻塞。
- 风险：无法在治理页看到“导入时强制 PENDING_REVIEW/DRAFT”的状态证据；虽然发布仍被阻断，但与“导入必须强制写待审核/草稿”的规格不一致。
- 证据：`psy_scale_import_job` 记录 SUCCESS + created_scale_id=7；`psy_scale_governance` count=0；`GET /api/v1/scales/7/package` governance=null；就绪页 `GOVERNANCE_MISSING`。

### 7.6 发布审批手工执行（2026-09-19 继续）

| 用例 | 结果 |
| --- | --- |
| MT-PUB-004 | 通过：以 `counselor`（user 5，量表创建人为 user 4）点击“审批样例”，Golden Case 修订 2 状态变为“已审批”，阻塞项减少 1；构建人自审入口不存在 |
| MT-PUB-005 | 受证据阻塞：专业审核弹窗可填写决定/资质/证据/范围，但后端用合法 `reviewToken` 返回 `400 SCALE_PUBLICATION_EVIDENCE_INCOMPLETE`（治理与验证证据未完成，禁止批准），属正确的 fail-closed；前端未展示该错误，见 F-20 |
| MT-PUB-006 | 未执行：在上述阻塞清零前发布按钮保持禁用（已实测禁用状态） |

#### F-20（P1）发布审批页错误被静默吞掉

- 专业审核提交后弹窗保持打开、无错误提示、无控制台错误、数据库无新审批行。
- 用相同字段 + 合法 UUID `reviewToken` 直接调用 `POST /scales/7/publication/reviews/PROFESSIONAL`，后端返回 `400 SCALE_PUBLICATION_EVIDENCE_INCOMPLETE`（“批准发布前必须完成量表治理和验证证据。”）。
- 根因：`ScalePublicationPage` 的 `reviewMutation`/`publishMutation` 没有 `onError`，React Query 会吞掉失败；与 F-18 同类（400 业务码不反馈给用户）。

#### F-21（P3）缺少必填请求字段返回 500 而非 400

- 调用专业审核接口时漏传必填 `reviewToken`，返回 `500 INTERNAL_ERROR/服务器异常`，而不是 400 参数校验错误；说明请求体反序列化异常被统一映射为 500。

### 7.7 统计与群体报告手工执行（2026-09-19 继续）

| 用例 | 结果 |
| --- | --- |
| MT-STAT-001 ~ 003 | 通过：仪表盘卡片、7 天趋势、任务状态/风险分布均渲染，任务关闭后状态分布同步（已关闭 20% / 进行中 80%） |
| MT-STAT-004 | 通过：默认群体报告查询返回任务 × 群组行（12 人、提交 1、完成率 8.33%、均分 3.00、高风险 0、预警 0） |
| MT-STAT-006 | 通过：对比对象 user 6 后“带个人对比=1”，行内显示 3.00 / LOW / 差值 0.00 与标准分 45.00 / LOCAL_DEMO_NORM |
| MT-STAT-007 | **部分失败**：Word 导出 200（31,122 字节 docx）；PDF 导出稳定返回 400；界面在未选任务+群组时点击导出无下载、无错误提示，见 F-22 |
| MT-STAT-008 | 通过：DEFAULT 租户统计只统计本租户任务/预警/报告 |

#### F-22（P1）群体报告 PDF 导出失败，且前端静默无反馈

- 同一筛选范围下：
  - `format=WORD` → 200，`application/vnd.openxmlformats-officedocument.wordprocessingml.document`，31,122 字节；
  - `format=PDF` / `format=pdf` / `exportFormat=PDF` → 400 `BAD_REQUEST`（请求错误）。
- 对照：个体报告 `GET /api/v1/exports/reports/download?reportId=4&exportFormat=PDF` → 200 `application/pdf` 51,788 字节，说明 PDF 生成能力本身可用，缺陷限定在群体报告 PDF 导出。
- 未限定任务+群组时接口返回 `GROUP_REPORT_EXPORT_SCOPE_REQUIRED`，但界面点击 PDF/Word 后没有任何提示或下载（静默失败），与 F-18/F-20 同类。

### 7.8 预警、干预与安全策略手工执行（2026-09-19 继续）

| 用例 | 结果 |
| --- | --- |
| MT-WARN-002/003 | 通过：接单使预警进入处理中；指派给 user 5 后状态“已指派”，`psy_warning_assignment` 写入 assignee=5/assigned_by=4 |
| MT-WARN-004 | 通过：预警导出弹窗 → 开始导出 → “导出完成，文件已下载到本地”，导出编号 `16634b9b-ebfe-427d-841a-e5765cf24e93` |
| MT-WARN-005 | 通过：干预记录 2 创建成功（PROCESSING），结案后干预/预警均为 CLOSED；warning 5 写入 3 条 response_event、1 条 follow_up（PENDING，2026-09-26 10:00）、close_checklist 4 项全部为 true |
| MT-WARN-006 | 通过：P0 策略由 assessor 创建、counselor 专业复核、assessor 审批 → APPROVED/active（reviewer 5 ≠ approver 4） |
| MT-WARN-006 负向 | 通过：同一用户（COUNSELOR+ASSESSMENT_ADMIN）自审自批 → `400 SAFETY_POLICY_DUAL_REVIEW_REQUIRED`，策略保持 DRAFT/inactive |
| MT-WARN-008 | 通过：P1 策略生效后新提交 HIGH → 新预警 `policy_resolution_status=RESOLVED`、`safety_policy_version=1` |
| MT-WARN-012 | 通过：无结案说明被前端阻断；缺少显式 `imminentDangerFlag` 时后端返回 `WARNING_CLOSE_CHECKLIST_REQUIRED`（fail-closed） |
| MT-WARN-013 | 通过：结案 `needRetest=true` → 复测任务 10（RETEST/IN_PROGRESS）创建并回填 `retest_task_id`；warning 6 关闭、检查单 4/4、follow_up PENDING |
| MT-WARN-007 | **失败**：策略快照 `MISSING` 的预警 4 仍可结案（CLOSED），未按安全响应策略 fail-closed 要求阻断，见 F-23 |

#### F-23（P1）缺少已审批策略的高风险预警仍可关闭

- 预警 4（HIGH→P0）创建时 `policy_resolution_status=MISSING`、无策略快照；在仅补齐联系/评估/交接/随访证据和 `imminentDangerFlag=false` 后，`POST /interventions/3/close` 返回 200，预警直接变为 `CLOSED`，`policy_resolution_status` 仍为 `MISSING`。
- 对照：预警 6 在 P1 策略生效后创建，`policy_resolution_status=RESOLVED` 后正常关闭，说明正常路径可用。

### 7.9 数据库、运维与导出链路手工执行（2026-09-19 继续）

| 用例 | 结果 |
| --- | --- |
| MT-DB-001 | 通过：`databaseMigration validate` 校验 28 个迁移成功（PostgreSQL 18.4，Flyway 提示仅测试到 17） |
| MT-DB-002/003 | 通过：`flyway_schema_history` 28 条且最高 V28；表 61 张（46 psy / 15 sys） |
| MT-DB-004 | 通过：安全策略双人分离、专业复核、题型白名单、发布审批证据 4 个关键约束存在 |
| MT-DB-007 | 通过：未设置 `PSY_FLYWAY_BASELINE_APPROVED=YES` 执行 baseline 被拒绝，报 “Baseline blocked…” |
| MT-DB-009/010 | 通过：db/migration 无未提交改动；`generate_code_docs.py check` 对 doc/10、doc/13 返回 ok |
| MT-OPS-007 | 通过：`/actuator/prometheus` 200、1.74MB，存在 scoring/warning queue/notification queue/export jobs/scheduler 指标 |
| MT-OPS-008 | 通过：请求头 `X-Correlation-Id` 原样回传 |
| MT-OPS-009 | 通过：`/actuator/health/liveness`、`/readiness` 均为 UP |
| MT-OPS-010 | 通过：`ops/prometheus/psy-alert-rules.yml` 含 18 条 alert/expr 规则 |
| MT-NFR-008 | 通过：`psy_e2e_*`、`psy_migration_*`、`psy_perf_*`、`psy_recovery_*` 残留 schema = 0 |
| MT-EXP-001/002 | 通过：个体报告同步导出 PDF 200（51,788 字节）、WORD 200、TEXT 200 |
| MT-EXP-003/004 | 通过：异步导出 job `31943cf9…` PENDING→PROCESSING→DONE；界面查询显示 DONE/WORD/文件大小 3175/重试 0；下载接口返回 200 docx（PK 头） |
| MT-STAT-007 | 部分失败（F-22）：群体报告 Word 200（31,122 字节），PDF 稳定 400 |
| MT-AND-001 ~ 006 | 本次范围外（用户指示不做 Android 检查） |

#### F-24（P2）总体健康检查 503 DOWN，但 liveness/readiness UP 且无法定位组件

- `GET /actuator/health` 返回 503 `{"status":"DOWN","groups":["liveness","readiness"]}`，而 `/actuator/health/liveness`、`/health/readiness` 均为 200 UP；Redis `PONG`、PostgreSQL 正常。
- 由于 `show-details: never`，看不到具体 DOWN 组件，`/actuator/health/{db,redis,diskSpace}` 均 404，运维无法从健康端点定位原因。
- 影响：使用整体 health 做就绪判断的探针会误判实例不可用；建议明确哪些组件应计入 readiness，并暴露受控的组件明细或排除非关键指标。
- 影响：高风险预警可以在没有任何已审批安全响应策略的情况下被结案，与“处置必须引用已生效策略、缺失时显式阻断”的规格冲突。

### 7.10 评分与质量策略实测（MT-SCORE-001 ~ 016）

执行方式：`python3 scripts/manual_test/run_api_suite.py --modules SCORE`（夹具走产品链路：Excel 导入 → 三语治理包 → Golden Case 运行/审批 → 双人复核 → 发布 → 真实任务提交）。

| 用例 | 结果 |
| --- | --- |
| MT-SCORE-001 | 通过：STRESS_DEMO A/B/C → 总分 6（MEDIUM），维度 1/2/3 |
| MT-SCORE-002 | 通过：REVERSE_SUM 总分 7；Q3/Q4 原始 0 → 反向 3，raw/reverse 留痕 |
| MT-SCORE-003 | 通过：WEIGHTED_SUM 7.5、维度 4/3.5；权重 0 导入被拒 |
| MT-SCORE-004 | 通过：AVERAGE 1.5（6/4），维度均值 2.5/0.5 |
| MT-SCORE-005 | 通过：WEIGHTED_AVERAGE 全 D → 3.0=15/5（分母为权重和） |
| MT-SCORE-006 | 通过：系数 1.25 → 7.5；系数 0 建量表 400 |
| MT-SCORE-007 | 通过：常模 mean=6/sd=3 → z=0.3333、T=53.3330（z 先取 4 位）、standardScore=T、normCode 落库 |
| MT-SCORE-008 | 通过（运行时口径）：运行时上下文无年龄/性别/机构 → 无命中时保持 NORMAL、norm/z/t 为空；Golden Case 验证成人常模命中与年龄 10 不命中 |
| MT-SCORE-009 | 通过：阈值规则与选项规则各触发一次，`high_risk_rule_code` 落库并各生成 1 条预警 |
| MT-SCORE-010 | 通过：REJECT 缺失必答 → 400 `ANSWER_REQUIRED_MISSING`，无结果行 |
| MT-SCORE-011 | 通过：ALLOW + maxMissingRatio 0.2 → 缺失比例 0.25、WARNING、MISSING_RATIO_EXCEEDED、总分=已答和 7 |
| MT-SCORE-012 | 通过：PRORATE 6→10（×4/3×1.25），维度 D2 ×2，轨迹 prorateFactor=1.33333333 |
| MT-SCORE-013 | 通过：300s=VALID；5s=REVIEW_REQUIRED/DURATION_TOO_SHORT；7200s=REVIEW_REQUIRED/DURATION_TOO_LONG |
| MT-SCORE-014 | 通过：轨迹含算法码/版本、逐题原始+有效分、维度、缺失策略、规则匹配，不含题干/自由文本 |
| MT-SCORE-015 | 通过：两次追加评分 → 版本 1/2/3、仅 1 行 is_current、supersedes 链完整、报告历史保留；对已失效结果再评分 404 |
| MT-SCORE-016 | 阻塞（外部证据）：SCL-90 技术包 DRAFT 导入 + 5 个 Golden Case 全绿（GSI/PST/PSDI 闭环），但无外部权利范围与专业/业务签署 → 不发布、不出正式报告（BLOCKED_EXTERNAL） |

### 7.11 通知、后台任务、数据库与非功能实测

| 用例 | 结果 |
| --- | --- |
| MT-NOTI-005 ~ 016 | 全部通过：投递明细/跨租户空列表、单渠道重试 SENT+租约清空、批量重试+审计+跨租户 0 行、关闭 PUSH 只生成 IN_APP、ANDROID 设备登记/重复登记单行/掩码、停用后不再投递、回执单调升级+反向重放拒绝、回调 401/404/授权入库、队列概况与 DB 一致、失败首行可见、死信重放清租约、无邮件主机时审批照常提交 |
| MT-OPS-001 | 通过：双实例共享库；外部持有 `psy:scheduler:lock:assessment:task-overdue` 时扫描跳过，释放后任务恰好一次 OVERDUE、1 条通知 |
| MT-OPS-002 | 通过：allowTimeoutSubmit=true 完整草稿自动提交（VALID、1 结果）+ 1 条 TASK_OVERDUE + scheduler 指标 |
| MT-OPS-003/004 | 通过：HIGH 预警升级 escalation_count=1；45 天草稿被清理、近期草稿保留 |
| MT-OPS-005/006 | 通过：通知与导出 worker 崩溃恢复演练（SENT/DONE、retry_count=1、单行） |
| MT-DB-005 | 通过：隔离 schema 迁移+种子二次执行行数不变、无重复用户、父子链一致 |
| MT-DB-006 | **失败**：`existing-database-baseline.sql` 要求的 `uk_psy_scale_code_version` 已被 V8 替换 → F-30 |
| MT-DB-008 | **失败（提交态）**：断言写死 23 个迁移、实际 28 → F-25；临时改 28 后整条演练通过（341,367B 备份、目录/数据 diff 一致、恢复冒烟） |
| MT-NFR-001 | 通过：1x/10x 性能基线成功、所有 HTTP Case 0 错误、schema 用后删除 |
| MT-NFR-002 ~ 005 | 通过：并发提交（1 答卷/1 结果/1 报告/1 预警）、并发保存（一个版本冲突）、导出并发租约、通知并发各发一次 |
| MT-NFR-006 ~ 008 | 通过：容量声明只限本机、治理保持 BLOCKED_EXTERNAL、演练残留 schema = 0 |

### 7.12 预约、报告、统计、权限与三语实测

| 用例 | 结果 |
| --- | --- |
| MT-APPT-003/006 | 通过：名额售罄 `SCHEDULE_FULL`；管理员代约 `source_type=ADMIN`，跨租户排班 404 |
| MT-APPT-007 | **失败**：end≤start 与 quota=0 被拒，但完全相同的重复排班被接受 → F-32 |
| MT-APPT-008/010/012 | 通过：咨询记录关联+越权 403；预约关联预警+跨租户预警 404；跨租户取消被拒、咨询师仅本租户 |
| MT-RPT-005 ~ 011 | 通过：检索/筛选/租户隔离、按用户查询+403、跨租户详情/导出 404、重新生成新版本+历史+审计、三语 locale 落库与无 locale 回退、三格式导出、未知模板阻断 |
| MT-RPT-014 | 通过：匿名任务 0 报告/0 预警，不出现在个人与工作人员报告列表，仅保留群体统计行 |
| MT-RPT-012/013 | 阻塞：图表渲染属前端视觉；SCL-90 待审核横幅需外部签署后正式发布 |
| MT-STAT-005 | 通过（附缺口）：维度分析返回平均分，标准差/最大最小/超标人数为空 → F-34 |
| MT-SEC-010/012/013/018/019/020 | 通过：无租户角色 400 `TENANT_CONTEXT_REQUIRED`、无凭据泄露、重放幂等、角色叠加仍租户受限、服务端 403、注入串原样存储且库未破坏 |
| MT-I18N-004/005/006 | 通过：未知枚举回退原始码、三语目录完整 + AntD locale 驱动校验、报告语言随提交语言（日语含假名、英语无 CJK） |

### 7.13 量表维护与发布治理剩余用例

通过 Excel 导入 + 维护 API 覆盖 MT-SCALE-003/006/008 ~ 019/021 ~ 025：基础信息、六题型、选项、结果规则重叠阻断、高风险规则条件必填、效度规则运行时未实现阻断、常模与覆盖度、图表配置、新版本与重复版本拒绝、版本对比、已发布不可编辑、草稿删除、权限负向、任务快照不受新版本影响；另覆盖 MT-PUB-014（历史游标倒序分页）、MT-PUB-016/017/018（发布权限 403、并发发布一个 200 一个 400、已发布包不可写）与 MT-TASK-006/007/008/010/014/015/016（时间校验、匿名开关 fail-closed、重考/超时开关与两次提交、草稿删除、分页筛选、权限负向、通知联动）。

### 7.14 新增缺陷（F-25 ~ F-37，按本轮实测证据）

| 编号 | 级别 | 说明 |
| --- | --- | --- |
| F-25 | P2（测试工具） | `scripts/sql/assert-backup-restore-core.sql` 写死 23 个迁移、仓库已是 28 → MT-DB-008 提交态必失败 |
| F-26 | P3（口径） | `sys_user` 无 age/gender/org_type 列 → 受限常模运行时永不命中，只能在 Golden Case 验证 |
| F-27 | P3（口径） | 常模无命中直接回落 `NORMAL`，无“待审核/无匹配”标识 |
| F-28 | P3（审计） | `scoring_trace_json` 内无生成时间（仅 `scored_at`） |
| F-29 | P2（一致性） | 通知投递明细跨租户返回 200+空列表，与其它跨租户 404/403 口径不一致（无泄露） |
| F-30 | P2（工具/文档） | 预检要求的 `uk_psy_scale_code_version` 已被 V8 替换 → 全量迁移库无法通过 baseline 预检 |
| F-31 | P2 | 逾期自动提交仅对 `allow_timeout_submit_flag=true` 生效（手顺未写）；REJECT 下不完整草稿静默留在 DRAFT，截止后无法再提交 |
| F-32 | P2 | 完全相同的重复排班被接受，与手顺“重复排班冲突被拒绝”不符 |
| F-33 | P2 | 缺少必填字段的请求体返回 500 `INTERNAL_ERROR` 而非 400 校验错误（统一错误信封口径） |
| F-34 | P3 | 维度分析只返回平均分，标准差/最大最小/超标人数为空 |
| F-35 | P3 | 通知故障工作台未返回“错误首行聚合数量” |
| F-36 | P3 | `Accept-Language: en` 回落 zh-CN，仅 `en-US` 生效 |
| F-37 | P3 | 预约状态机无确认/完成/失约入口（COMPLETED 仅种子数据），与手顺口径说明一致 |

### 7.15 缺陷修复与回归（2026-09-19 夜）

对本轮 FAIL 逐条定位根因并修复，随后回归受影响用例：

| 缺陷 | 根因 | 修复 | 回归证据 |
| --- | --- | --- | --- |
| F-11 禁用账号可登录 | `PasswordAuthenticationHandler` 只拦 LOCKED/PENDING_*/REJECTED，漏了 DISABLED | 新增 `AccountDisabledException(AUTH_403004)` + DISABLED 分支 + LOGIN_FAIL 审计；`auth.account.disabled` 三语消息 | MT-AUTH-003/MT-USER-005 通过（403 AUTH_403004，启用后恢复登录） |
| F-10 微信/Google Mock 任意 code 换令牌 | 未配置时 auto-config 默认注册 Mock Provider，任意非空 code 生成身份 | 新增 `mock-enabled`（默认 false）+ `DisabledWechat/GoogleSocialAuthProvider` fail-closed；`DefaultSocialLoginService` 把未配置异常转成 400 `auth.social.*.notConfigured` | MT-AUTH-028 通过（wechat/google 均 400，无令牌）；auth-starter 单测含 fail-closed 用例 |
| F-13 审计接口无授权 | `/auth/login-logs`、`/auth/security-events` 无 @PreAuthorize | 两个端点加管理角色限制 | MT-SEC-003 通过（respondent 403，org_manager 200） |
| F-12 任务时间 -8h 且叠加 | 前端 `dayjs.toISOString()` 把本地时间转 UTC | 改为 `format("YYYY-MM-DDTHH:mm:ss")` 提交本地墙钟 | 浏览器实测：UI 输入 2026-09-25 10:00 / 09-26 18:30 → DB 存储完全一致 |
| F-14 未知路径 500 | 静态资源回退异常未映射 | `GlobalExceptionHandler` 增加 `NoResourceFoundException→404`、`HttpRequestMethodNotSupported→405`、`HttpMessageNotReadable→400`、`DataIntegrityViolation→409`，并保留 `auth.*` 消息键 | MT-SEC-011 通过（404 统一信封、三语） |
| F-16 唯一键冲突 500 / F-33 缺必填字段 500 | 同上（未处理异常） | 同上 | 重复用户 400 `user.admin.username.exists`；缺字段 400 `VALIDATION_ERROR` |
| F-15 任务关闭无 UI 入口 | 前端只有 API | 任务列表新增“关闭任务”Popconfirm + `closeTask` mutation + 三语文案 | 浏览器实测：关闭 → 状态 CLOSED + toast“任务已关闭”；MT-TASK-005 通过 |
| F-17 量表搜索大小写敏感 | SQL `like` 直配 | `lower(scale_name/scale_code) like lower(:q)` | MT-SCALE-001 复核：`stress_demo`/`STRESS` 均可命中 |
| F-18/F-20 前端静默失败 | mutation 无 onError；发布阻断不提示 | 新增 `utils/api-error.ts`（提取后端 message+code），量表维护/发布/发布审批/群体报告导出接入 onError toast | MT-SCALE-007/020、MT-PUB-005 通过；浏览器实测业务审批失败弹出“量表创建人不能审批同一量表发布。(SCALE_PUBLICATION_INDEPENDENT_REVIEW_REQUIRED)” |
| F-19 导入不建 governance | Excel 导入不写治理行 | `ScalePackageRepository.createDraftGovernanceIfMissing` + 导入确认调用（DRAFT/PENDING_REVIEW） | MT-IMP-010 通过（就绪页由 GOVERNANCE_MISSING 变为 GOVERNANCE_NOT_APPROVED/SOURCE_REFERENCE_MISSING） |
| F-21 缺 reviewToken 500 | 请求体缺非空字段未映射 | 同 F-14 的 `HttpMessageNotReadable→400` | MT-PUB-005 通过（缺 token → 400 VALIDATION_ERROR） |
| F-22 群体报告 PDF 400 | macOS CJK 字体为 .ttc，PDFBox 直接 load 失败后回落 Helvetica，中文 glyph 抛异常 | `StatisticsService` 复用 TTC（TrueTypeCollection）加载 + 字形覆盖校验 + 不可编码字符降级为 `?` | MT-STAT-007 通过（PDF 57KB/200、WORD 31KB/200） |
| F-23 MISSING 策略预警可结案 | 结案未校验策略快照 | `WarningRepository.findPolicyResolutionStatus` + `InterventionService.close` fail-closed（400 `WARNING_SAFETY_POLICY_REQUIRED`） | MT-WARN-007 通过（预警保持 PROCESSING，业务单测覆盖） |
| F-24 整体 health 503 DOWN | mail 健康指示器（未配置 SMTP）拖垮整体状态 | `management.health.mail.enabled` 默认 false（配置 SMTP 时置 true）+ `show-components: always` | 浏览器/curl：health 200 UP 且可匿名访问，组件明细可见 |
| F-25 备份演练断言写死 23 | `assert-backup-restore-core.sql` 硬编码 | 改为读取 `psy.expected_migration_count`（wrapper 按迁移文件数注入，默认 28） | MT-DB-008 通过：提交态演练 exit=0，源/恢复库 migrations=28，目录/数据 diff 一致 |
| F-30 基线预检索引漂移 | 预检仍要求 V8 已删除的 `uk_psy_scale_code_version` | 预检接受旧索引或 V8 租户/全局唯一索引对 | MT-DB-006 通过（在一次性 legacy 形态库上执行 baseline+V16 全过） |
| F-32 重复排班被接受 | 创建排班无重叠校验 | `existsOverlappingSchedule` + `SCHEDULE_CONFLICT` + 三语消息 | MT-APPT-007 通过（相同排班 400 SCHEDULE_CONFLICT） |
| F-36 `Accept-Language: en` 回落中文 | supported locales 缺 `en` | `I18nConfig` 增加 `Locale.ENGLISH` | en-US/zh-CN 业务错误消息分别返回英文/中文 |
| F-39 多选超限错误码不统一 | Golden 评估对超限抛 MULTI_SELECT_INVALID | 评估路径改用 `ANSWER_SELECTION_LIMIT_EXCEEDED`（与运行时一致），其余非法仍 MULTI_SELECT_INVALID | 运行/评估两条路径代码一致（ANS-009 覆盖运行时） |
| F-29 跨租户投递明细返回空列表 | 与项目 404/403 口径不一致 | `findDeliveries` 在他租户存在投递时返回 404 | MT-NOTI-005 通过（跨租户 404） |

回归方式：`python3 scripts/manual_test/run_api_suite.py --modules ...`（NOTI/ANS/SEC/AUTH/IMP/APPT/SCALE/PUB/RPT/STAT/TASK/HOME/WARN/SCORE/EXP/NFR/DB）+ 浏览器实测（任务关闭、任务时间、发布审批错误提示）+ `gradlew test`（backend 480 通过）与 `npx vitest run src/i18n src/app`（前端 19 通过）。

仍未修复（记录为后续项，不影响本轮交付判定）：

- F-26/F-27/F-28（常模运行时上下文缺列、无命中直接回落 NORMAL、轨迹缺生成时间）：涉及用户模型字段与评分语义，需要产品/专业口径确认后再改。
- F-31（逾期自动提交仅当 allowTimeoutSubmitFlag=true）：手顺已按实现更新前置条件。
- F-34（维度分析只返回平均分）、F-35（故障集群未返回聚合数量）、F-38（导出语言跟随请求而非报告语言）：属增强项，建议与报表/运维需求一并排期。

### 7.16 代码审查与第二轮修复（2026-09-19 夜）

对 7.15 的提交做了一次变更级代码审查，逐条修复审查发现；修复均带回归证据：

| 审查发现 | 级别 | 修复 | 回归证据 |
| --- | --- | --- | --- |
| 127 条存量 `MISSING` 策略高危预警在 fail-closed 后无法结案且无补救入口 | P1 | 新增 `POST /api/v1/warnings/{id}/policy-resolution`（复用创建时的策略选择规则，写快照/版本/截止时间 + `PSY_WARNING_POLICY_RESOLVED` 审计）；无匹配策略时 400 `SAFETY_POLICY_NOT_AVAILABLE`；前端预警列表对 MISSING 行增加“重新解析策略”按钮 | MT-WARN-007 通过：MISSING 结案被拒 → 创建+双人审批 P2 策略 → 重新解析 RESOLVED+审计 → 证据链结案 CLOSED；浏览器实测成功提示“已绑定 v1”与失败提示“当前风险等级尚无已审批的安全响应策略…(SAFETY_POLICY_NOT_AVAILABLE)” |
| 禁用账号在密码校验前响应，可枚举账号且不记录失败 | P2 | `PasswordAuthenticationHandler` 调整为先验密码：凭证错误统一 401 `AUTH_401001`，密码正确后才返回 DISABLED/LOCKED/PENDING 状态错误并审计 | 实测“禁用+错密码 → 401 用户名或密码错误”；新增单测覆盖；MT-AUTH-003/USER-005 仍通过 |
| `Accept-Language: ja`/`zh` 裸标签回落中文 | P2 | `I18nConfig` 增加语言标签归一化 resolver（zh→zh-CN、ja→ja-JP、en→en，其余走原 AcceptHeader 解析） | 实测 ja→日语、zh→中文、en/en-GB→英语、ja-JP→日语 |
| `TrueTypeCollection` 成功路径未关闭 | P3 | `StatisticsService` 改为返回 `LoadedCjkFont(font, closeable)`，在 PDF 写完后 finally 关闭 | MT-STAT-007 通过（PDF 200/56.8KB） |
| 缺 `auth.social.provider.unsupported` 消息键，客户端看到原始键 | P3 | 三个 bundle 补齐该键 | 未再出现裸键（provider 未启用返回本地化文案） |
| 公开的 `/auth/social/{google,wechat}/mock` 重复端点 | P3 | 删除端点与 `permitAll` 配置 | 匿名 401、带令牌 404 RESOURCE_NOT_FOUND；auth-starter 测试通过 |
| 量表页仅 3/19 个 mutation 有 onError | P3 | 补全 15 个 mutation 的统一错误提示（`scales.operationFailed`） | 前端 tsc + 128 用例通过 |
| 关闭任务使用写死原因，与弹窗文案不符 | P3 | 改为弹窗内“关闭原因”输入框（必填/500 字），提交操作者填写的原因 | 浏览器实测：填写“MT UI 关闭原因验证”→ 状态 CLOSED、`close_reason` 与输入一致、toast“任务已关闭” |
| mail 健康默认关闭未在部署文档说明 | P3 | doc/20 增补健康检查与导出保留策略说明（含 `PSY_MAIL_HEALTH_ENABLED`） | 文档更新 |
| 测试工具硬编码本机路径 / `sql_one` 空结果抛异常 / 魔法时间戳 / raw_body 与 body 冲突静默 | P4 | 抽出 `checks_common.py`（共享 api/wait_for）；路径改环境变量；`sql_one` 空结果返回空串；`http` 冲突显式报错；consolidate 用显式 `source` 标记；`run_cases` 不再用 NOT_EXECUTED 覆盖历史结果；8094 端口占用时拒绝启动 | 全量手顺回归 0 失败 |
| （测试偶发暴露的产品缺陷）导出任务含死信在完成后 15 分钟被硬编码清理，死信可能来不及人工重放 | P2 | `ExportJobStore` 增加可配置保留：`retention-seconds`（默认 900）、`dead-letter-retention-seconds`（默认 604800）、`cleanup-scan-delay-ms`；清理仅删除过期 DONE/FAILED，死信按 7 天窗口保留 | 新增单测 `cleanup keeps dead letters until the longer dead-letter window expires`；后端 481 用例全绿；MT-EXP-005/011/012 通过 |

顺带修正的测试口径：MT-EXP-005 先创建任务再断言列表；MT-EXP-012 先触发一次导出再断言 `psy_export_*` 指标；MT-HOME-001 只统计 IN_APP 未读；MT-I18N-008 改为“三语键集合一致且 ≥494”而非固定 494。

`python3 scripts/generate_code_docs.py check` 重新通过（业务端点 110 条、认证端点 52 条，已反映新增预警策略解析接口与移除的 Mock 端点）。

### 7.17 汇总（最终）

- 机器可核对状态：`build/reports/manual-test/execution.json`（324 条逐条 status/detail）+ `build/reports/manual-test/evidence/*.json`；汇总命令 `scripts/manual_test/consolidate_evidence.py`。
- **最终计数：PASS 306 / FAIL 0 / BLOCKED 18 / 未执行 0**（324 条）；全部 FAIL 已修复并回归（见 §7.15）。
- 执行覆盖面：冒烟、认证与账号、用户/组织、量表维护与发布治理、导入（Excel + 源包）、任务、作答（含多题型链路）、评分全方法+质量策略+常模、报告、统计、通知、导出、预约咨询、预警/干预/安全策略、权限与租户隔离、三语、数据库、后台任务与恢复演练、非功能并发与性能基线。
- BLOCKED 18 条的构成：Android 6 条（用户指示本轮不做，按手顺要求不得计为通过）、需真实外部环境 7 条（邮箱激活、SSO、微信、导出死信故障注入、tenantless SYS_ADMIN、图表渲染、SCL-90 待审核横幅）、跳题源包与 SCL-90 正式发布各 1 条、其余为容器内无法获取的 UI 截图类。
- 原 FAIL 17 条（F-10~F-25/F-30/F-32/F-33 等）已全部定位根因并修复，回归后转 PASS；修复清单与证据见 §7.15。
- 本轮补充执行（第二批）覆盖 ANS-007/009~015/020~022（多题型发布量表上的作答校验与语言留痕）、MT-IMP-006~015、MT-EXP-005~012、MT-WARN-001/009/011/014~017、MT-HOME-001/002/006~008、MT-AUTH-003/006/008/009/023~025（+022/027/029 记录为外部阻塞）、MT-PUB-001/006/007/008~018、MT-RPT-002/015、MT-SEC-001/002，以及 7 条交叉引用用例（ANS-018/019、WARN-010、EXP-007/009/010、PUB-015）。

### 7.16 第二批新增发现

| 编号 | 级别 | 说明 |
| --- | --- | --- |
| F-38 | P3 | 导出任务的 `locale_tag` 与文件名语言跟随**请求头语言**而不是报告本身的语言：对 ja-JP 报告用 zh-CN 请求导出时，生成中文文件名而内容为日文（EXP-011 用 ja-JP 请求时一致，属口径问题） |
| F-39 | P3 | 多选超限返回的 code 为 `ANSWER_SELECTION_LIMIT_EXCEEDED`，而 Golden Case 评估路径抛 `MULTI_SELECT_INVALID`，两个入口对同一违规的代码不统一（ANS-009 记录） |
