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

## 8. 前端审查修复（2026-09-19，全部落地）

用户要求“梳理画面前端，是否有不太和业务的地方或者不是特别容易操作的地方”后，对 admin-web 全量页面做了业务贴合度与易用性审查，并按“全部修改”完成实现。审查项与实现对应关系如下（编号与 §25/MT-FE 对齐）：

### 8.1 后端（lx-boot backend）

| 变更 | 位置 | 说明 |
| --- | --- | --- |
| 管理端预约登记 | `AppointmentController`/`AppointmentService`/`AppointmentRepository` | 新增 `GET /api/v1/appointments`（状态/被预约人/咨询师/日期区间 + 分页）；`AppointmentSummary` 增加被预约人姓名与账号 |
| 代客预约 | `CreateAppointmentRequest.userId` | 工作人员可为他人预约（`sourceType=ADMIN`，`user_id`=被预约人）；非本租户/停用用户返回 `APPOINTMENT_TARGET_NOT_FOUND`；普通用户传他人 ID 返回 `APPOINTMENT_FORBIDDEN` |
| 代取消 | `AppointmentService.cancel` | 预约归属人以外，工作人员可在本租户内代取消（`APPOINTMENT_CANNOT_CANCEL` 仍保护终态） |
| 权限清单对齐 | `@PreAuthorize` | `POST /appointments` 增加 COUNSELOR；`POST /counselors/me/schedules` 增加 ORG_MANAGER/SYS_ADMIN；`GET /statistics/dashboard|group-reports|download` 增加 SCHOOL_LEADER；`POST/GET /exports/reports*` 增加 USER（服务层已有归属校验） |
| 预警责任人 | `WarningRepository/Service/Controller` | 列表返回 `assigneeUserId/assigneeDisplayName`；新增 `GET /api/v1/warnings/assignee-options`（本租户在职工作人员） |
| 选择器目录 | 新增 `org.sainm.psy.directory.*` | `GET /api/v1/directory/{users,groups,tasks,scales}`，供预约/报告/发布/治理/审计页面共用的下拉选择 |
| 错误文案 | `i18n/messages*.properties` | 新增 `error.appointment_target_not_found`（中/日/英） |

### 8.2 前端（admin-web）

- 权限模型：`pickPrimaryRole` 只用于外壳选择；新增 `hasAnyRole` 并让 `Permission`/`AccessGuard`/菜单/路由重定向按**角色并集**判定（多角色账号不再丢功能）。
- 预约页：管理端改为“全部预约 + 筛选 + 服务端分页 + 代客预约 + 代取消”；咨询记录按钮仅在“已完成/已确认且时间已过”可用；用户视图保持原样。
- 预警页：责任人选择器、“责任人”列、逾期红标、操作收敛为“接单/指派/报告页/更多”。
- 量表发布/治理：量表 ID 输入框改为可搜索的量表选择器（保留 `?scaleId=` 深链）。
- 群体报告：四个手输 ID 改为选择器，列表接服务端分页（`pagination` + 总数）。
- 个体报告：用户/群组/量表/任务改用共享选择器，修复 ASSESSMENT_ADMIN 调 `/user-admin/*` 会 403 的隐患。
- 通知页：拆为“我的通知 / 通知运维”两个页签（普通用户仍为单页）。
- 导出中心：最近作业行内可下载；新增保留策略提示。
- 认证审计：页码/条数提示、每页条数、跳页；会话治理账号选择器；三条硬编码英文提示改为三语；会话状态与审计结果本地化。
- 仪表盘：新增按角色显示的快捷入口；最近预警/最近报告可点击跳转。
- 报告详情：被测者可导出自己的报告（后端归属校验不变）。
- 我的任务：状态以服务端为准，本地标记只显示“本设备已提交”提示。
- 菜单：按业务域分组（工作台/测评管理/风险与预警/报告与数据/服务与通知/组织与安全/我的空间）。
- 枚举本地化：任务模式/状态、评分方式、量表状态、治理下拉、Golden Case 类型、策略优先级、会话状态、审计结果、设备信任/自动处置、导出格式均为三语词条。

### 8.3 回归证据（2026-09-19 22:2x）

