# 全量手动测试手顺（全功能 + 全业务 + 全网络）

> 版本：2026-09-19 重设版。本文档替代 `doc/30-manual-test-procedure.md` 作为**全量**执行标准；
> doc/30 保留为 324 条历史用例与逐条步骤的详细说明。两者共同构成 case catalog：
> `python3 scripts/manual_test/build_catalog.py` 会把两边的用例表合并为
> `build/reports/manual-test/cases.json`（harness 的执行清单）。

## 0. 适用范围与不变量

- **全功能**：`doc/13-api-design-detailed.md` 中的每一个业务/认证接口都必须有至少一个用例（API 用例、UI 用例或 NET 用例），由 `scripts/manual_test/coverage_audit.py` 机械核对；前端 `route-config.tsx` 的每条路由必须在手顺中被引用。
- **全业务**：§3 的端到端场景覆盖量表、任务、作答、评分、报告、预警、干预、复测、预约、通知、导出、审计、多角色、多租户、三语。
- **全网络**：所有用例通过真实 HTTP（浏览器 → Vite/Ingress → 后端 → PostgreSQL）执行；外部集成（SMTP/微信/SSO/推送/对象存储）通过真实网络对端执行，未配置时必须验证“失败关闭”（返回明确错误码而不是 500 或静默成功），禁止用 mock 冒充通过。

## 1. 全功能 API 套件（MT-API）

