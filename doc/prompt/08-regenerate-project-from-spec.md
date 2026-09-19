# 08 工程重建 Prompt（语言无关规格 → 目标语言实现）

用途：把本仓库的**业务规格与工程约束**完整交给 AI，用于在新语言（默认为 Go）中重建同等能力的系统。

使用方式：

1. 把本文档整篇作为系统提示/任务描述交给执行方。
2. 按 §19 的阶段顺序执行，每阶段结束必须产出 §18 要求的证据。
3. 若只重建部分能力（例如仅后台任务），显式声明"仅实现 §10 与 §12"这类范围裁剪，其余章节作为接口约束而非交付范围。

**本文档是规格，不是现状描述。** 现状事实来源是仓库代码与 `README.md`；本文档描述重建后必须达到的能力与不变式。

### 可直接使用的启动指令（复制给执行方）

```text
你要在 Go 中重建一套心理测评与预警系统。附件《工程重建 Prompt》是唯一规格来源：
§1-§9 是不可协商的业务与不变式契约，§10-§15 是运行时与部署契约，
§16-§18 是验收门槛，§19 是实施顺序。请遵守：

1. 先输出你打算实现的阶段范围与对应验收清单，等我确认后再写代码。
2. 不得违反 §17 禁止事项；不得把技术通过表述为正式支持或临床可用。
3. 每完成一个阶段，必须给出 §16 要求的可验证证据（命令 + 退出码 + 计数 + 产物路径）。
4. 规格未覆盖的细节，选择与 PostgreSQL/HTTP 标准一致的最保守做法，并显式列出假设。
```

---

## 1. 系统定位与范围

高校心理中心 / 辅导员体系 / 企业 EAP 场景的心理测评与预警系统。核心闭环：

```
量表建模 → 测评任务下发 → 被测者作答（Web/Android）→ 评分与质量判定 → 报告生成（三语，多格式）
   → 预警识别与分派 → 干预/咨询 → 复测任务 → 群体统计 → 导出与审计
```

用户角色：平台管理员、组织管理员、测评管理员、心理咨询师（COUNSELOR）、学校领导（只读统计）、被测者（USER）。

**明确非目标**（重建时不得擅自扩范围）：

- 不含 iOS 原生端与微信小程序。
- 不含真实第三方 Push 厂商 SDK 深度集成（仅 HTTP/FCM 网关契约）。
- 不含对象存储厂商 SDK 深度适配（仅 S3 兼容 HTTP + 本地目录两种模式）。
- 不承担量表版权、常模、临床切分点的**授权与专业审校**：这些是外部治理阻塞项，系统只负责记录状态与证据。

## 2. 技术与交付约束

### 2.1 目标技术栈（Go 版本）

| 层 | 约束 |
| --- | --- |
| 后端 | Go 1.22+；HTTP 采用标准库 `net/http` + 轻量路由（chi/echo 任一）；**不使用 ORM**，沿用显式 SQL（`pgx`/`sqlx`） |
| 数据库 | PostgreSQL 14+（生产 13 已验证可用）；结构由**迁移工具管理**（golang-migrate / goose，语义见 §4.3） |
| 缓存/锁 | Redis 仅用于跨实例调度锁，不得作为业务状态存储 |
| 鉴权 | JWT（HS256，密钥 ≥256 bit）、Argon2 口令散列、会话与设备治理；认证域可独立部署为服务，但契约见 §5 |
| 报告生成 | PDF（支持中日文 CJK 字形）+ Word(.docx) + 纯文本；Excel 导入/导出 |
| 前端 | React 19 + antd 5 + React Query + ECharts + Vite；三语（zh-CN / ja-JP / en-US） |
| Android | Kotlin + Compose，仅被测者端（登录、任务、作答、报告、预约、通知） |
| 可观测 | 指标（Prometheus 文本格式）、结构化日志、trace id 贯穿、6 类业务队列指标 |

### 2.2 兼容性硬约束

重建后必须保持：

- **HTTP 契约**：§5 的 109 个端点路径、方法与权限语义。
- **数据库结构**：§4 的表与关键约束（可由迁移重建，历史迁移语义必须等价）。
- **错误码**：§11 的业务码（大写蛇形，返回给客户端的统一错误响应）。
- **多租户与治理不变式**：§9 全部条款，一条都不能放宽。

## 3. 领域模型

### 3.1 量表域（`psy_scale*`）

- **Scale**：`scale_code` + `version_no`，按租户隔离（同一代码在不同租户各自一份）；`status ∈ {DRAFT, PUBLISHED}`；`score_method ∈ {SIMPLE_SUM, REVERSE_SUM, WEIGHTED_SUM, AVERAGE, WEIGHTED_AVERAGE}`；`score_coefficient > 0`。
- 同一量表**只能有一个当前版本**（部分唯一索引保证）。
- **Dimension**（维度，含权重）、**Question**（题目，7 种题型）、**Option**（选项，含分值/排他/其他选项标记）、**ResultRule**（结果规则：区间 → 标题/描述/建议/风险等级）、**HighRiskRule**（高风险规则）、**ValidityRule**（效度规则）、**Norm**（常模：均值/标准差/T 分）、**QualityPolicy**（质量策略）、**VisualizationConfig**（可视化配置）。
- **Governance**（治理）：`copyright_status`、`authorization_status`、`rights_holder`、`authorization_scope`、`authorized_languages`、`target_population`、`non_diagnostic_statement`、`governance_status`。**导入时必须强制 `PENDING_REVIEW` / `DRAFT`**（见 §8.3）。
- **Translation**：量表/维度/题目/选项/结果规则/高风险规则各自的多语言表，语言集合固定为 `zh-CN`、`ja-JP`、`en-US`，每条含 `review_status`。
- **GoldenCase**（黄金用例）：输入/期望输出/内容指纹/版本号/创建人/审批人；**GoldenCaseRun**：运行结果与差异；**PublicationReview**：发布评审证据。