- 后端：`bash ./gradlew test` 全绿（493+ 用例；新增预约代约/代取消、预警指派选项、目录接口权限测试；`ControllerAuthorizationContractTest` 同步新权限清单）。
- 前端：`npx tsc -b` 无错；`npx vitest run` 128+ 通过（含新增多角色路由断言）。
- 文档：`python3 scripts/generate_code_docs.py check` 通过（新端点已写入 `doc/13-api-design-detailed.md`）。
- 真实接口（重启后的最新 jar）：
  - `GET /api/v1/directory/users|groups|tasks|scales` 正常返回；
  - `GET /api/v1/warnings/assignee-options` 返回 assessor/counselor/org_manager 等在职人员；
  - `GET /api/v1/appointments?userId=6` 只返回被预约人 6 的预约，且带 `userDisplayName`；
  - 管理员代约：`POST /api/v1/appointments {userId:6}` → 预约 33（ADMIN/归属用户 6）；随后 `POST /appointments/33/cancel` → CANCELLED（管理端代取消）；
  - `POST /api/v1/warnings/255/assign {assigneeUserId:5}` → ASSIGNED，列表责任人列返回“Default Counselor”。
- 真实界面（in-app 浏览器快照）：菜单已分组；预约页显示“全部预约 + 筛选 + 共 30 条分页 + 被预约人列 + 禁用态咨询记录按钮”；预警页显示“责任人”列、已逾期红标与“接单/指派/报告页/更多”操作列。

### 8.4 本轮数据变更与未完成项

- 测试数据变更：预约 33（用户 6，ADMIN 来源，已取消，备注 `MT admin on-behalf review`）；预警 255 已指派给用户 5（Default Counselor）。
- 未完成（需跨仓库/产品确认）：
  - 认证审计的“总数”需要 auth-starter 审计 SPI 返回 count（本轮已提供页码/条数/每页条数/跳页，总数留待 SPI 变更后补充）；
  - `COMPLETED/NO_SHOW` 的预约状态产品入口仍缺失（沿用 MT-APPT-009 记录）；
  - `ScaleListPage.tsx`（2402 行）体量问题本轮做了枚举本地化与入口优化，文件拆分作为独立重构任务保留。

## 9. 代码审查问题修正（2026-09-19，review-agent 第二轮）

针对 review-agent 对 §8 改动的缺陷清单，逐条修正并回归：

| 编号 | 级别 | 问题 | 修正 |
| --- | --- | --- | --- |
| R-1 | P1 | 通知页把「设备与推送」移进管理页签，普通用户无法登记/停用设备（MT-NOTI-009 失效） | 设备区块对普通用户（无页签）始终渲染；管理人员在「我的通知」页签同样可见，仅运维卡片留在「通知运维」页签。实测 `respondent` 登录后通知页显示设备表单与已绑设备列表（含停用按钮） |
| R-2 | P2 | 取消预约确认框显示原始 key `appointments.cancelConfirm` | 三语补词条（确认取消该预约吗？/この予約をキャンセルしますか？/Cancel this appointment?），并用脚本校验全量 `t("...")` key 无缺失 |
| R-3 | P2 | 审计会话治理、群体报告对比用户只加载前 100 人且无服务端搜索（默认租户 132 人，后 32 人不可选） | 两处选择器改为 `onSearch` + `filterOption={false}` 的服务端关键字检索，`/directory/users?keyword=` 实时查询 |
| R-4 | P3 | 校领导点击仪表盘「最近报告」行进入 403 页面 | 按 `canRolesAccessPath(roles, "/reports/1")` 决定是否挂 `onRow` 跳转 |
| R-5 | P3 | 治理下拉缺 `governance.option.DRAFT`，仍显示裸 `DRAFT` | 三语补 DRAFT 词条（草稿/下書き/Draft） |
| R-6 | P3 | 审计失败结果词条用了 `FAILURE`，后端实际写 `FAIL` | 新增 `authAudit.resultValue.FAIL` 三语词条（失败/失敗/Failure） |
| R-7 | P3 | 导出保留期提示写死 15 分钟/7 天，与可配置保留期可能不一致 | 后端 `ExportArtifactStorageInfoResponse` 新增 `retentionSeconds`/`deadLetterRetentionSeconds`，前端按接口值渲染提示（实测返回 900/604800） |
| R-8 | P3 | 代客预约下拉包含已停用用户，选中必被后端拒绝 | `/directory/users` 新增 `activeOnly`；代客预约选择器用 `activeOnly=true`（默认租户 132 人中 100 在职），历史预约筛选仍可用全部用户 |
| R-9 | P3 | `safetyPolicyPriorityLabel` 未被使用，策略页仍显示裸 P0–P3 | 策略页风险等级下拉接入该 helper |
| R-10 | 附带 | `scalePublication.goldenCaseHistory` 词条历史缺失（量表列表显示裸 key） | 三语补词条（Golden Case 历史版本/ゴールデンケース履歴/Golden case history） |

回归证据：