| 编号 | 优先级 | 端点 | 目的与步骤 | 期望结果 |
| --- | --- | --- | --- | --- |
| MT-API-001 | P0 | `GET /api/v1/directory/users`; `GET /api/v1/directory/groups`; `GET /api/v1/directory/tasks`; `GET /api/v1/directory/scales` | 用 `assessor` 依次调用四个目录接口；再用 `activeOnly=true` 拉取用户；用 `respondent` 调 users | 全部 200 且返回本租户数据；`activeOnly` 只返回 `ENABLED`；`respondent` 403 |
| MT-API-002 | P0 | `GET /api/v1/appointments` | 管理端分页查询；用 `userId=6` 过滤；用 `respondent` 调同一路径 | 分页结构含 `list/page/size/total`；过滤结果全部属于该用户且带被预约人姓名；`respondent` 403 |
| MT-API-003 | P0 | `GET /api/v1/warnings/assignee-options`; `POST /api/v1/warnings/{id}/assign` | 取责任人候选；对一条 `PENDING` 预警指派；回查列表与 `psy_warning_assignment` | 候选人来自本租户在职员工；指派返回 `ASSIGNED`；列表出现责任人姓名；数据库存在对应 assignment 行 |
| MT-API-004 | P0 | `GET /api/v1/statistics/dashboard` | `assessor` 调用；用 `Accept-Language: ja-JP` 再调；用新建的 `SCHOOL_LEADER` 账号调用 | 200 且包含 overviewCards；日语请求返回日语卡片标题；校领导可访问仪表盘 |
| MT-API-005 | P0 | `POST /api/v1/exports/reports` | 管理端导出报告（TEXT）；`respondent` 导出自己的报告；`respondent` 导出他人报告 | 前两者 200 且带内容；跨用户导出返回 403/404（所有权校验） |
| MT-API-006 | P1 | `GET /api/v1/exports/reports/jobs/{jobId}/download` | 取最近一个 `DONE` 作业并下载 | 200 且字节数 > 0；无 `DONE` 作业时记为 BLOCKED 并注明需先执行 EXP 模块 |
| MT-API-007 | P0 | `POST /api/v1/my/notifications/{id}/read` | 用 `respondent` 找一条未读通知并标记已读；回查列表与投递表 | 返回 `readFlag=true`；数据库 `read_flag=t`；列表同步 |
| MT-API-008 | P1 | `GET /api/v1/reports/by-result/{resultId}` | 取最近一条已提交答卷的 resultId 查询报告 | 200 且返回 `reportId` |
| MT-API-009 | P1 | `POST /api/v1/tasks/{id}` | 建任务→更新名称/时间；再用空名称触发校验 | 更新落库；非法请求 400 且失败关闭 |
| MT-API-010 | P1 | `GET /api/v1/scales/import-template` | 下载导入模板 | 200，xlsx 或 octet-stream，字节数 > 1000 |
| MT-API-011 | P1 | `GET /api/v1/scales/{scaleId}/package` | 取已发布量表的治理包 | 200 且返回非空快照 |
| MT-API-012 | P1 | `GET /api/v1/scales/{scaleId}/publication/history`; `.../history/reviews`; `.../history/runs` | 依次查询三个历史游标接口 | 均为 200 且带 `items` 分页结构 |
| MT-API-013 | P1 | `POST /api/v1/scales/{scaleId}/publication/reviews/{reviewType}` | 用无效 reviewToken 提交专业评审 | 4xx（400/403/404/409）且失败关闭，不得 500 |
| MT-API-014 | P1 | `POST /api/v1/scales/{id}/dimensions/batch`; `POST /api/v1/scales/{id}/dimensions/{dimensionId}` | 空批量与不存在的维度 ID | 均为 4xx 校验/未找到，失败关闭 |
| MT-API-015 | P0 | `GET /auth/roles`; `GET /auth/permissions`; `GET /auth/groups`; `GET /auth/tenants`; `GET /auth/users` | 用 `sysadmin` 依次查询；用 `respondent` 查询 users | sysadmin 全部 200；respondent 403 |
| MT-API-016 | P0 | `GET /auth/users/{userId}/sessions`; `POST /auth/users/{userId}/sessions/{sessionId}/revoke` | 新建临时用户并登录；查询其会话；撤销该会话 | 会话列表含新登录；撤销成功且可复查 |
| MT-API-017 | P1 | `GET /auth/users/{userId}/devices` | 查询临时用户设备 | 200 且返回数组（可为空） |
| MT-API-018 | P1 | `GET /auth/security-events` | sysadmin 查询安全事件 | 200 且非空，包含多类事件 |
| MT-API-019 | P1 | `GET /auth/admin/ping` | sysadmin 与普通用户分别调用 | sysadmin 返回 `{ok:true}`；respondent 401/403 |
| MT-API-020 | P0 | `POST /auth/password/reset` | 对临时用户重置密码→用新密码登录→恢复默认密码 | 新密码登录成功；旧密码失效；恢复后仍可登录 |
| MT-API-021 | P1 | `POST /auth/qr/cancel` | 用一个不存在的 sceneCode | 4xx 且失败关闭 |
| MT-API-022 | P1 | `GET /auth/email-verify`; `POST /auth/sso/token` | 用无效 token/ticket 调用 | 均 4xx 且失败关闭 |
| MT-API-023 | P1 | `GET /auth/sso/{provider}/authorize`; `GET /auth/sso/{provider}/callback` | 在未配置 IdP 的环境调用 OIDC authorize/callback | authorize 非 5xx（失败关闭或重定向）；callback 4xx/5xx 之一但带明确错误；配置 IdP 后按 §4 MT-NET-003 执行正路径 |
| MT-API-024 | P1 | `POST /auth/users/{userId}/roles` | 给临时用户追加 `COUNSELOR` 角色 | 200 且数据库角色行包含 COUNSELOR |
| MT-API-025 | P2 | `POST /wechat/jssdk/config` | 传入页面 URL | 200 且返回 JSON map；未配置微信时为空 map（不得 500） |
| MT-API-026 | P2 | `GET /wechat/portal`; `POST /wechat/portal` | 未配置微信时调用 | 非 5xx，失败关闭 |
| MT-API-027 | P2 | `POST /api/v1/wechat/menu/sync` | 普通管理员调用（应 403）；`sysadmin` 调用 | 角色门生效；未配置微信服务时返回明确消息而不是 500 |

> 说明：MT-API-006/023 及 §4 的微信/SSO 正路径需要真实外部条件（已完成的导出作业、IdP、微信凭据）。
> 未满足时 harness 记录 `BLOCKED` 并附原因，不允许记为 PASS。

## 2. 全功能 UI 套件（MT-UI）

每页在三种语言下各执行一次（共 22 页面 × 3 语言），自动化入口：
`cd admin-web && npx playwright test e2e/i18n-page-sweep.spec.ts`