### 3.2 测评域（`psy_assessment*`）

- **Task**：`task_mode`（SCREENING 等）、匿名开关、允许保存/超时提交/重考开关、起止时间、`status ∈ {DRAFT, PUBLISHED, IN_PROGRESS, COMPLETED, CLOSED}`、锁定的量表版本（`scale_version_no` / `scale_version_group_id`）。
- **TaskAssignment**：按用户或组织下发。
- **AnswerSheet**：`answer_status ∈ {DRAFT, SUBMITTED, ...}`；必须记录提交幂等令牌、质量结果（有效题数/缺失数/质量状态 `VALID|INVALID|NOT_EVALUATED`）、跳过题集合、匿名聚合租户/组织。
- **AnswerItem**：逐题作答；`answer_value` 数值型语义 + `answer_text` 文本型语义（`TIME` 以 `HH:mm` 存 `answer_text`）。
- **Result**：总分、标准分、T 分、Z 分、常模码、风险等级、高风险标记与规则码、**评分轨迹 JSON**（含中间量、维度分、受限 profile）。结果**追加写入、不可覆盖**（重评产生新行，旧行保留 `is_current=false`）。

### 3.3 预警与干预域（`psy_warning*` / `psy_intervention*` / `psy_safety_response_policy`）

- **WarningRecord**：`warning_level ∈ {LOW, MEDIUM, HIGH, CRITICAL}`、`warning_priority ∈ {P0, P1, P2}`、`status ∈ {PENDING, ASSIGNED, PROCESSING, CLOSED}`、外部可见原因 + **内部证据 JSON**、截止时间、首次响应时间、升级时间/次数。
- **WarningAssignment** 与状态流水；**InterventionRecord**（`current_status`、计划文本、结案摘要、是否需要复测、关联复测任务）。
- **SafetyResponsePolicy**（安全响应策略）：按风险类别一条生效策略；字段含危机资源、紧急联系人、处置步骤；状态 `DRAFT → APPROVED/RETIRED`。

### 3.4 支撑域

- 通知：**Notification**（消息）、**NotificationPolicy**（渠道策略）、**NotificationDelivery**（投递流水：渠道、状态、尝试次数、下次重试时间、租约、死信原因）。
- 导出：**ExportJob**（报告/群体报告导出：格式、状态、尝试次数、租约、文件元数据）。
- 预约与咨询：**CounselorSchedule**、**AppointmentRecord**（`appointment_status ∈ {CREATED, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW}`）、**CounselingRecord**。
- 审计与设备：登录日志、审计事件（含跨租户访问、量表治理动作、预警动作）、用户设备。

## 4. 数据库契约

### 4.1 规模与分组（重建后应达到）

共 **61 张表**（2026-09-19 在 Flyway V1–V28 迁移后的 PostgreSQL 实测）：`psy_scale*`（23）、`psy_assessment*`（6）、`psy_warning*`（5）、`psy_notification*`（3）、`psy_intervention*`（2）、`psy_export_job`/`psy_report`/`psy_safety_response_policy`/`psy_appointment_record`/`psy_counseling_record`/`psy_counselor_schedule`/`psy_user_device`（各 1），`sys_*`（15）。精确列、约束与索引见 [10-database-table-design.md](../10-database-table-design.md)。

### 4.2 关键约束（必须在迁移中体现）

1. **租户归属**：所有直接租户表 `tenant_id` 非空；子表通过父表继承，禁止孤儿行（有专门预检脚本）。
2. **单一生效策略**：同一 `(tenant_id, risk_category)` 只能有一条 `active_flag=true`。
3. **安全策略双人分离**：`active_flag=true` 必须满足 `status='APPROVED'` 且 `approved_by`、`professional_reviewer_id`、`professional_reviewed_at` 均非空；且 `professional_reviewer_id` 与审批人必须是不同用户（应用层 + 数据库约束双重保证）。
4. **结果追加语义**：结果表禁止 UPDATE 覆盖历史；`is_current` 唯一指向当前结果。
5. **质量留痕**：答卷与结果均有质量状态、有效题数、缺失数相关约束。
6. **评分轨迹**：结果表提供评分轨迹 JSON 且带约束校验。
7. **时间题与跳过规则**：`skip_rules_json` 持久化声明式分支（`whenQuestionNo` + `whenOptionCode` + `skipQuestionNos`），读取时必须**失败关闭**（畸形/失效规则不得静默降级为空规则）。

### 4.3 迁移治理语义

- 迁移集合 `V1..V28`：V1 为**冻结基线**（含认证域与业务域全部建表），后续版本只增不改。
- 已执行迁移**禁止修改**；变更必须新增版本。
- 迁移执行入口必须：使用 **session 级 advisory lock**（避免并发索引等待迁移器自身事务）、`baseline-on-migrate=false`、**禁止 clean**。
- 非事务迁移（含 `CREATE INDEX CONCURRENTLY`）必须显式声明为不可回滚段，失败后走前滚修复，不得删除索引或改写迁移。
- 结构迁移与种子数据**分离**：结构只由迁移建立；种子数据仅供开发/演示，生产禁止自动导入。
- 生产默认 **关闭自动迁移**（需显式开关），并要求上线前完成预检：表数量、关键约束、租户归属、历史数据一致性。

## 5. API 契约（109 个端点）

统一约定：