- 后端 `bash ./gradlew test`：BUILD SUCCESSFUL（含新增 `activeOnly` 控制器用例）；`generate_code_docs.py check` 通过。
- 前端 `npx tsc -b`、`npx vitest run`（131 通过）、`npm run build` 全部通过；全量 i18n key 覆盖脚本无缺失。
- 真实接口：`/directory/users?activeOnly=true` 只返回 ENABLED（100 条），不带参数返回 132 条含 DISABLED；`/exports/reports/storage` 返回 `retentionSeconds=900`、`deadLetterRetentionSeconds=604800`。
- 真实界面：管理端通知页「我的通知」页签可见设备与推送；`respondent` 登录后通知页为单页且包含设备登记与设备列表（本轮验证期间 in-app 浏览器保留为 `respondent` 会话）。

## 10. 逐页中日英显示侦测与修正（2026-09-19）

按“每个页面中日英的显示都需要侦测，不匹配的情况调查后修改”的要求，新增两套侦测手段并逐条修正：

| 工具 | 位置 | 作用 |
| --- | --- | --- |
| 静态扫描 | `scripts/i18n_source_audit.py` | 扫描 `admin-web/src`（排除 i18n 目录）中未走 `t()` 的中日文字面量；结果 0 条 |
| 运行时逐页扫描 | `admin-web/e2e/i18n-page-sweep.spec.ts` | 22 个页面 × zh-CN/ja-JP/en-US（66 次真实加载），检测原始 i18n key、裸枚举码、`undefined/null/NaN`、路由标题缺失/串语言、接口 4xx/5xx、控制台报错，并按页面跨语言比对可疑未翻译文本；报告写入 `build/reports/i18n-sweep/report.json` |

侦测发现与修正：

| 编号 | 现象 | 根因 | 修正 |
| --- | --- | --- | --- |
| I-1 | 直接打开 `/auth-audit` 返回后端 `AUTH_401002` JSON、页面空白 | Vite 代理用前缀 `/auth` 把 SPA 路由 `/auth-audit` 也转发给后端（k8s/nginx 若用 `location /auth/` 前缀同样会吞掉 `/auth/sso/callback`） | `vite.config.ts` 代理收窄为 `/auth/`，并为 SPA 的 `/auth/sso/callback` 增加 bypass 返回 `index.html`；部署侧需把 `/auth/sso/callback` 排除在 API 前缀之外 |
| I-2 | 会话详情页显示裸 `ACTIVE` / `SUCCESS` | 页面未走枚举词条 | `SessionDetailPage` 接入 `sessionStatusLabel` / `auditResultLabel`（三语） |
| I-3 | 通知消息在日语/英语界面仍显示中文标题与正文（预警催办、干预创建/结案等） | 通知在**创建时**按触发者语言落库，之后不再翻译 | 后端新增 `NotificationLocalizer`：读取 `/my/notifications` 时按**查看者语言**重新渲染（title/content 词条 + payload 参数），参数缺失的历史数据回退原文；`NotificationContextRepository` 为历史记录补查 `intervention→warningId`、`task→taskName`；同时补齐 dispatcher payload（TASK_OVERDUE 任务名、WARNING_CLAIMED/ASSIGNED/INTERVENTION_* 的 id 参数、RETEST 任务名），保证新数据零回退 |
| I-4 | `/notifications` 在 ASSESSMENT_ADMIN 下出现 403 控制台错误 | 运维页签对非 USER 一律请求 `/notifications/policies`，而该接口只允许 ADMIN/SUPER_ADMIN/SYS_ADMIN | 策略查询与策略卡片按 `ADMIN/SUPER_ADMIN/SYS_ADMIN` 门控，其他运维角色仍可用投递概况/失败聚类/重试 |
| I-5 | 用户管理表格显示 `ASSESSMENT_ADMIN` 等角色码 | 表格未本地化角色码 | 接入 `getRoleLabel`（三语），未知码保持原样 |
| I-6 | 报告详情题目表格控制台警告 antd `rowKey index` 弃用 | `rowKey={(record,index)=>…}` 使用了 index 参数 | 改为基于 `questionId/optionCode/optionLabel/answerText` 的稳定键 |
| I-7（工具链） | 用例 `MT-SEC-018`/`MT-SEC-020` 在两个模块重复注册，后者静默覆盖前者 | harness `CHECKS` 以 ID 为键 | 合并两处断言（多角色叠加 + 跨租户约束；画像 XSS + 任务名注入），重复注册清零 |

回归证据：