| 编号 | 页面/路由 | 目的与步骤 | 期望结果 |
| --- | --- | --- | --- |
| MT-UI-001 | `/home`、`/my/tasks`、`/my/reports`、`/my/profile` | 被测者登录后逐页浏览并切换三语 | 页面标题、统计、按钮、空状态本地化；任务/报告数据正确 |
| MT-UI-002 | `/my/tasks/{id}` | 打开一条未过期任务的作答页 | 题干、选项、进度、草稿保存、提交按钮本地化；过期任务提示 `TASK_EXPIRED` |
| MT-UI-003 | `/dashboard` | 三语浏览仪表盘 | 卡片、图表标题、最近预警/报告表本地化；快捷入口可用 |
| MT-UI-004 | `/scales`、`/scale-publication`、`/scale-governance` | 量表维护、发布就绪、治理录入 | 量表选择器可用；状态/计分方式/治理下拉本地化；无裸枚举码 |
| MT-UI-005 | `/tasks`、`/warnings`、`/safety-response-policies` | 任务、预警、安全策略页 | 任务模式/状态、预警等级/状态/优先级、责任人列、逾期标记本地化 |
| MT-UI-006 | `/group-reports`、`/user-reports` | 群体/个体报告 | 选择器可服务端检索；分页显示总数；风险等级/报告类型本地化 |
| MT-UI-007 | `/appointments` | 管理端“全部预约”与用户端“我的预约” | 筛选/分页/代客预约/代取消可用；咨询记录按钮状态约束生效 |
| MT-UI-008 | `/exports-center` | 导出中心 | 最近作业行内下载；保留期提示与后端配置一致 |
| MT-UI-009 | `/notifications` | 管理端与用户端通知页 | 管理端“我的通知/通知运维”页签；设备与推送给所有角色；通知文本跟随当前语言 |
| MT-UI-010 | `/user-admin`、`/pending-registrations` | 用户管理与待审核注册 | 角色、状态、审核操作本地化；可跳转审计 |
| MT-UI-011 | `/auth-audit`、`/session` | 认证审计与我的会话 | 深链可直接打开（不被 `/auth` 代理吞掉）；状态/结果本地化；分页与跳页可用 |
| MT-UI-012 | `/reports/{id}` | 报告详情（staff 与 respondent） | 报告内容、导出、打印本地化；被测者可导出自己的报告 |

## 3. 全业务场景套件（MT-BIZ）

| 编号 | 优先级 | 场景（端到端） | 期望结果/证据 |
| --- | --- | --- | --- |
| MT-BIZ-001 | P0 | 量表全生命周期：Excel 导入 → 治理录入（来源/授权/质量策略/算法/常模）→ Golden Case 运行 → 双人评审 → 发布 | 每一步有落库与审计；未双人评审不可发布；发布后版本快照不可变 |
| MT-BIZ-002 | P0 | 测评任务闭环：建任务 → 分配用户/组 → 被测者作答（含跳题/多选上限/时限）→ 提交 → 评分 → 报告生成 | 任务状态、答卷状态、结果、报告、通知链路一致；重复提交幂等 |
| MT-BIZ-003 | P0 | 风险预警闭环：高风险作答 → 预警生成 → 接单 → 指派 → 干预记录 → 结案（含复测） | `deadline_time`/优先级/责任人正确；策略缺失必须阻断结案；复测任务生成并通知 |
| MT-BIZ-004 | P1 | 预约与咨询：创建排班 → 用户预约/管理端代约 → 咨询记录（仅已完成或已到时间）→ 取消/完成 | 归属被预约人；名额与并发不超卖；记录与预约关联 |
| MT-BIZ-005 | P1 | 通知与投递：任务/报告/预警通知生成 → 应用内已读 → 投递失败 → 重试/死信 | 通知按查看者语言渲染；投递状态可追溯；重试幂等 |
| MT-BIZ-006 | P1 | 报告导出与审计：导出报告（同步/异步）→ 作业下载 → 审计事件 | 导出受所有权与租户约束；审计记录含操作人/格式/结果 |
| MT-BIZ-007 | P0 | 多角色与权限：单角色/多角色账号（含 SCHOOL_LEADER）逐页访问 | 多角色取并集；校领导仅仪表盘+群体报告；越权 403 |
| MT-BIZ-008 | P0 | 多租户隔离：DEFAULT/CAMPUS_DEMO/ENTERPRISE_DEMO 三类账号交叉访问 | 数据、会话、导出、预警、预约全部按租户隔离；全局管理员访问写 `PSY_TENANT_SCOPE_OVERRIDE` 审计 |
| MT-BIZ-009 | P0 | 三语全链路：同一业务在 zh/ja/en 下各执行一次（作答→报告→通知→导出） | 报告/通知/导出文件语言与提交语言一致；页面无串语言（MT-UI/MT-I18N-009） |
| MT-BIZ-010 | P1 | 匿名测评：匿名提交高风险 → 统计 → 预警规则 | 匿名结果不生成个人预警、不泄露 token；群体统计包含该数据 |