> 计数边界：本仓库 Kotlin 控制器实测为 109 条业务 `method + path` 映射；相邻 `auth-starter` 仓库另有 54 条 `/auth/**` 认证路径。本节按域压缩列契约语义（合并同一路径的多方法与别名，因此表行数少于 109）；逐条代码级清单与权限/参数请以 [13-api-design-detailed.md](../13-api-design-detailed.md) 为准。

- 前缀 `/api/v1`；认证域端点在 `/auth/**`（登录、刷新、登出、SSO、微信、注册）。
- 成功响应统一信封 `{"code":"0","message":"OK","data":...}`；错误响应同信封 + 业务码（§11）。
- 所有端点默认需要认证；权限以角色表达（下表 roles 列为允许角色集合，`isAuthenticated` 表示仅需登录）。
- 时间统一 ISO-8601（服务端时区 Asia/Shanghai）；分页参数 `page`/`size`，响应 `list/page/size/total`。

### 5.1 量表与量表治理（40）

| 方法 | 路径 | 允许角色 |
| --- | --- | --- |
| GET | `/api/v1/scales` | ASSESSMENT_ADMIN, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| POST | `/api/v1/scales` | 同上 |
| GET | `/api/v1/scales/{id}` | 同上 |
| DELETE | `/api/v1/scales/{id}` | 同上 |
| POST | `/api/v1/scales/{id}/basic` | 同上 |
| POST | `/api/v1/scales/{id}/publish` | 同上 |
| GET | `/api/v1/scales/{id}/versions` | 同上 |
| POST | `/api/v1/scales/{id}/versions` | 同上 |
| GET | `/api/v1/scales/{id}/versions/{targetId}/diff` | 同上 |
| POST | `/api/v1/scales/{id}/dimensions/batch` | 同上 |
| POST | `/api/v1/scales/{id}/dimensions/{dimensionId}` | 同上 |
| POST | `/api/v1/scales/{id}/questions/batch` | 同上 |
| POST | `/api/v1/scales/{id}/questions/{questionId}` | 同上 |
| POST | `/api/v1/scales/{id}/options/{optionId}` | 同上 |
| POST | `/api/v1/scales/{id}/result-rules/batch` | 同上 |
| POST | `/api/v1/scales/{id}/norms/batch` | 同上 |
| GET | `/api/v1/scales/{id}/norm-coverage` | 同上 |
| POST | `/api/v1/scales/{id}/visualizations` | 同上 |
| GET | `/api/v1/scales/{id}/versions/{targetId}/diff` | 同上 |
| GET | `/api/v1/scales/import-template` | 同上 |
| POST | `/api/v1/scales/imports/parse` | 同上 |
| GET | `/api/v1/scales/imports` | 同上 |
| GET | `/api/v1/scales/imports/{id}` | 同上 |
| POST | `/api/v1/scales/imports/{id}/confirm` | 同上 |
| POST | `/api/v1/scales/imports/package/preview` | 同上 |
| POST | `/api/v1/scales/imports/package/{id}/confirm` | 同上 |
| GET | `/api/v1/scales/{scaleId}/package` | 需登录 |
| PUT | `/api/v1/scales/{scaleId}/package` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/package/export` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/publication/readiness` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/publication/history` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/publication/history/cases` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/publication/history/runs` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/publication/history/reviews` | 需登录 |
| GET | `/api/v1/scales/{scaleId}/publication/golden-cases` | 需登录 |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases` | ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases/{caseId}/run` | 同上 |
| POST | `/api/v1/scales/{scaleId}/publication/golden-cases/{caseId}/approve` | **COUNSELOR**（专业复核） |
| POST | `/api/v1/scales/{scaleId}/publication/reviews/{reviewType}` | 需登录（按评审类型细分权限） |

### 5.2 任务与作答（13）

| 方法 | 路径 | 允许角色 |
| --- | --- | --- |
| GET/POST | `/api/v1/tasks`, `/api/v1/tasks/{id}` | ASSESSMENT_ADMIN, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| DELETE | `/api/v1/tasks/{id}` | 同上 |
| POST | `/api/v1/tasks/{id}/assign-users`, `/assign-groups`, `/close` | 同上 |
| GET | `/api/v1/my/tasks` | 需登录 |
| GET | `/api/v1/my/tasks/{taskId}/questions` | USER |
| POST | `/api/v1/answer-sheets/save` | USER |
| POST | `/api/v1/answer-sheets/submit` | USER（幂等令牌） |
| POST | `/api/v1/results/{resultId}/rescore` | ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |

### 5.3 报告与统计（9）

| 方法 | 路径 | 允许角色 |
| --- | --- | --- |
| GET | `/api/v1/reports` | COUNSELOR, ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| GET | `/api/v1/reports/my` | 需登录 |
| GET | `/api/v1/reports/{id}`, `/api/v1/reports/by-result/{resultId}` | 需登录（按归属校验） |
| GET | `/api/v1/reports/users/{userId}` | COUNSELOR, ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| POST | `/api/v1/reports/{id}/regenerate` | 同上 |
| GET | `/api/v1/statistics/dashboard` | COUNSELOR, ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| GET | `/api/v1/statistics/group-reports` | 同上 |
| GET | `/api/v1/statistics/group-reports/download` | 同上 |

### 5.4 预警、干预与安全策略（9）