- 逐页扫描最终结果：`pages=22 locales=3`，原始 key/裸枚举/`undefined`/标题缺失/页面空白/接口错误/控制台错误均为 0；剩余 61 条 `identical-across-locales` 全部为业务数据（ID、账号、租户/组名、事件码 `PSY_*`、存储模式、量表题干与选项文本）。
- 静态扫描：`hardcoded CJK literals: 0`。
- 后端 `cleanTest test`：499 用例 0 失败（新增 `NotificationLocalizerTest` 5 条）；前端 `tsc -b`、`vitest run`（131）与 `npm run build` 全部通过。

## 11. 全量手动测试重设与整跑（2026-09-19）

按“全功能 + 全业务 + 全网络”重设手动测试体系，并完成一轮整跑。

### 11.1 体系与工具

| 产物 | 说明 |
| --- | --- |
| `doc/31-manual-test-procedure-full.md` | 全量执行标准：MT-API（全功能接口）、MT-UI（逐页三语）、MT-BIZ（端到端业务）、MT-NET（全网络外部通道）与判定规则 |
| `scripts/manual_test/build_catalog.py` | 从 doc/30 + doc/31 生成 `build/reports/manual-test/cases.json`（411 条，含 doc/30 的 324 条历史用例，无丢失） |
| `scripts/manual_test/coverage_audit.py` | 覆盖率审计：**接口 168/168 全部由自动用例实际请求**（含占位段匹配 4 个、循环字面量路径 4 个）、**前端路由 23/23**；输出 `doc/manual-test/coverage-matrix.md` 与 `case-registry.json` |
| `scripts/manual_test/checks_full_coverage.py` | 新增 MT-API-001~027（27 条）覆盖此前无自动用例的 42 个接口 |
| `scripts/manual_test/consolidate_full_suite.py` | 把 UI/I18N/BIZ/NET/FE 的证据合并进执行日志 |
| `admin-web/e2e/i18n-page-sweep.spec.ts` + `scripts/i18n_source_audit.py` | 逐页三语运行时侦测与静态硬编码扫描（§10） |

> 本轮之后外部通道用例已改为套件内真实执行（`checks_network.py`、`smtp_sink.py`、对象存储故障注入、
> 套件内图表渲染与 `MT-PUB-006` 自发布哈希校验），见 §12；`consolidate_full_suite.py` 不再覆盖本轮实跑的 PASS。

### 11.2 本轮发现并修复的缺陷

| 编号 | 级别 | 现象 | 修正 |
| --- | --- | --- | --- |
| G-1 | P2 | `GET /wechat/portal` 缺少必填参数时返回 **500 INTERNAL_ERROR**（其他缺少必填参数的接口同理） | `GlobalExceptionHandler` 新增 `MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException` → 400 `VALIDATION_ERROR`（实测：缺参 400，带参回显 200） |
| G-2 | P1 | 导出作业下载用例查错列（`psy_export_job` 主键为 `id`） | 用例改为 `select id ...`；提交一次真实导出作业后 MT-API-006 PASS（下载 997B） |
| G-3 | P1 | `GET /reports/by-result/{resultId}` 用例传了答卷 ID（应为 `psy_assessment_result.id`） | 用例改用 result 表主键；实测 result 270 → report 271 |
| G-4 | P1 | 任务更新用例在 IN_PROGRESS 任务上执行，触发 `TASK_NOT_EDITABLE` | 用例改为创建 DRAFT 任务→更新→校验→删除，符合后端状态机 |
| G-5 | P2 | 发布历史接口返回 `cases/reviews/runs` 游标结构，用例按 `items` 断言 | 用例兼容三种键；实测 history=4/reviews=0/runs=0 |
| G-6 | **P1** | 配置 `PSY_MAIL_HOST` 后激活邮件仍被丢弃（`NoOpMailSenderService` 生效）——`MailSenderConfiguration` 的 `@ConditionalOnBean(JavaMailSender)` 在用户配置阶段评估不到自动配置的 Bean | 去掉该条件（保留 `spring.mail.host` 条件与表达式判断）；真实 SMTP 通道随即打通 |
| G-7 | **P1** | **全站报告图表被静默禁用**：`VisualizationRepository.hasTable()` 按表名跨 schema 统计并要求 `== 1`，当存在第二个同名表（本机 `mt_dbg2` schema）时返回 false；`ReportService.withVisualizations()` 又用 `runCatching{}.getOrNull()` 吞掉异常且无日志 | `hasTable()` 改用 `to_regclass('psy_scale_visualization_config') is not null`（按 search_path 解析）；`withVisualizations()` 增加 warn 日志；可视化服务补充 debug 日志 |
| G-8 | P2 | harness `MT-SCALE-017` 使用 `viewScope=REPORT`，而渲染器只识别 `REPORT_DETAIL`（前端下拉也仅提供 REPORT_DETAIL/GROUP_REPORT），配置写入后永不显示且接口不校验 | 用例改为 `REPORT_DETAIL`；同时记录“接口未校验 scope 枚举”为待加固项 |