## 4. 全网络与外部集成套件（MT-NET）

### 4.1 外网连通性基线

```bash
# 出网检查（任一可用即代表全网络环境就绪；CI 内网可跳过 google）
curl -s -o /dev/null -w 'github:%{http_code}\n' --max-time 6 https://api.github.com
curl -s -o /dev/null -w 'baidu:%{http_code}\n'  --max-time 6 https://www.baidu.com
# 若需要代理：export HTTPS_PROXY=http://<proxy>:<port>（后端与浏览器都要设置）
curl -s -o /dev/null -w 'idp:%{http_code}\n' --max-time 8 "$PSY_AUTH_SSO_OIDC_ISSUER_URI/.well-known/openid-configuration"
```

| 编号 | 优先级 | 通道 | 配置（环境变量） | 验证步骤 | 期望 |
| --- | --- | --- | --- | --- | --- |
| MT-NET-001 | P0 | 外网连通 | 无（可选 `HTTPS_PROXY`） | 执行 4.1 命令 | 至少 github/baidu 返回 200；后端日志无 DNS/超时错误 |
| MT-NET-002 | P1 | SMTP 邮件 | `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`（对应 `spring.mail.*`） | 用外部注册入口提交注册 → 查收激活邮件 → 打开激活链接 | 邮件真实送达；`GET /auth/email-verify?token=…` 把状态 3→4；未配置 SMTP 时注册流程必须走 no-op 并留下 `(MT mail noop)` 证据 |
| MT-NET-003 | P1 | SSO (OIDC/CAS) | `PSY_AUTH_SSO_OIDC_ISSUER_URI`、`..._CLIENT_ID/SECRET`、`PSY_AUTH_SSO_OIDC_AUTHORIZATION_ENDPOINT`、`..._TOKEN_ENDPOINT`、`PSY_AUTH_SSO_CALLBACK_BASE_URL`、`PSY_AUTH_SSO_FRONTEND_CALLBACK_URL` | 打开 `/auth/sso/oidc/authorize` → 完成 IdP 登录 → 回调 `/auth/sso/{provider}/callback` → 前端 `/auth/sso/callback?ticket=…` → 用 ticket 换 token | 登录成功、ticket 一次性且 2 分钟过期；重复使用 ticket 失败；回调深链不被 API 前缀吞掉（MT-I18N-011） |
| MT-NET-004 | P1 | 微信生态 | `PSY_AUTH_WECHAT_APP_ID/SECRET`、`PSY_AUTH_WECHAT_*`（OAuth/JS-SDK/菜单） | 微信内打开 `/auth/wechat` 完成 OAuth；`POST /wechat/jssdk/config` 获取签名；`POST /api/v1/wechat/menu/sync` 同步菜单；`GET/POST /wechat/portal` 验证门户回调 | OAuth 建号/绑定成功；JS-SDK 返回 appId/nonceStr/signature；菜单同步返回成功；未配置时全部失败关闭 |
| MT-NET-005 | P1 | 推送通道 | `PSY_NOTIFICATION_PUSH_HTTP_ENDPOINT_URL`（真实或内网可达的接收端） | 触发一条通知 → 接收端收到 POST → 回报 delivered/clicked 回调 | 投递记录状态随回调更新；失败进入 FAILED 并可重试；未配置时 PUSH 渠道保持 PENDING/FAILED 且不阻塞其他渠道 |
| MT-NET-006 | P1 | 对象存储导出 | `PSY_EXPORT_ARTIFACT_MODE=S3`、`..._ENDPOINT_URL`、`..._BUCKET`、`PSY_EXPORT_ARTIFACT_ACCESS_KEY/SECRET_KEY`（或本地 MinIO） | 导出报告 → 检查对象存储出现对象 → 通过作业下载接口取回 | 对象可下载且校验一致；`/exports/reports/storage` 的 mode/bucket 与实际一致；未配置时回落 LOCAL_PATH 并有提示 |

### 4.2 本机可复现的“真实网络对端”配方（无外部凭据也能跑正路径）