| 方法 | 路径 | 允许角色 |
| --- | --- | --- |
| GET | `/api/v1/warnings` | COUNSELOR, ASSESSMENT_ADMIN, ADMIN, SUPER_ADMIN |
| POST | `/api/v1/warnings/{id}/claim` | COUNSELOR, ASSESSMENT_ADMIN, ADMIN, SUPER_ADMIN |
| POST | `/api/v1/warnings/{id}/assign` | ASSESSMENT_ADMIN, ADMIN, SUPER_ADMIN |
| POST | `/api/v1/interventions` | COUNSELOR, ASSESSMENT_ADMIN, ADMIN, SUPER_ADMIN |
| POST | `/api/v1/interventions/{id}/close` | 同上 |
| GET | `/api/v1/safety-response-policies` | COUNSELOR + 管理角色 |
| POST | `/api/v1/safety-response-policies` | ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| POST | `/api/v1/safety-response-policies/{id}/professional-review` | **COUNSELOR** |
| POST | `/api/v1/safety-response-policies/{id}/approve` | 管理角色（且不得与复核人相同） |

### 5.5 通知、导出、预约、用户与注册（38）

| 方法 | 路径 | 允许角色 |
| --- | --- | --- |
| GET | `/api/v1/my/notifications` | 需登录 |
| POST | `/api/v1/my/notifications/{id}/read` | 需登录 |
| POST | `/api/v1/my/notifications/deliveries/{deliveryId}/received`, `/clicked` | 需登录（单调状态升级） |
| GET | `/api/v1/notifications/policies`, POST 同上 | ADMIN, SYS_ADMIN, SUPER_ADMIN |
| GET | `/api/v1/notifications/{id}/deliveries` | 管理角色集合 |
| POST | `/api/v1/notifications/{id}/deliveries/retry` | 同上 |
| GET | `/api/v1/notifications/deliveries/summary` | 同上 |
| GET | `/api/v1/notifications/ops/feed` | 同上 |
| POST | `/api/v1/notifications/deliveries/retry-batch` | 同上 |
| POST | `/api/v1/notifications/deliveries/{deliveryId}/callbacks` | 同上（运维回调） |
| POST | `/api/v1/exports/reports`, `/jobs` | COUNSELOR + 管理角色集合 |
| GET | `/api/v1/exports/reports/jobs`, `/jobs/{jobId}`, `/jobs/{jobId}/download`, `/reports/download` | 同上 |
| POST | `/api/v1/exports/reports/jobs/{jobId}/retry` | 管理角色集合 |
| GET | `/api/v1/exports/reports/storage` | 管理角色集合 |
| POST | `/api/v1/appointments` | USER + 管理角色 |
| GET | `/api/v1/appointments/my` | 需登录 |
| POST | `/api/v1/appointments/{id}/cancel` | 需登录 |
| GET | `/api/v1/counselors`, `/counselors/{id}/schedules` | 需登录 |
| POST | `/api/v1/counselors/me/schedules` | COUNSELOR + 管理角色 |
| POST | `/api/v1/counseling-records` | COUNSELOR + 管理角色 |
| GET/POST | `/api/v1/my/profile` | 需登录 |
| GET/POST | `/api/v1/user-admin/users`, `/users/{userId}/roles`, `/users/{userId}/status`, `/users/{userId}/password/reset` | ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| GET | `/api/v1/user-admin/tenants`, `/groups`, `/roles` | ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| GET | `/api/v1/admin/external-registrations/pending` | ASSESSMENT_ADMIN, ORG_MANAGER, ADMIN, SYS_ADMIN, SUPER_ADMIN |
| POST | `/api/v1/admin/external-registrations/{userId}/approve`, `/reject` | 同上 |

## 6. 核心流程与状态机

### 6.1 任务生命周期

```
DRAFT → PUBLISHED → IN_PROGRESS → COMPLETED → CLOSED
```

- 发布时校验量表版本完备性（维度/题目/选项/结果规则/高风险规则/常模覆盖），并**锁定版本**；任务进行中量表内容不得被就地修改（量表侧改为发布新版本）。
- 逾期扫描任务按 `end_time` 推进状态并触发通知；关闭任务需记录操作人与原因。

### 6.2 作答与提交

1. 拉取题目：按租户 + 任务 + 用户校验可见性，返回题目、选项、跳过规则声明。
2. 保存草稿：服务端保留草稿（版本号 CAS），客户端可缓存"游标 + 版本"（**不得缓存题目正文与答案明文**）。
3. 提交：**幂等令牌**去重；校验题型规则（见 §7.2）；计算质量结果；评分；生成报告；命中高风险则创建预警；整个过程**单事务内完成**，失败不得留下半成品。
4. 重考/重评：旧结果保留（`is_current=false`），新增结果行；轨迹与新结果一并记录。

### 6.3 预警与干预闭环

```
PENDING → ASSIGNED → PROCESSING → CLOSED
```

- 认领（claim）与指派（assign）都需审计；超时未认领按配置小时数升级（优先级/等级提升 + 通知升级对象）。
- 干预记录 `current_status`：进行中 → 已结案；结案需填写摘要，可选择生成**复测任务**并回填 `retest_task_id`。
- 高风险预警的处置必须引用**已生效的安全响应策略**；无生效策略时明确提示（不允许静默跳过）。

### 6.4 导出作业

```
PENDING → PROCESSING → DONE | FAILED → DEAD_LETTER
```

- 领取需**租约 + fencing token**：处理中崩溃后由超时回收重新入队，重试次数与退避按配置；超过最大次数进 `DEAD_LETTER`，仅管理员可人工重试。
- 产物同时支持本地目录与 S3 兼容 HTTP 两种模式；下载必须校验归属（任务归属租户 + 请求者权限）。

### 6.5 通知投递

```
PENDING → PROCESSING → SENT | FAILED → DEAD_LETTER
                  ↘（渠道回执）RECEIVED → CLICKED
```