### 11.3 整跑结果（411 条用例）

| 指标 | 数值 |
| --- | --- |
| 用例总数（catalog） | 411（doc/30 324 条 + MT-API 27 + MT-UI 12 + MT-BIZ 10 + MT-NET 6 + MT-FE 29 + MT-I18N 新增 3） |
| PASS | **400** |
| FAIL | **0** |
| BLOCKED | 11（Android 6 条按用户指示不测；微信 2 条缺公众号凭据；外部专业/业务签署 3 条） |
| NOT_EXECUTED | 0 |
| 接口覆盖 | 168/168（自动 168） |
| 路由覆盖 | 23/23 |
| 逐页三语 | 22 页 × 3 语，7 类检测项全部 0 缺陷；剩余 `identical-across-locales` 均为业务数据 |

首轮整跑为 PASS 388 / BLOCKED 23；随后按 §11.5 接通本机真实外部对端、修复 G-1~G-8 并复跑，最终为 **PASS 400 / FAIL 0 / BLOCKED 11 / NOT_EXECUTED 0**。

接口覆盖口径：方法一致且路径逐段匹配，设计文档中的占位段（`{id}`）接受具体值（如 `POST /api/v1/scales/234/dimensions/99999999` 覆盖 `POST /api/v1/scales/{id}/dimensions/{dimensionId}`），用例侧由变量拼出的 `*` 必须与占位段对齐，因此不存在“未请求却记账”的接口；初始版本中仅在手顺声明的 8 个接口（MT-API-013/014/015/023）已全部由自动用例实际请求。

关键证据路径：

- 执行日志：`build/reports/manual-test/execution.json`（每条含 status/detail/时间）
- 单条证据：`build/reports/manual-test/evidence/<caseId>.json`
- 逐页三语：`build/reports/i18n-sweep/report.json`
- 覆盖矩阵：`doc/manual-test/coverage-matrix.md`、`doc/manual-test/case-registry.json`

### 11.4 外部条件依赖项与当前状态

| 类别 | 用例 | 需要的条件 | 状态 |
| --- | --- | --- | --- |
| 邮件 | MT-NET-002 / MT-AUTH-022 | 真实 SMTP 主机与账号（`SPRING_MAIL_*`） | ✅ §11.5 本机 SMTP sink 闭环 |
| SSO | MT-NET-003 / MT-AUTH-027 / MT-SEC-014 | OIDC 或 CAS 测试 IdP（issuer/client/secret/callback） | ✅ §11.5 本机 CAS IdP 闭环 |
| 推送 | MT-NET-005 | 可达的推送 HTTP 接收端 | ✅ §11.5 本机接收端闭环 |
| 对象存储 | MT-NET-006 / MT-EXP-008 | S3/MinIO endpoint/bucket/密钥（含死信故障注入） | ✅ §11.5 HTTP 对象存储 + 死信重放闭环 |
| 微信 | MT-NET-004 / MT-AUTH-029 | 公众号 appId/secret（OAuth + JS-SDK + 菜单） | ⛔ **仍阻塞**：无真实公众号凭据，只能验证失败关闭路径 |
| Android | MT-AND-001~006 | Android SDK/模拟器 | ⛔ **仍阻塞**：用户已指示本轮不做 Android 检查 |
| 外部专业签署 | MT-RPT-013 / MT-SCORE-016 | 外部专业签署（SCL-90 待专业复核模板、GSI/PST/PSDI 结论） | ⛔ **仍阻塞**：需外部专业/业务签署 |
| 跳题能力 | MT-ANS-016 | 通用 profile 明确拒绝 `skipRules`（`GENERIC_SINGLE_CHOICE does not support skipRules`），带跳题的量表必须走**专用 profile**；需产品侧决定是否实现该 profile，之后才能验证运行时跳题 | ⛔ **仍阻塞**：属产品能力缺口，非环境问题 |

上述条件就绪后，按 `doc/31` §4 的配置与验证命令直接执行即可，无需改动用例定义。

### 11.5 本轮已闭环的外部通道（真实网络对端）

为在不依赖外部账号的前提下执行「全网络」正路径，本机起了三类**真实网络对端**（非应用内 mock）：