```bash
# SMTP：真实 SMTP 会话，并把每封邮件（含激活链接）写成 JSONL 供 harness 回读
python3 scripts/manual_test/smtp_sink.py --port 2526

# 推送：真实 HTTP 接收端（记录 JSON 体到 build/reports/manual-test/push-receiver.jsonl）
python3 scripts/manual_test/push_receiver.py --port 9099

# 对象存储：真实 HTTP 对象存储（PUT/GET/DELETE + X-Api-Key，落盘到 build/reports/manual-test/object-store/）
#   POST /__control {"failPut":true|false} 可注入 PUT 失败，用于 MT-EXP-008 的死信演练
python3 scripts/manual_test/http_object_store.py --port 9100 --api-key mt-object-key

# 以全通道启动后端（自助注册保持开启以执行邮件激活；QR 登录开启以执行扫码用例）
PSY_MAIL_HOST=127.0.0.1 PSY_MAIL_PORT=2526 \
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false \
PSY_NOTIFICATION_PUSH_HTTP_ENABLED=true PSY_NOTIFICATION_PUSH_HTTP_ENDPOINT_URL=http://127.0.0.1:9099/push \
PSY_EXPORT_ARTIFACT_STORAGE_MODE=HTTP_OBJECT_STORAGE PSY_EXPORT_ARTIFACT_ENDPOINT_URL=http://127.0.0.1:9100 \
PSY_EXPORT_ARTIFACT_BUCKET=psy-export-artifacts PSY_EXPORT_ARTIFACT_API_KEY=mt-object-key \
PSY_EXPORT_MAX_ATTEMPTS=3 PSY_EXPORT_INITIAL_RETRY_DELAY_SECONDS=2 PSY_EXPORT_MAX_RETRY_DELAY_SECONDS=2 \
PSY_EXPORT_PENDING_SCAN_DELAY_MS=2000 \
AUTH_MODULE_QR_LOGIN_ENABLED=true \
java -jar backend/build/libs/psy-backend-0.1.0-SNAPSHOT.jar

# 导出入死信（故障注入 + 快速重试）
PSY_EXPORT_ARTIFACT_ENDPOINT_URL=http://127.0.0.1:9199 PSY_EXPORT_MAX_ATTEMPTS=3 \
PSY_EXPORT_INITIAL_RETRY_DELAY_SECONDS=2 PSY_EXPORT_MAX_RETRY_DELAY_SECONDS=2 \
PSY_EXPORT_PENDING_SCAN_DELAY_MS=2000 java -jar ...
# → 作业进入 DEAD_LETTER 后恢复存储并重放：
curl -X POST http://127.0.0.1:8090/api/v1/exports/reports/jobs/{jobId}/retry -H "Authorization: Bearer $TOKEN"
# 本机等价做法（无需重启后端）：对 http_object_store.py 打开故障注入 → 作业 DEAD_LETTER → 关闭注入 → retry
curl -X POST http://127.0.0.1:9100/__control -d '{"failPut":true}'

# 报告图表（雷达/条形/风险分布）截图证据
python3 scripts/manual_test/seed_chart_scale.py        # 输出 reportId（需 REPORT_DETAIL 作用域可视化配置）
cd admin-web && PSY_E2E_CHART_REPORT_ID=<reportId> npx playwright test e2e/report-charts.spec.ts
# MT-RPT-012 已在 run_api_suite 内直接执行上述浏览器渲染（要求 5173 已启动）

# 开关类验证
PSY_AUTH_SELF_REGISTRATION_ENABLED=false  # POST /auth/register → 400 当前未开放自助注册
AUTH_MODULE_QR_LOGIN_ENABLED=true         # /auth/qr/scene→scan→confirm→poll 全链路

# SSO：真实协议 CAS 测试 IdP（无外部 IdP 凭据时使用；生产环境替换为学校 IdP）
python3 scripts/manual_test/cas_test_idp.py --port 9200
PSY_AUTH_SSO_CAS_ENABLED=true PSY_AUTH_SSO_CAS_SERVER_URL=http://127.0.0.1:9200/cas \
PSY_AUTH_SSO_CALLBACK_BASE_URL=http://127.0.0.1:8090 \
PSY_AUTH_SSO_FRONTEND_CALLBACK_URL=http://127.0.0.1:5173/auth/sso/callback java -jar ...
# 1) GET /auth/sso/cas/authorize → 302 IdP login → 2) 302 回 /auth/sso/cas/callback?ticket=ST-…
# 3) 应用 serviceValidate 成功后 302 前端 /auth/sso/callback?ticket=… → 4) POST /auth/sso/token 换令牌
# 注意：SSO 不自动建号；未预置账号返回 401 auth.sso.user.notProvisioned（首次登录会绑定身份到同名/同邮箱账号）
```