- 渠道：站内、邮件（SMTP）、微信模板消息、HTTP Push、FCM。空配置时对应渠道**降级为 no-op 而非报错**。
- 回执状态**只能单调升级**（不得把终态写回低级态）；运维回调需鉴权。
- 队列指标必须暴露：待投递数、处理中数、失败/死信数、最老待投递时长。

### 6.6 外部注册审批

```
status=4 PENDING_APPROVAL → status=1 ENABLED（通过）
                          → status=5 REJECTED（拒绝）
```

- 列表与审批均按请求者租户过滤；审批与拒绝都发送通知邮件（邮件失败不得回滚状态变更）。

## 7. 计分与质量策略

### 7.1 计分方法（5 种，必须全部实现）

| 方法 | 语义 |
| --- | --- |
| `SIMPLE_SUM` | 有效题分值求和 |
| `REVERSE_SUM` | 按维度/题目反向计分后求和 |
| `WEIGHTED_SUM` | 维度权重加权求和 |
| `AVERAGE` | 平均分（分母 = 参与计分的题数） |
| `WEIGHTED_AVERAGE` | 维度均值的加权平均，**分母为维度权重之和** |

- 总分 = 原始分 × `score_coefficient`（系数必须 > 0）。
- **受限 profile**（如 SCL-90）：GSI/PST/PSDI 等指标必须基于**原始分**计算，不能直接用已乘系数的总分（需要先除以系数还原）。
- 评分轨迹必须包含：算法码/版本、逐题有效分、维度分、中间量、质量结果、受限 profile、生成时间。

### 7.2 题型与作答校验（7 种）

`SINGLE_CHOICE`、`MULTI_SELECT`、`MATRIX`、`TEXT_WITH_OPTION`、`TEXT`、`TIME`、`SLIDER`

每种题型的必填/取值范围/选项合法性/矩阵单元格唯一性/滑杆步长等都必须服务端校验，并有独立错误码（见 §11，`ANSWER_*` / `QUESTION_*` 系列）。

### 7.3 质量策略

- 策略维度：缺失答案处理 `REJECT` / `ALLOW` / `PRORATE`；与 5 种计分方法组合共 15 种组合必须都可用。
- 结果需记录：有效题数、缺失题数、缺失比例、答题时长、质量状态；质量不通过时按策略拒绝提交或标记为 `INVALID`，`INVALID` 结果不进入逾期/统计口径。

### 7.4 跳过规则与高风险规则

- 跳过规则为**声明式**：`whenQuestionNo` + `whenOptionCode` → `skipQuestionNos`；解析失败必须**失败关闭**（拒绝加载任务，而不是忽略规则）。
- 高风险规则：触发条件为"选项命中"或"分值阈值"**二选一**，阈值必须落在量表响应范围内；规则需提供三语结果标题/描述/建议。

## 8. 量表导入与发布治理

### 8.1 两种导入入口

1. **Excel 模板导入**（`/scales/imports/parse` + `/confirm`）：模板列固定，逐行校验并产出问题清单（severity/code/位置）。
2. **源包导入**（JSON 源包预览 + 确认）：完整声明量表、维度、题目、选项、结果规则、高风险规则、常模、治理信息与三语翻译。

### 8.2 源包校验规则（必须全部实现）

- 标识与版本、量表代码冲突检查、响应范围与选项标签数量一致；
- 算法绑定与量表形态一致（例如 SCL-90 profile 要求 90 题、0–4 计分、系数为 1）；
- 结果规则区间不重叠、覆盖完整；高风险规则条件合法；常模均值/标准差/年龄区间合法；
- 三语翻译键完整且非空；
- 已存在量表时只允许"新增版本"或明确的冲突错误。

### 8.3 导入治理强制项（不可放宽）

- 源包**声明的授权/版权状态不得因为"被导入"而变成审批结论**：入库时一律写为 `authorization_status=PENDING_REVIEW`、`copyright_status=PENDING_REVIEW`、`governance_status=DRAFT`；原始声明保留在导入作业的预览 JSON 中作为审计线索。
- 治理状态推进只允许人工操作（见 §8.4），并且每次推进都要有操作人、时间与依据。

### 8.4 发布治理链

```
DRAFT → 内容指纹锁定 → GoldenCase（专业复核人独立批准）→ 发布评审（各评审类型通过）
      → readiness 通过 → PUBLISHED
```

- **GoldenCase**：由一人创建、另一名 COUNSELOR 批准（禁止自审），必须绑定当前内容指纹且运行结果为 PASS。
- **完成度检查（readiness）**：维度/题目/选项/结果规则/常模/翻译/治理证据齐备才算就绪。
- 发布评审类型与决策需落库为证据（评审人、时间、决策、指纹）。
- **技术通过 ≠ 正式支持**：治理状态与技术支持状态是两套字段，`BLOCKED_EXTERNAL` 表示授权/审校/双审批/业务验收未完成；系统不得据此对外声称"正式支持"。

## 9. 多租户与安全不变式

1. **租户判定**：请求者租户来自认证上下文。缺租户**不等于**全局权限：只有租户为空的 `SYS_ADMIN`/`SUPER_ADMIN` 才允许跨租户操作，且**每次跨租户访问都必须写审计**；租户为空的其它角色直接拒绝（`TENANT_CONTEXT_REQUIRED`）。
2. **租户绑定管理员**只能访问本租户数据；统计、列表、导出、审批一律带租户谓词。
3. **双人分离**：
   - 安全响应策略：专业复核人（COUNSELOR）≠ 审批人；未复核不得生效。
   - GoldenCase：创建人 ≠ 批准人。