| 通道 | 对端工具 | 验证结果 |
| --- | --- | --- |
| SMTP | `python3 -m smtpd -n -c DebuggingServer 127.0.0.1:2525` | 外部注册 → 收到激活邮件（含 token）→ `GET /auth/email-verify` 200，用户状态 3→4（MT-NET-002 / MT-AUTH-022 PASS） |
| 推送 | `python3 scripts/manual_test/push_receiver.py`（127.0.0.1:9099） | 指派任务后 PUSH 投递收到 chunked JSON（deliveryId/notificationId/receiver/device/token/title/content/deepLink/payload），投递状态 SENT、provider=http（MT-NET-005 PASS） |
| 对象存储 | `python3 scripts/manual_test/http_object_store.py`（127.0.0.1:9100，`HTTP_OBJECT_STORAGE` 模式） | 导出作业 PUT 997B（带 X-Api-Key）落盘；应用内下载触发 GET 200/997B；`/exports/reports/storage` 显示 mode/bucket（MT-NET-006 PASS） |
| 导出入死信 | 将 `PSY_EXPORT_ARTIFACT_ENDPOINT_URL` 指向不可达端口并缩短重试 | PENDING→重试→**DEAD_LETTER**（retry=3）；恢复存储后 `POST /exports/reports/jobs/{id}/retry` 重放 → DONE（MT-EXP-008 PASS） |
| 报告图表 | `scripts/manual_test/seed_chart_scale.py` + `admin-web/e2e/report-charts.spec.ts` | 发布带可视化配置的合成量表 → 生成 report 309 → 3 个 canvas 全部绘制（截图 `build/reports/chart-checks/report-309.png`）（MT-RPT-012 PASS） |
| 自助注册开关 | `PSY_AUTH_SELF_REGISTRATION_ENABLED=false` | `POST /auth/register` → 400 `AUTH_400002`「当前未开放自助注册。」（MT-AUTH-019 PASS） |
| 扫码登录 | `AUTH_MODULE_QR_LOGIN_ENABLED=true` | scene PENDING→SCANNED→APPROVED→CONSUMED（返回令牌对）；cancel→CANCELED，取消后 confirm 400（MT-AUTH-030 PASS） |
| 无租户超管 | harness 自建 tenantless SYS_ADMIN（`mtglobaladmin`，复制 sysadmin 凭据哈希） | 全局视角可见 3 个租户的量表；写入 `PSY_TENANT_SCOPE_OVERRIDE` 审计（MT-SEC-009 PASS） |
| 报告图表缺陷 | `VisualizationRepository.hasTable()` + `ReportService.withVisualizations()` | 修复后 report 309 返回 3 个可视化；异常不再静默吞掉（G-7） |
| SSO（CAS） | `scripts/manual_test/cas_test_idp.py`（:9200，真实 CAS 协议子集） | authorize→CAS login→callback→`/auth/sso/token` 换令牌 200；本地账号 `mtcasuser` 首次登录完成身份绑定（`sys_auth` 出现 `CAS|mtcasuser`）；未预置账号 401 `auth.sso.user.notProvisioned`；应用票据与 CAS 票据均一次性（MT-NET-003 / MT-AUTH-027 / MT-SEC-014 PASS） |

执行后的整跑统计：**411 条用例，PASS 400 / FAIL 0 / BLOCKED 11 / NOT_EXECUTED 0**。
剩余 11 条阻塞：Android 6 条（用户指示不测）、微信 2 条（MT-AUTH-029 / MT-NET-004，缺真实公众号凭据）、外部专业/业务签署 3 条（MT-ANS-016 / MT-RPT-013 / MT-SCORE-016）。

## 12. 全网络用例真实化、覆盖率口径与发布哈希一致性缺陷（2026-09-20 追加）

### 12.1 动机

§11 的外部通道用例当时以「环境轮次执行 + 汇总脚本写入证据」的方式闭环：`consolidate_full_suite.py` 会直接覆盖
`execution.json` 里的状态，既能掩盖本轮失败，也可能把「本轮根本没执行的用例」记成通过。本轮把这批用例改成
**每轮真实执行**的检查，并让汇总只负责「本轮确实无法执行的模块」。

### 12.2 新增与改造