> 注意：`MT-NET-004`（微信正路径）仍需真实公众号凭据；`MT-SEC-009` 需要 tenantless 超管账号；
> `MT-ANS-016/MT-RPT-013/MT-SCORE-016` 需要外部专业/业务签署。上述四项在无凭据环境下只能验证失败关闭路径。

## 5. 执行方式与判定

```bash
# 1) 生成执行清单（doc/30 + 本文档）
python3 scripts/manual_test/build_catalog.py

# 2) 覆盖率审计：接口 168/168、路由全部被引用、并列出实现缺口
python3 scripts/manual_test/coverage_audit.py

# 3) API/DB 全量执行（真实 HTTP + psql 校验）
cd backend && nohup java -jar build/libs/psy-backend-0.1.0-SNAPSHOT.jar &   # 8090
cd ../admin-web && npm run dev &                                            # 5173
python3 scripts/manual_test/run_api_suite.py            # 全部注册用例
python3 scripts/manual_test/run_api_suite.py --modules API   # 本次新增的 MT-API 套件

# 4) 逐页三语运行时侦测
cd admin-web && npx playwright test e2e/i18n-page-sweep.spec.ts
python3 scripts/i18n_source_audit.py

# 5) 汇总证据
python3 scripts/manual_test/consolidate_evidence.py
python3 scripts/manual_test/consolidate_full_suite.py     # UI/I18N/BIZ/NET/FE 证据合并
```

**判定规则**

1. `coverage_audit.py` 必须报告：接口 168/168 已覆盖，无未引用路由。判定口径为「方法一致 + 路径逐段匹配」，
   设计文档中的占位段（`{id}`、`{reviewType}`、`{provider}`）接受具体值，因此用例实际请求
   `POST /api/v1/scales/234/dimensions/99999999` 即视为覆盖 `POST /api/v1/scales/{id}/dimensions/{dimensionId}`；
   用例侧由变量拼出的 `*` 必须与占位段对齐。若某接口只有手顺声明而无自动请求，审计会把它列进
   `procedure-only` 并在矩阵中标出，必须补齐自动用例或写明无法自动化的原因。
2. `run_api_suite.py` 的 FAIL 必须为 0；`BLOCKED` 必须逐条写明外部条件（凭据、IdP、对象存储、推送接收端），且不得因阻塞跳过 §3 的 P0 场景。
3. 逐页三语扫描的 `raw-i18n-key`、`enum-code`、`broken-value`、`missing-route-label`、`empty-page`、`api-error`、`console-error` 必须为 0；剩余 `identical-across-locales` 必须是业务数据。
4. 执行记录写入 `doc/process/10-manual-test-execution-<date>.md`，含通过/失败/阻塞明细、测试数据前缀与清理方式、证据路径。
5. §4 的外部通道用例（MT-NET-001/002/003/005/006、MT-AUTH-022/027/030、MT-SEC-014、MT-EXP-008、MT-RPT-012）
   由 `run_api_suite.py` 直接执行：对端未启动或后端未配置对应通道时记 `BLOCKED` 并写明缺什么，
   禁止用 mock 或历史证据把它记成 PASS。`consolidate_full_suite.py` 只允许为「本轮未执行」的用例补写
   环境证据；由 harness 实跑的 PASS 保留原始明细，出现 FAIL 直接终止汇总。
6. `MT-PUB-006` 必须用本轮构建自行发布量表（含图表配置）来验证「发布哈希 = 导出哈希」；
   历史 PUBLISHED 行可能携带修复前（G-9）算出的旧哈希，不得作为该用例的判定依据。

## 6. 与旧手顺的关系

| 文档 | 定位 |
| --- | --- |
| `doc/30-manual-test-procedure.md` | 324 条历史用例（逐条步骤、SQL、附录），继续作为细则与回归用例库 |
| `doc/31-manual-test-procedure-full.md`（本文） | 全量执行标准：功能/业务/网络三套件的入口、覆盖判定与执行命令 |
| `doc/manual-test/coverage-matrix.md` | 自动生成的接口 ↔ 用例覆盖矩阵 |
| `doc/manual-test/case-registry.json` | 自动生成的机器可读注册表（接口、路由、用例映射） |