4. **认证与凭据**：JWT 密钥 ≥256 bit（HS256）；口令 Argon2；登录失败锁定与 IP 维度限流；SSO 一次性 code/state 必须**校验后立即失效**；SSO 回调只允许服务端配置的可信前端地址。
5. **隐私**：默认不返回题目正文与答案明文给非授权角色；匿名答卷只保留聚合租户/组织；报告下载需归属校验；日志不得打印凭据与答案明文。
6. **审计**：跨租户访问、量表治理动作、GoldenCase 审批、预警认领/指派、干预结案、导出重试、通知回执回调、外部注册审批都要留痕。

## 10. 后台任务与并发

必须实现的定时任务（间隔可配置）：

| 任务 | 职责 | 默认间隔 |
| --- | --- | --- |
| 通知投递扫描 | 领取待投递消息、重试、超时回收、死信 | 60s |
| 任务逾期扫描 | 推进逾期任务状态并通知 | 60s |
| 预警升级扫描 | 超时未认领/未处理升级与催办 | 60s |
| 草稿清理 | 清理过期草稿（按保留天数） | 1h |
| 导出作业扫描 | 领取待处理作业、重试、超时回收 | 60s |
| 导出产物清理 | 清理过期导出文件 | 5min |

并发要求：

- 多实例部署时用 **Redis 分布式锁**（`SET NX EX` + Lua 比对令牌释放）避免重复执行；Redis 不可用时**降级为单实例执行**而不是阻塞业务。
- 作业领取使用**租约 + fencing**：租约到期可被其他实例接管，旧实例的写入必须因 fencing 失效而被拒绝。
- 所有重试都有最大次数、指数退避与死信出口。

## 11. i18n 与错误响应

- 语言：`zh-CN`（默认）、`ja-JP`、`en-US`，共约 **494 个消息键**，三语键集合必须完全一致。
- 语言选择：`Accept-Language` 请求头，落库的翻译按 `locale_code` 区分；报告生成需按请求语言输出（含 Word/PDF 字体与 CJK 字形）。
- 前端：`locale` 存本地存储，切换后所有界面、枚举标签、表格列、校验提示同步切换；枚举码**不得直接展示**（必须有本地化映射与回退）。
- 错误响应统一：`{"code":"<BUSINESS_CODE>","message":"<本地化文案>","data":null}`；未知异常返回 `INTERNAL_ERROR` + 本地化通用文案，不得泄露堆栈。
- 业务码风格：大写蛇形，按域前缀分组（`ANSWER_*`、`QUESTION_*`、`SCALE_*`、`GOLDEN_CASE_*`、`NORM_*`、`APPOINTMENT_*`、`EXPORT_*`、`JOB_*`、`NOTIFICATION_*`、`SAFETY_POLICY_*`、`INTERVENTION_*`、`EXTERNAL_REGISTRATION_*`、`TENANT_*`），现有实现约 90+ 个业务码。

## 12. 配置契约（环境变量）

重建时应保留同名环境变量语义（完整清单以仓库 `application.yml` 为准，静态统计 96 个 `PSY_*` 名称）。关键分组：

| 分组 | 变量 | 语义 |
| --- | --- | --- |
| 数据库 | `PSY_DB_URL` / `PSY_DB_USERNAME` / `PSY_DB_PASSWORD` | JDBC/DSN、账号、口令；连接池参数另有 `PSY_DB_*_TIMEOUT_MS`、`PSY_DB_MAX_LIFETIME_MS`、`PSY_DB_KEEPALIVE_TIME_MS` |
| Redis | `PSY_REDIS_HOST` / `PSY_REDIS_PORT` / `PSY_REDIS_PASSWORD` / `PSY_REDIS_DATABASE` | 仅调度锁使用 |
| 迁移 | `PSY_FLYWAY_ENABLED`（默认 false）/ `PSY_SQL_INIT_MODE`（默认 never） | 生产默认关闭自动迁移；种子需显式开启 |
| 调度锁 | `PSY_SCHEDULER_LOCK_ENABLED` / `PSY_SCHEDULER_LOCK_KEY_PREFIX` | 默认开启，前缀 `psy:scheduler:lock` |
| 鉴权 | `PSY_JWT_SECRET`（**必须 ≥32 字节**）/ `PSY_AUTH_ANONYMOUS_IDENTITY_SECRET` | 密钥过短将导致签发失败（HS256 要求 256 bit） |
| SSO | `PSY_AUTH_SSO_OIDC_*`、`PSY_AUTH_SSO_CAS_*`、`PSY_AUTH_SSO_FRONTEND_CALLBACK_URL`、`PSY_AUTH_SSO_CALLBACK_BASE_URL` | 回调地址必须显式配置，只允许可信前端域 |
| 社交/微信 | `PSY_AUTH_WECHAT_ENABLED`、`PSY_NOTIFICATION_WECHAT_ENABLED`、`PSY_NOTIFICATION_WECHAT_TEMPLATE_ID` | 关闭时对应能力 no-op |
| 邮件 | `PSY_MAIL_HOST`（空则 no-op 发信）/ `PSY_MAIL_PORT` / `PSY_MAIL_USERNAME` / `PSY_MAIL_PASSWORD` / `PSY_MAIL_FROM` + 超时项 | SMTP |
| Push | `PSY_NOTIFICATION_PUSH_HTTP_*`、`PSY_NOTIFICATION_PUSH_FCM_*`、`PSY_NOTIFICATION_DEEP_LINK_*` | HTTP/FCM 网关与深链 |
| 通知队列 | `PSY_NOTIFICATION_MAX_ATTEMPTS`、`PSY_NOTIFICATION_INITIAL_RETRY_DELAY_SECONDS`、`PSY_NOTIFICATION_MAX_RETRY_DELAY_SECONDS`、`PSY_NOTIFICATION_PROCESSING_TIMEOUT_MINUTES`、`PSY_NOTIFICATION_DELIVERY_BATCH_SIZE`、`PSY_NOTIFICATION_DELIVERY_SCAN_DELAY_MS` | 重试、退避、租约超时、批大小 |
| 导出 | `PSY_EXPORT_MAX_ATTEMPTS`、`PSY_EXPORT_PROCESSING_TIMEOUT_SECONDS`、`PSY_EXPORT_MAX_IN_MEMORY_JOBS`、`PSY_EXPORT_MAX_IN_MEMORY_FILE_BYTES`、`PSY_EXPORT_FILE_STORAGE_ENABLED`、`PSY_EXPORT_STORAGE_DIR`、`PSY_EXPORT_ARTIFACT_*`（S3 兼容：ENDPOINT_URL/BUCKET/KEY_PREFIX/API_KEY/STORAGE_MODE）、`PSY_EXPORT_CLEANUP_ENABLED` | 作业与产物 |
| 预警 | `PSY_WARNING_UNCLAIMED_ESCALATION_HOURS`、`PSY_WARNING_PROCESSING_REMINDER_HOURS`、`PSY_WARNING_ESCALATION_SCAN_DELAY_MS` | 升级与催办 |
| 测评 | `PSY_ASSESSMENT_DRAFT_RETENTION_DAYS`、`PSY_ASSESSMENT_DRAFT_CLEANUP_SCAN_DELAY_MS` | 草稿保留与清理 |
| 外部注册 | `PSY_EXTERNAL_REGISTRATION_ACTIVATION_BASE_URL`、`..._APPROVAL_SUBJECT`、`..._REJECTION_SUBJECT` | 激活链接与通知主题 |
| 可观测 | `PSY_TRACING_ENABLED`、`PSY_TRACING_SAMPLING_PROBABILITY`、`PSY_LOG_CONSOLE_FORMAT` | 追踪采样与日志格式 |
| HTTP 客户端 | `PSY_HTTP_CONNECT_TIMEOUT_MILLIS`、`PSY_HTTP_READ_TIMEOUT_MILLIS` | 出站超时 |