| 产物 | 变化 |
| --- | --- |
| `scripts/manual_test/smtp_sink.py` | 新增：真实 SMTP 接收端，把每封邮件（含 multipart 正文与激活链接）写成 `build/reports/manual-test/smtp-sink.jsonl` |
| `scripts/manual_test/net_channels.py` | 新增：不跟随 302 的原始 HTTP 客户端、JSONL 回读/等待、TCP 可达性探测 |
| `scripts/manual_test/checks_network.py` | 新增：MT-NET-001~006 真实执行（出网基线 / SMTP / CAS SSO / 微信阻塞 / 推送 / 对象存储） |
| `scripts/manual_test/http_object_store.py` | 新增 `POST /__control`：注入 PUT 失败（503）与恢复 |
| MT-EXP-008 | 由「说明性阻塞」改为真实演练：注入失败 → 作业 `DEAD_LETTER` → 关闭注入 → `retry` → `DONE` 并可下载 |
| MT-AUTH-022 / MT-AUTH-027 / MT-SEC-014 | 由占位阻塞改为真实执行：邮件激活 3→4；CAS 全链路 + 未预置账号 401 不自动建号；应用票据/CAS 票据重放与 state 篡改全部失败关闭 |
| MT-RPT-012 | 套件内直接运行 `e2e/report-charts.spec.ts`（Chromium）断言 canvas 全部绘制并保存截图 |
| MT-PUB-006 | 改为本轮自行发布带图表配置的量表，断言「发布哈希 == 两次导出哈希」，并给出 G-9 的回归保护 |
| `harness.py` | 实跑用例写入 `executedBy=harness`，供汇总脚本区分「本轮实跑」与「环境轮次证据」 |
| `consolidate_full_suite.py` | 只为本轮未执行的模块补写证据；实跑 PASS 保留原始明细；**遇到 FAIL 直接终止**汇总 |

### 12.3 全通道后端与对端（本机可复现）

```bash
python3 scripts/manual_test/smtp_sink.py --port 2526 &
python3 scripts/manual_test/push_receiver.py --port 9099 &
python3 scripts/manual_test/http_object_store.py --port 9100 --api-key mt-object-key &
python3 scripts/manual_test/cas_test_idp.py --port 9200 &
# 后端
PSY_MAIL_HOST=127.0.0.1 PSY_MAIL_PORT=2526 \
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false \
PSY_NOTIFICATION_PUSH_HTTP_ENABLED=true PSY_NOTIFICATION_PUSH_HTTP_ENDPOINT_URL=http://127.0.0.1:9099/push \
PSY_EXPORT_ARTIFACT_STORAGE_MODE=HTTP_OBJECT_STORAGE PSY_EXPORT_ARTIFACT_ENDPOINT_URL=http://127.0.0.1:9100 \
PSY_EXPORT_ARTIFACT_BUCKET=psy-export-artifacts PSY_EXPORT_ARTIFACT_API_KEY=mt-object-key \
PSY_EXPORT_MAX_ATTEMPTS=3 PSY_EXPORT_INITIAL_RETRY_DELAY_SECONDS=2 PSY_EXPORT_MAX_RETRY_DELAY_SECONDS=2 \
PSY_EXPORT_PENDING_SCAN_DELAY_MS=2000 \
PSY_AUTH_SSO_CAS_ENABLED=true PSY_AUTH_SSO_CAS_SERVER_URL=http://127.0.0.1:9200/cas \
PSY_AUTH_SSO_CALLBACK_BASE_URL=http://127.0.0.1:8090 \
PSY_AUTH_SSO_FRONTEND_CALLBACK_URL=http://127.0.0.1:5173/auth/sso/callback \
AUTH_MODULE_QR_LOGIN_ENABLED=true java -jar build/libs/psy-backend-0.1.0-SNAPSHOT.jar
```

### 12.4 本轮实跑证据（`run_api_suite.py` 内直接执行）

| 用例 | 结果 |
| --- | --- |
| MT-NET-001 | 出网基线 `api.github.com=200, www.baidu.com=200` |
| MT-NET-002 | SMTP sink 收到激活邮件（subject `Activate your account`，902B）→ 链接使账号 3→4；token 重放 400 |
| MT-NET-003 | CAS：authorize 302 → IdP login 302 → callback 302 → 前端回调；ticket 换令牌 200，`/auth/me` = mtcasuser(200)；IdP 日志含 serviceValidate |
| MT-NET-005 | 推送接收端收到 `deliveryId=1566 / notificationId=722`，投递记录 `SENT/provider=http` |
| MT-NET-006 | 导出作业 PUT 997B 到对象存储（apiKeyPresent=true），应用内下载字节数一致 |
| MT-AUTH-022 | 邮件激活链接使账号进入 `PENDING_APPROVAL(4)` |
| MT-AUTH-027 | CAS 身份绑定 `CAS\|mtcasuser`；未预置身份 → HTTP 401 `notProvisioned`（不自动建号） |
| MT-SEC-014 | 应用票据重放 400、CAS 票据重放 `INVALID_TICKET`、未知票据/篡改 state 均 400 |
| MT-EXP-008 | 注入 503 PUT → `DEAD_LETTER` → 关闭注入 → `retry` → `DONE`（997B 可下载） |
| MT-RPT-012 | report 398 在 Chromium 中全部 canvas 绘制，截图 `build/reports/chart-checks/report-398.png` |
| MT-PUB-006 | 本轮发布的量表 `published_content_hash` 与两次导出头完全一致 |
| MT-AUTH-030 | QR 场景创建→扫描→确认→消费（返回令牌）→重放被拒 |