## 13. 可观测性

必须暴露的指标（名称与语义建议保持一致，便于沿用现有告警规则）：

| 指标 | 类型 | 语义 |
| --- | --- | --- |
| `psy.scheduler.runs` / `psy.scheduler.duration` | Counter / Timer | 按 `job` × `outcome(success/failure/skipped)` |
| `psy.scoring.runs` / `psy.scoring.duration` | Counter / Timer | 评分次数与耗时 |
| `psy.assessment.submissions` / `psy.assessment.submission.duration` | Counter / Timer | 按 `outcome` × `mode` × `identity(anonymous/identified)` × `risk` 分桶，**标签值必须白名单归一化** |
| `psy.warning.queue.size`（按 status）/ `psy.warning.queue.oldest.open.seconds` | Gauge | 预警积压与最老未处理时长 |
| `psy.warning.actions` | Counter | 按 `action`（认领/指派/结案等） |
| `psy.notification.delivery.attempts` / `psy.notification.delivery.recovered` | Counter | 投递尝试与恢复 |
| `psy.notification.queue.size`（pending/processing/failed_or_dead_letter）/ `psy.notification.queue.oldest.pending.seconds` | Gauge | 投递积压 |
| `psy.export.jobs` | Counter | 按 `status` × `format` |
| `psy.export.job.file.bytes` | DistributionSummary | 产物大小，按 `format` |

其他要求：结构化日志（可选 JSON）、每请求 correlation id 贯穿日志与响应头、trace/span id 注入日志、敏感字段脱敏、健康检查端点（存活/就绪）与 Prometheus 抓取端点；告警规则至少覆盖：队列积压、最老待投递/未处理时长、调度任务失败、导出死信。

## 14. 前端与 Android 契约

### 14.1 Admin Web / 用户侧 Web

- 页面清单（28）：登录、注册（外部/自助）、SSO 回调、微信 OAuth、仪表盘、量表列表、量表发布就绪、量表治理录入、测评任务、预警管理、安全响应策略、群体报告、个体报告管理、报告详情、预约咨询、导出中心、通知消息、通知运维、用户管理、待审核注册、认证审计、会话详情、我的任务、作答页、我的报告、我的资料、占位页。
- 路由按角色控制（前端仅做呈现层控制，**权限判定必须在服务端**）。
- 三语界面 + 枚举本地化：所有后端枚举码必须经本地化映射后展示，未知码回退显示原码；多语言词条三语键集合必须一致（用测试强制）。
- 构建期变量：`VITE_WECHAT_APP_ID`、`VITE_SSO_OIDC_ENABLED`、`VITE_SSO_CAS_ENABLED`；缺失时对应入口隐藏或提示错误，必须写进 `.env.example` 与部署文档。
- 部署形态：静态产物由 nginx 托管，`/api/` 与 `/auth/` 反代到后端；SPA 未知路由回退 `index.html`。

### 14.2 Android 被测者端

- 能力：登录、任务列表、作答与提交校验、报告查看、预约、通知。
- 必须有：本地会话存储（凭据不落明文日志）、提交幂等令牌、离线草稿仅存游标不存题目/答案明文、答题校验与后端规则一致的单测。

## 15. 部署与运维

- 拓扑：nginx（80/443）→ 后端（仅回环 8090）→ PostgreSQL（回环 5432）→ Redis（回环 6379）。
- 后端以 systemd 托管：`EnvironmentFile` 注入配置、`Restart=on-failure`、按机器内存设定堆上限（1.8G 机器建议 `-Xmx512~640m` 并配置 swap），日志进 journald。
- 初始化顺序（**不可颠倒**）：迁移 → 认证域种子（权限目录/基础角色）→ 业务种子。业务种子不含权限目录，顺序错误会导致全站 403。
- 迁移守卫：预检（表数/约束/租户归属/孤儿行）→ 显式 baseline 或 migrate → 校验 history；**禁止 clean**。
- 备份与恢复：定期备份 + 恢复演练脚本；恢复后必须验证关键表行数与约束。
- 上线检查项：`PSY_JWT_SECRET` 已替换、演示账号已停用/删除、数据库口令已更换、HTTPS 已配置、Redis 已设口令或仅回环、告警规则已接入。

## 16. 测试与验收门槛

重建不是"能编译能启动"，必须产出下列证据：

1. **迁移验证**：空库执行全部迁移成功；重复执行幂等；关键约束（§4.2 七条）有测试覆盖；非事务迁移可重复执行。
2. **种子验证**：按 §15 顺序导入后，权限目录非空、管理员可登录、业务数据父子租户链一致；种子重复执行不产生重复行。
3. **API 契约测试**：§5 每个端点至少 1 条正向 + 1 条权限负向用例（越权必须 403，跨租户必须拒绝）。
4. **业务闭环测试**：作答（7 种题型）→ 提交（幂等）→ 评分（5 方法 × 3 质量策略）→ 报告（三语、PDF/Word/文本）→ 预警 → 干预 → 复测。
5. **量表回归**：每个已登记量表版本需 Golden Case 通过 + 逐量表技术闭环检查（计数、评分轨迹、三语报告、任务锁版、幂等、并发、追加式重评）。
6. **并发与恢复**：导出与通知 worker 在外部调用中被强杀后，租约回收 → 重试（`retry_count` 递增、租约清空、不产生重复行）；死信可人工重放；旧实例 fencing 写入被拒绝。
7. **前端验证**：三语切换、枚举本地化、SPA 深链、e2e 覆盖任务作答与预警处置；控制台零错误。
8. **可观测性验证**：§13 指标在真实运行中出现且标签不失控（无 id 类高基数）。

证据格式要求：命令 + 退出码 + 计数（tests/passed/failed/skipped）+ 关键日志或报告产物路径。**不接受"应该可以"这类描述。**

## 17. 禁止事项

1. 不得用 `clean`、删迁移、改已执行迁移的方式"解决"迁移失败；只能前滚修复。
2. 不得让源包里的授权/版权声明自动成为审批结论；治理状态只能人工推进并留痕。
3. 不得绕过租户谓词；缺租户不等于全局权限；跨租户访问必须审计。
4. 不得把技术 PASS、Golden Case 通过、报告生成成功表述为"正式支持/临床可用"；授权、三语审校、专业双审批、业务验收是外部阻塞项。
5. 不得把 Redis 当作业务状态权威；Redis 丢失只能影响锁，不能影响业务数据。
6. 不得在日志、前端缓存、导出产物中泄露答案明文、凭据或未授权题目正文。
7. 不得在生产导入演示种子或保留演示账号。
8. 不得在前端做权限决定；不得将枚举码直接展示给用户。

## 18. 交付物与验收标准

交付物清单：

1. 服务端代码（§5 全部端点 + §6 流程 + §7 计分 + §8 治理 + §10 任务）。
2. 数据库迁移集合（与 V1..V28 语义等价，含全部约束与索引）与迁移守卫入口。
3. 种子脚本（认证域权限目录 + 业务演示数据）与初始化脚本（管理员）。
4. 部署物：systemd unit、nginx 站点配置、`.env.example`、迁移/备份/恢复脚本。
5. API 文档 + 三语消息目录 + 错误码清单。
6. 报告模板与生成器（PDF/Word/文本，CJK 字体随包）。
7. 测试套件与验收证据包（§16）。

验收标准：§16 八项证据全部产出；§17 禁止事项零违反；§5 端点权限矩阵可被自动化测试逐条验证；三语界面与报告可用；从空库到可登录系统可一键复现。

## 19. 分阶段实施顺序（建议）

**阶段 0：契约固化。** 先把数据库结构、API 契约、错误码、i18n 键、租户与治理不变式固化成可执行测试（可用现有 Kotlin 实现作为对照实现生成期望值）。没有这一步，后续阶段无法验证等价性。

**阶段 1：后台任务与 worker。** 提取通知投递、导出作业、逾期与预警升级、草稿清理为独立服务，共享同一数据库与迁移。它们与请求路径解耦、边界清晰，最容易先落地并立刻降低 JVM 资源占用。验收：租约/重试/死信/幂等全套测试。

**阶段 2：读路径。** 统计、仪表盘、群体报告、报告查询改为新实现，用差分回归（同一数据集对比两套实现的输出）验证。

**阶段 3：认证接入。** 优先复用现有认证服务；若自建，必须实现 §9 第 4 条的全部语义（JWT ≥256 bit、Argon2、锁定与限流、SSO 一次性 code/state）。

**阶段 4：写路径与治理。** 作答、评分、量表导入与发布治理、安全响应策略双人复核。这是风险最高的阶段，必须逐条实现 §4.2、§8.3、§9 的不变式并配套负向测试。

**阶段 5：报告与导出产物。** PDF/Word/Excel 与 CJK 字体、三语输出、报告语义（含受限 profile 指标）。此阶段最容易出现"能生成但内容不对"，必须以 Golden Case 与逐量表回归收敛。

每阶段结束条件：本阶段相关的 §16 证据齐全 + 与旧实现差分一致（同输入同输出，允许的差异必须显式记录）+ §17 零违反。