### 12.5 最终统计（411 条）

| 指标 | 数值 |
| --- | --- |
| PASS | **400** |
| FAIL | **0** |
| BLOCKED | **11**（Android 6 / 微信 2 / 外部专业与业务签署 3） |
| NOT_EXECUTED | 0 |
| 其中由 harness 每轮实跑 | **268**（此前 250；新增 NET/SSO/邮件/死信/图表等） |
| 接口覆盖 | 168/168（自动 168） |
| 路由覆盖 | 23/23 |
| 逐页三语 | 22 页 × 3 语；findings 58 条全部为 `identical-across-locales` 业务数据（用户名、租户/组名、事件码、存储模式、量表题干与选项） |
| 静态硬编码扫描 | `hardcoded CJK literals: 0` |
| 后端回归 | `cleanTest test` 503 用例 0 失败 / 0 错误（16 skipped） |
| 前端回归 | `tsc -b` 无错、`vitest run` 131 通过、`npm run build` 成功 |
| 文档一致性 | `generate_code_docs.py check` 通过 |

### 12.6 覆盖率审计口径修正

`coverage_audit.py` 原先只做「归一化后字符串精确匹配」，导致 8 个接口只能记成「仅手顺声明」。现改为
**方法一致 + 路径逐段匹配**：设计文档中的占位段（`{id}`/`{reviewType}`/`{provider}`）接受具体值，例如
`POST /api/v1/scales/234/dimensions/99999999` 记为覆盖 `POST /api/v1/scales/{id}/dimensions/{dimensionId}`；
用例侧由变量拼出的 `*` 必须与占位段对齐，因此不会出现「没请求却记账」。同时补上 `for path in (...)` 这类
循环字面量路径的识别（MT-API-015）。结果：**168/168 全部由自动用例真实请求**（其中 4 个走占位段匹配、4 个走循环字面量）。

### 12.7 G-9（P2，数据一致性）：修复前发布的量表，发布哈希与导出哈希永久不一致

| 项 | 内容 |
| --- | --- |
| 现象 | 整跑中 `MT-PUB-006` FAIL：库中 `published_content_hash=1ccad65b…` ≠ 导出响应头 `X-Scale-Content-Hash=df1e9236…`（scale 235） |
| 调查 1 | `information_schema.tables` 中 `psy_scale_visualization_config` 同时存在于 `public` 与 `mt_dbg2`，旧版 `VisualizationRepository.hasTable()` 要求表名计数 `== 1`，因此返回 false |
| 调查 2 | 按 `ScaleContentFingerprintService.calculate()` 用同一份数据复算：**不含**可视化配置 → `1ccad65b24c9b555…`（与库中发布哈希逐字符一致）；**含**可视化配置 → `df1e923637a5db09…`（与当前导出头逐字符一致） |
| 结论 | scale 235 是 G-7 修复前（23:29）发布的：发布时 `hasTable()` 误判导致图表配置未进入哈希。修复并重启后重新计算的哈希自然不同——属「缺陷修复导致的历史哈希失配」，不是新的不确定性（同一版本内两次导出结果一致） |
| 处理 1 | 用例：MT-PUB-006 不再依赖历史 PUBLISHED 行，改为本轮自行发布带图表配置的量表并断言「发布哈希 == 两次导出哈希」 |
| 处理 2 | 产品：`ScalePackageExportService.export()` 在 `PUBLISHED` 且 `published_content_hash` ≠ 当前哈希时输出 WARN，不再静默（实测日志：`scale 235 is PUBLISHED but its content hash no longer reproduces the stored value (published=1ccad65b… current=df1e9236…)`） |
| 处理 3 | 单元测试：新增 `ScaleContentFingerprintServiceTest`（4 条：可重复且与集合顺序无关、可视化配置参与哈希、治理包行参与哈希、数值格式归一） |
| 残余风险 | 修复前发布的历史量表（本机 scale 235）必须重新发布新版本才能得到与当前构建一致的发布哈希；`mt_dbg2` 这类同名 schema 只应存在于调试库，正式环境需要保证 search_path 唯一 |
