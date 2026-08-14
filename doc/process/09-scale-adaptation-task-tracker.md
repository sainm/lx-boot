# 逐量表适配任务与回归台账

## 1. 台账信息

- 建立日期：2026-08-14
- 工程：`lx-boot`
- 当前分支：`feature/redo`
- 建立时 HEAD：`9ef1aea`
- Android：不在当前目标范围
- 执行 Prompt：[逐量表适配与全量兼容回归 Prompt](../prompt/07-scale-by-scale-adaptation-and-regression.md)

本文件是逐量表适配的唯一状态台账。Prompt、设计文档、测试文件存在或代码可以编译，都不能单独作为完成证明。

## 2. 状态定义

| 状态 | 含义 |
|---|---|
| `NOT_STARTED` | 未开始 |
| `INPUT_PENDING` | 缺真实资料、授权、三语或正式规则 |
| `IN_PROGRESS` | 正在实现，完整验收未通过 |
| `PARTIALLY_SUPPORTED` | 部分能力存在，但无法正式闭环 |
| `TECHNICALLY_VERIFIED` | 技术闭环通过，外部审批未完成 |
| `BLOCKED_EXTERNAL` | 等待版权、专业、业务、常模或安全治理 |
| `FORMALLY_APPROVED` | 真实双审批完成，最终发布验收未完成 |
| `FULLY_SUPPORTED` | 全部代码、数据、回归和外部验收完成 |
| `REGRESSION_FAILED` | 已破坏至少一个既有量表 |
| `UNSUPPORTED` | 当前模型明确不能安全表达 |

总体任务另使用：`NOT_STARTED`、`IN_PROGRESS`、`COMPLETED`、`BLOCKED`。量表状态和工程任务状态不可混用。

## 3. 总体任务

| Task ID | 优先级 | 任务 | 状态 | 完成证据 | 剩余问题 |
|---|---:|---|---|---|---|
| `REG-001` | P0 | 建立机器可读的已完成量表注册表 | `COMPLETED` | [scale-adaptation-registry.json](../scale-packages/scale-adaptation-registry.json)；2026-08-14 执行 `python3 scripts/validate_scale_adaptation_registry.py` 通过，注册7个版本化包，其中6个进入技术回归、1个 SCL-90 历史草稿保留为部分支持；状态一致性门禁要求 `TECHNICALLY_VERIFIED/FULLY_SUPPORTED` 绑定最新回归 `PASS` | 每个新增量表仍需登记唯一版本和 SHA-256 |
| `REG-002` | P0 | 建立统一源包元数据校验与 SHA-256 固定 | `COMPLETED` | 校验器验证K6/K10/SCL-90/WHO-5/GAD-7/PHQ-9路径、SHA-256、版本、算法、模板、语言、Golden Case及expected SHA-256；五个通用单选包共享 `validate_generic_scale_package.py`，SCL-90保留受限专用校验；通用校验支持受控单题高风险规则和 K10 reverse-score 规则 | 仍需在每次完整回归时保存逐量表 scoring trace/结果/报表差异报告 |
| `REG-003` | P0 | 在隔离 PostgreSQL 导入所有注册量表并运行全部 Golden Case | `COMPLETED` | `scripts/run-scale-package-e2e.sh` 创建临时schema；通用业务E2E 8/8；最新注册表执行器 `REG-PLAYWRIGHT-20260814-135509` 运行PHQ-9 10/10、K6 6/6、K10 9/9、SCL-90 5/5、WHO-5 6/6、GAD-7 8/8 Golden Case；6个技术回归项全部 PASS，历史 SCL-90 草稿不进入 active run | SCL-90 v1 历史草稿仍保持部分支持，不得与 v2 技术版本混称 |
| `REG-004` | P0 | 比较完整 scoring trace、结果规则、风险和版本摘要 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509` 对 PHQ-9、K6、K10、SCL-90、WHO-5、GAD-7 从持久化选项/答卷重算每题原始、反向、权重/有效分、维度、结果风险、派生指标、内容hash与算法；K10 额外验证 reverse-score recode 和 10–50 总分，SCL-90 额外验证 `SCL90_PROFILE`、GSI/PST/PSDI 和两条高风险规则 | 所有量表仍需真实专业解释和治理审批；技术结果不等于临床结论 |
| `REG-005` | P0 | 统一验证三语 Web、Word、PDF、文本语义 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509` 隔离 E2E/PostgreSQL 验证6个 active 技术版本的三语题目、三种提交 locale、日文 Web 及 Word/PDF/文本，SCL-90 profile-only 结果不含未经批准临床切点；共享 PDF 字体集合导出兼容已修复 | 三语字符串仍是技术草稿，需正式翻译审校和电子使用范围确认 |
| `REG-006` | P0 | 验证旧任务锁版、新版本隔离和历史结果/报告不变 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509` 对6个 active 技术版本验证任务锁版、内容hash、新版本隔离、原结果/报告不可变、幂等/并发提交和追加式重评分历史 | 生产历史数据迁移/正式发布仍需业务验收；旧 SCL-90 v1 仅保留历史部分支持 |
| `REG-007` | P1 | CI 增加全量量表兼容回归门禁和逐量表报告 | `COMPLETED` | `.github/workflows/ci.yml` 上传 `build/reports/scale-adaptation`；浏览器 E2E 启动器已调用注册表 Playwright + PostgreSQL 证据执行器 | CI 真实运行结果需等待远程工作流；本地已通过同一入口 |
| `REG-008` | P1 | 建立回归失败工件保存和差异报告 | `COMPLETED` | runner保存不可变注册表指纹、逐量表输入、命令、stdout/stderr、退出码、requiredCheckResults；预检失败保存阶段/数据影响JSON；最新完整报告 `REG-PLAYWRIGHT-20260814-135509` | 失败前驱（常模带入、数字总分、OTF PDF、K10版本长度、K10 reverse Golden）均已记录为关闭问题；治理阻塞仍单独保留 |
| `REG-009` | P0 | 建立已知通用量表快速适配入口 | `COMPLETED` | `GENERIC_SINGLE_CHOICE`共享Playwright、源包校验和PostgreSQL证据；PHQ-9/K6/WHO-5/GAD-7 完整 required checks PASS；SCL-90 使用独立 `SCL90_RESTRICTED_PROFILE`，同样复用通用 Web/Word/PDF/文本/历史闭环 | 多选、矩阵、跳题和其他专用算法需建立受控profile，不能伪装成通用单选能力 |
| `REG-010` | P0 | 用GAD-7验证第四份已知量表的零业务代码快速接入 | `COMPLETED` | `gad7-v1-source-draft.json`、8个Golden Case、三语草稿和4档结果规则；`REG-PLAYWRIGHT-20260814-135509` 中GAD-7 8/8，PHQ-9/K6/K10/WHO-5/SCL-90均完成 active 技术闭环 | GAD-7正式三语审校、成人/其他人群范围、结果解释、法律/业务使用范围、真实专业与业务审批仍属外部阻塞 |
| `REG-011` | P0 | 用PHQ-9验证受控单题高风险的第五份已知量表快速接入 | `COMPLETED` | `phq9-v1-source-draft.json`、10个Golden Case（含题9阳性）、共享 `GENERIC_SINGLE_CHOICE` 高风险扩展、`REG-PLAYWRIGHT-20260814-135509` 中 PHQ-9 10/10 PASS；无量表专属 Controller/Service/SQL/E2E | 中文/日文正式翻译与电子使用权、成人/其他人群范围、题9危机响应责任人/SLA、真实专业双审批和业务验收仍属外部阻塞 |
| `REG-012` | P0 | 用 SCL-90 受限 profile-only 版本完成一次真实版本化技术适配 | `COMPLETED` | `scl90-v2-source-technical.json`（`SCL90_USER_AUTHORIZED@authorized-profile-v1`）；90题、10维度、`SCL90_PROFILE:1`、GSI/PST/PSDI、2个高风险信号、5 Golden Case；`REG-PLAYWRIGHT-20260814-135509` 中 SCL-90 5/5 required checks PASS，且 PHQ-9/K6/K10/WHO-5/GAD-7 全量兼容 PASS | 个人/内部研究范围已登记为用户输入，但正式授权范围归档、三语翻译权/审校、专业双审批、常模、危机责任人/SLA和业务验收仍阻塞；不宣称正式支持 |
| `REG-013` | P0 | 用 K10 官方自填版本完成一次真实版本化技术适配 | `COMPLETED` | `k10-v1-source-official-draft.json`（`K10_OFFICIAL_FREE_USE@official-30day-5point-v1`）；10题、5点显示选项、reverse-score recode、10–50 总分、4档结果、9 Golden Case；`REG-PLAYWRIGHT-20260814-135509` 中 K10 10/10 required checks PASS，且全部5个既有 active 版本同步 PASS | Harvard 的免费使用/引用要求已登记；三语正式审校、切点/人群适用范围、专业双审批和业务验收仍是外部阻塞；不宣称正式支持 |

## 4. 量表适配一览

### 4.1 已进入仓库的真实量表候选

| Task ID | 量表/版本 | 算法与报表 | 技术状态 | 治理状态 | 已有证据 | 未完成项 | 最近全量回归 |
|---|---|---|---|---|---|---|---|---|
| `SCALE-PHQ9-001` | `PHQ9_FREE_USE` / `pfizer-public-domain-severity-v1` | `GENERIC_SCORE_CALCULATOR:1` / `RISK_TRIAGE` | `TECHNICALLY_VERIFIED` | `BLOCKED_EXTERNAL` | Pfizer/PHQ 官方来源、9题0–3求和、5/10/15/20分界、题9阳性人工复核规则、三语草稿、10 Golden Case；共享闭环验证题9高风险、完整 scoring trace、Web/Word/PDF/文本、幂等/并发/重评分和任务锁版 | 中文/日文正式翻译与电子使用权、成人/其他人群范围、题9危机响应责任人/SLA、结果解释审校、真实专业双审批和业务验收 | `REG-PLAYWRIGHT-20260814-135509`：10/10 PASS；技术证据不等于正式支持 |
| `SCALE-K6-001` | `K6_OFFICIAL_FREE_USE` / `official-self-admin-v1` | `GENERIC_SCORE_CALCULATOR:1` / `SINGLE_SCORE` | `TECHNICALLY_VERIFIED` | `BLOCKED_EXTERNAL` | 官方来源包；三语；6 Golden Case；共享 `GENERIC_SINGLE_CHOICE` 隔离 PostgreSQL/Playwright 完整技术链路；Web/Word/PDF/文本 | 真实专业审核、三语审核、13+适用人群审核、业务验收和正式发布 | `REG-PLAYWRIGHT-20260814-135509`：10/10 PASS；非正式审批 |
| `SCALE-K10-001` | `K10_OFFICIAL_FREE_USE` / `official-30day-5point-v1` | `GENERIC_SCORE_CALCULATOR:1` / `SINGLE_SCORE` | `TECHNICALLY_VERIFIED` | `BLOCKED_EXTERNAL` | Harvard 官方 K10 资料；10题、5点显示选项、reverse-score recode、10–50 总分、4档结果、三语草稿、9 Golden Case；共享 `GENERIC_SINGLE_CHOICE` 全链路验证 scoring trace、结果语义、Web/Word/PDF/文本、任务锁版、历史兼容、幂等/并发/重评分 | 官方引用/版权声明须随使用保留；三语正式审校、切点与目标人群适用范围、真实专业双审批和业务验收；当前不宣称正式支持 | `REG-PLAYWRIGHT-20260814-135509`：K10 10/10，且6个 active 版本全部 PASS |
| `SCALE-SCL90-002` | `SCL90_USER_AUTHORIZED` / `authorized-profile-v1` | `SCL90_PROFILE:1` / `NORMATIVE_PROFILE`（profile-only，不加载常模） | `TECHNICALLY_VERIFIED` | `BLOCKED_EXTERNAL` | 90题、10维度、三语技术草稿、GSI/PST/PSDI、2个高风险信号、5 Golden Case；专用受限 profile + 共享 Web/Word/PDF/文本/任务锁版/历史回归 | 用户确认的个人自我观察/非商用研究范围已记录，但正式授权范围归档、三语翻译权与审校、常模、专业双审批、危机责任人/SLA和业务验收仍缺失 | `REG-PLAYWRIGHT-20260814-135509`：10/10 PASS；技术验证不等于正式支持 |
| `SCALE-SCL90-001` | `SCL90_USER_DRAFT` / `v1`（历史草稿） | `SCL90_PROFILE:1` / `NORMATIVE_PROFILE` | `PARTIALLY_SUPPORTED` | `BLOCKED_EXTERNAL` | 90题、10维度、三语草稿、GSI/PST/PSDI、5 Golden Case（含非法选项）、3 个有效 Case scoring trace、风险规则草稿；仅保留历史证据 | 版权授权、正式三语、总体结果区间、常模、危机责任人/SLA、真实双审批和完整 E2E；不得把 v1 与 v2 混为同一版本 | 历史 `REG-PLAYWRIGHT-20260814-130054`：源包/5 Golden/草稿导入/3 trace PASS；7项保持`NOT_RUN` |
| `SCALE-WHO5-001` | `WHO5_WELL_BEING` / `who-2024-open-access-v1` | `GENERIC_SCORE_CALCULATOR:1` / `SINGLE_SCORE` | `TECHNICALLY_VERIFIED` | `BLOCKED_EXTERNAL` | WHO 官方资料源包；5题、0–5求和、`WHO5_PERCENTAGE_SCORE`、三语、6 Golden Case；共享 `GENERIC_SINGLE_CHOICE` 完整链路：双审批工作流、发布、任务锁定、13分评分、52%指标、日文 Web、Word/PDF/文本、幂等/并发提交和重评分历史 | 真实专业双审批、三语审校、CC BY-NC-SA 使用范围确认、切点适用人群/结果解释、业务验收；当前不实现任何未声明的 item-level 临床处置规则 | `REG-PLAYWRIGHT-20260814-135509`：10/10 PASS；仅为隔离环境合成审批，不是正式临床/业务批准 |
| `SCALE-GAD7-001` | `GAD7_FREE_USE` / `pfizer-free-use-v1` | `GENERIC_SCORE_CALCULATOR:1` / `SINGLE_SCORE` | `TECHNICALLY_VERIFIED` | `BLOCKED_EXTERNAL` | Pfizer无版权限制/免费使用声明、英文原表/说明及中日验证资料；7题0–3、4档非诊断结果、三语草稿、8 Golden Case；仅通过包+注册表复用完整技术链路 | 精确中日文正式审校、成人及其他适用人群、分级解释、法律/业务使用范围、真实专业双审批和业务验收 | `REG-PLAYWRIGHT-20260814-135509`：10/10 PASS；隔离合成审批不等于正式批准 |

### 4.2 通用引擎可优先评估的候选量表

以下状态只表示候选顺序，不表示已获得授权或已正式支持。

| Task ID | 量表 | 预期能力 | 当前状态 | 开始条件 |
|---|---|---|---|---|
| `SCALE-PSS10-001` | PSS-10 | 正反向题、求和、`SINGLE_SCORE` | `INPUT_PENDING` | Mapi/ePROVIDE 当前登记为受控量表；CMU 明确要求通过 ePROVIDE 提交使用许可，电子实现还受原始问卷和 eCOA 条件约束；需取得可核验版本、中文/日文电子使用范围、反向题清单和非诊断解释规则后才可生成 ScalePackage |
| `SCALE-SAS-001` | SAS | 反向题、粗分和系数、`SINGLE_SCORE` | `INPUT_PENDING` | 授权版本、三语、正式边界和常模 |
| `SCALE-SDS-001` | SDS | 反向题、粗分和系数、`SINGLE_SCORE` | `INPUT_PENDING` | 授权版本、三语、正式边界和常模 |
| `SCALE-EPDS-001` | EPDS | 求和、反向题、高风险、`RISK_TRIAGE` | `INPUT_PENDING` | 授权、三语、围产期范围和危机处置规则 |
| `SCALE-DASS21-001` | DASS-21 | 三维度、系数、`DIMENSION_PROFILE` | `INPUT_PENDING` | 正式维度映射、三语和结果边界 |
| `SCALE-AUDIT-001` | AUDIT/AUDIT-C | 求和、分级、高风险、`RISK_TRIAGE` | `INPUT_PENDING` | 明确选择版本、三语和结果规则 |
| `SCALE-ESS-001` | ESS | 求和、分级、`SINGLE_SCORE` | `INPUT_PENDING` | 授权、三语和正式区间 |
| `SCALE-PSQI-001` | PSQI | 时间题、睡眠效率、七分量重编码、`DIMENSION_PROFILE` | `PARTIALLY_SUPPORTED` | 授权手册、完整题号/分量规则、三语和 Golden Case；外部输入仍为 `INPUT_PENDING` |

### 4.3 当前明确不支持或必须单独立项

| Task ID | 类别/量表 | 当前状态 | 缺口 |
|---|---|---|---|
| `SCALE-RATER-001` | HAMD、HAMA、Y-BOCS等他评量表 | `UNSUPPORTED` | 评定员身份、任务分配、访谈评分 UI 和资质控制 |
| `SCALE-CSSRS-001` | C-SSRS等分支访谈 | `UNSUPPORTED` | 访谈状态机、动态分支、即时危机处置和人工判断 |
| `SCALE-MMPI-001` | MMPI/MMPI-2 | `UNSUPPORTED` | 效度量表、专用常模、编码和受控专用报告 |
| `SCALE-16PF-001` | 16PF | `UNSUPPORTED` | 专用维度、标准分、常模和人格剖面 |
| `SCALE-EPQ-001` | EPQ | `UNSUPPORTED` | 专用维度、效度分、常模和解释 |
| `SCALE-PERSONALITY-001` | MBTI、NEO、大五完整版、YG等 | `UNSUPPORTED` | 专用算法、授权、常模和人格报告；部分还需要一题多维度 |

## 5. 单份量表任务模板

复制本表为目标量表建立任务，不得删除未通过项目。

| 子任务 | 状态 | 证据/命令 | 阻塞或回归问题 |
|---|---|---|---|
| INPUT 资料与授权 | `NOT_STARTED` | — | — |
| PACKAGE 源包与确定性校验 | `NOT_STARTED` | — | — |
| ENGINE 计分能力 | `NOT_STARTED` | — | — |
| RESULT 结果与风险规则 | `NOT_STARTED` | — | — |
| REPORT Web与报表布局 | `NOT_STARTED` | — | — |
| GOLDEN 全部样例 | `NOT_STARTED` | — | — |
| DATABASE PostgreSQL闭环 | `NOT_STARTED` | — | — |
| I18N 中日英 | `NOT_STARTED` | — | — |
| EXPORT Word/PDF/文本 | `NOT_STARTED` | — | — |
| SECURITY 租户/匿名/审计 | `NOT_STARTED` | — | — |
| APPROVAL 真实双审批 | `NOT_STARTED` | — | — |
| REGRESSION 全部历史量表 | `NOT_STARTED` | — | — |
| ACCEPTANCE 正式发布验收 | `NOT_STARTED` | — | — |

### 5.1 当前量表逐项状态

以下是已经登记的六份 active 量表版本（另含一份 SCL-90 历史草稿）的实际子任务状态。`TECHNICALLY_VERIFIED` 不等于真实专业审批；合成 E2E 审批只能证明技术工作流。

#### `SCALE-PHQ9-001` — `PHQ9_FREE_USE@pfizer-public-domain-severity-v1`

| 子任务 | 状态 | 证据/命令 | 阻塞或剩余问题 |
|---|---|---|---|
| INPUT 资料与授权 | `BLOCKED_EXTERNAL` | Pfizer/PHQ 官方公开资料、PHQ-9说明和原始验证来源已登记；原量表公开域声明可核验 | 中文/日文正式翻译及电子使用范围未确认；成人/其他人群、题9危机责任人/SLA和业务范围待审核 |
| PACKAGE 源包与确定性校验 | `COMPLETED` | `doc/scale-packages/phq9-v1-source-draft.json`；`validate_generic_scale_package.py`、注册表 SHA-256 PASS | 日文项目草稿不得替代有独立电子使用权的正式翻译 |
| ENGINE 计分能力 | `COMPLETED` | 共享 `GENERIC_SCORE_CALCULATOR:1`；9题0–3求和；受控单题 `scoreThreshold` 高风险规则 | 仅支持已声明的九题严重度版本，不包含未审查的功能影响附加题 |
| RESULT 结果与风险规则 | `COMPLETED` | 0–4/5–9/10–14/15–19/20–27 技术区间；第9题任意阳性触发 `PHQ9_ITEM9_POSITIVE`，只升高人工复核信号 | 结果解释、题9处置文本和风险分级需真实专业审核；不自动诊断或决定危机等级 |
| REPORT Web 与报表布局 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-130054`：高风险报告标题/描述/建议由三语同一 report 语义驱动 | 正式业务验收未完成 |
| GOLDEN 全部样例 | `COMPLETED` | 10/10 Golden PASS，含5/10/15/20边界、题9阳性、全高、缺失、非法选项 | 审批记录仍是隔离环境合成技术数据 |
| DATABASE PostgreSQL 闭环 | `COMPLETED` | 最新隔离 schema：导入、发布、题9高风险、完整 trace、报告和历史闭环 SQL PASS | 生产发布需真实审批；本次不修改生产 PostgreSQL |
| RUNTIME 暂存/提交/评分 | `COMPLETED` | 三语 questions、题9高风险提交、幂等、并发、追加式重评分 PASS | 题9人工处置入口仍等待真实责任人/SLA |
| I18N 中日英 | `IN_PROGRESS` | 三语草稿题目、结果、高风险文案、非诊断文本和日文 Web PASS | 正式翻译审校及日文电子使用权确认未完成 |
| EXPORT Word/PDF/文本 | `COMPLETED` | 同一高风险结果语义的 TEXT/PDF/Word 下载 PASS | 正式业务验收未完成 |
| SECURITY 租户/匿名/审计 | `COMPLETED` | 通用业务 E2E 8/8；同租户/跨租户/匿名/审计边界 PASS | 题9人工危机响应治理尚无真实责任人/SLA |
| APPROVAL 真实双审批 | `BLOCKED_EXTERNAL` | 隔离环境可执行合成 professional/business workflow | 缺真实专业资质、证据绑定和业务验收人；合成审批不升级正式状态 |
| REGRESSION 全部历史量表 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-130054`：PHQ-9/K6/WHO-5/GAD-7 required checks 全 PASS；SCL-90 保持 PARTIAL/7项 `NOT_RUN` | 总体报告仍为 `PARTIAL`，SCL-90 完整闭环未完成；不得把整体标记为 PASS |
| ACCEPTANCE 正式发布验收 | `BLOCKED_EXTERNAL` | 未进入生产发布 | 需完成翻译/电子权利、题9危机治理、真实专业双审批和业务签收 |

#### `SCALE-K6-001` — `K6_OFFICIAL_FREE_USE@official-self-admin-v1`

| 子任务 | 状态 | 证据/命令 | 阻塞或剩余问题 |
|---|---|---|---|
| INPUT 资料与授权 | `BLOCKED_EXTERNAL` | 官方来源包已登记 | 真实授权范围、目标人群切点和三语审校仍待确认 |
| PACKAGE 源包与确定性校验 | `COMPLETED` | `validate_scale_adaptation_registry.py`、K6 源包校验器 PASS | 新版本必须使用新版本号和 SHA-256 |
| ENGINE 计分能力 | `COMPLETED` | 通用 `GENERIC_SCORE_CALCULATOR:1`；6/6 Golden | 无 |
| RESULT 结果与风险规则 | `IN_PROGRESS` | 0–12/13–24 技术结果链路 PASS | 切点适用人群和正式解释仍需专业审核 |
| REPORT Web 与报表布局 | `COMPLETED` | 隔离 E2E 验证 Web、Word、PDF、文本 | 正式业务验收未完成 |
| GOLDEN 全部样例 | `COMPLETED` | 6/6 Golden PASS | 审批记录仍是合成技术数据 |
| DATABASE PostgreSQL 闭环 | `COMPLETED` | 注册表 SQL + 最新临时 schema `psy_e2e_1786697106_13841` | 生产发布需真实审批 |
| RUNTIME 暂存/提交/评分 | `COMPLETED` | 全量业务 E2E 与逐量表 SQL PASS | 无 |
| I18N 中日英 | `IN_PROGRESS` | 三语题目、结果、日文报告 PASS | 正式翻译审校未完成 |
| EXPORT Word/PDF/文本 | `COMPLETED` | K6 技术闭环导出 PASS | 正式业务验收未完成 |
| SECURITY 租户/匿名/审计 | `COMPLETED` | 通用隐私、跨租户、审计 E2E PASS | 无 |
| APPROVAL 真实双审批 | `BLOCKED_EXTERNAL` | 技术工作流可执行 | 缺真实专业资质、证据和业务验收人 |
| REGRESSION 全部历史量表 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509`：K6/K10/WHO-5/GAD-7/PHQ-9/SCL90-v2 全部 required checks PASS；结果、报表、幂等、并发、重评分和完整 trace 通过 | SCL-90 v1 历史草稿未进入 active run，不得与 v2 混算 |
| ACCEPTANCE 正式发布验收 | `BLOCKED_EXTERNAL` | 尚未进入生产发布 | 需真实审批和业务签收 |

#### `SCALE-K10-001` — `K10_OFFICIAL_FREE_USE@official-30day-5point-v1`

该版本使用 Harvard 官方 K10 自填资料和公开发布的中日文版本作为技术输入；版本锁定为 30 天/5 点显示选项，实际计算通过 `reverseScore` 将显示顺序重编码为 1–5。官方免费使用说明、引用要求和不同资料中的切点差异均记录为治理输入，技术通过不升级为正式支持。

| 子任务 | 状态 | 证据/命令 | 阻塞或剩余问题 |
|---|---|---|---|
| INPUT 资料与授权 | `BLOCKED_EXTERNAL` | Harvard K10/K6 页面、英文自填表、Mandarin/Japanese 表单、ABS/AIHW 评分资料已登记；页面注明可免费使用但须保留引用/版权说明 | 免费使用不等于免除引用和范围审查；不同资料的目标人群、时间窗和切点差异需专业确认 |
| PACKAGE 源包与确定性校验 | `COMPLETED` | `doc/scale-packages/k10-v1-source-official-draft.json`；`validate_generic_scale_package.py` 和注册表 SHA-256 `aa06c944cb3506337ef67ab56549f9f0a408bdbf5ae391041865748a6563659d` PASS | 源包仍是项目技术草稿，正式发布前不得省略来源声明 |
| ENGINE 计分能力 | `COMPLETED` | 共享 `GENERIC_SCORE_CALCULATOR:1`；10题、5个显示选项、`reverseScore`、1–5有效分、10–50总分；`K10_REVERSE_RECODE` Golden PASS | 仅锁定此版本的 1–5/30 天语义，不混用 0–4 canonical 变体 |
| RESULT 结果与风险规则 | `COMPLETED` | `K10_LOW` 10–19、`K10_MILD` 20–24、`K10_MODERATE` 25–29、`K10_SEVERE` 30–50；9/9 Golden 含 19/20/25/30 边界、缺失和非法选项 | 结果为非诊断技术解释；切点、人群范围、三语解释和安全提示待专业审核；无自动危机处置规则 |
| REPORT Web 与报表布局 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509`：三语 Web、日文结果以及 Word/PDF/TEXT 同一 report 语义 PASS | 正式业务验收未完成 |
| GOLDEN 全部样例 | `COMPLETED` | 9/9：ALL_NONE、19/20/25/30 边界、REVERSE、ALL_HIGH、MISSING_REQUIRED、INVALID_OPTION | Golden 是算法/边界证据，不是临床效度或常模证据 |
| DATABASE PostgreSQL 闭环 | `COMPLETED` | 隔离 schema `psy_e2e_1786715667_29939` 的注册表 SQL：源包/Golden、逐题 scoring trace、结果、三语、报表、任务锁、历史、幂等、并发、重评分全部 PASS；schema 已清理 | 未修改生产 PostgreSQL；真实发布仍需审批 |
| RUNTIME 暂存/提交/评分 | `COMPLETED` | 共享 generic closure 通过预览、发布、任务锁、三语提交 locale、10/50 总分和追加式重评分 | 只证明技术工作流，不代表个人研究结果的医学解释 |
| I18N 中日英 | `IN_PROGRESS` | 官方发布的英文/中文/日文技术文本已纳入包；三语 Web/结果/导出 PASS | 正式翻译审校、字符/电子使用范围和本地措辞验收未完成 |
| EXPORT Word/PDF/文本 | `COMPLETED` | 同一结果语义的 TEXT/PDF/Word 导出 PASS，含 CJK 字体兼容 | 正式业务验收未完成 |
| SECURITY 租户/匿名/审计 | `COMPLETED` | 共享通用 E2E 与注册表 SQL 的租户、匿名、审计边界 PASS | 无量表专属危机流程；安全治理仍需业务确认 |
| APPROVAL 真实双审批 | `BLOCKED_EXTERNAL` | 隔离 E2E 仅执行 disposable synthetic professional/business workflow | 缺真实专业资质、证据绑定和业务验收人；合成审批不升级正式状态 |
| REGRESSION 全部历史量表 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509`：PHQ-9/K6/K10/SCL90-v2/WHO-5/GAD-7 全部 required checks PASS；SCL90-v1 历史草稿不进入 active run | 历史草稿仍保留 PARTIAL/NOT_RUN，不得与 v2 混算 |
| ACCEPTANCE 正式发布验收 | `BLOCKED_EXTERNAL` | 未进入正式生产发布 | 等待正式三语、适用人群/切点、专业双审批、业务验收和引用/使用范围归档 |

#### `SCALE-WHO5-001` — `WHO5_WELL_BEING@who-2024-open-access-v1`

| 子任务 | 状态 | 证据/命令 | 阻塞或剩余问题 |
|---|---|---|---|
| INPUT 资料与授权 | `BLOCKED_EXTERNAL` | WHO 来源包、英中日资料已登记 | CC BY-NC-SA 使用范围和本地业务用途仍待确认 |
| PACKAGE 源包与确定性校验 | `COMPLETED` | WHO-5 源包校验器及注册表 SHA-256 PASS | 新版本必须使用新版本号和 SHA-256 |
| ENGINE 计分能力 | `COMPLETED` | 求和 + `WHO5_PERCENTAGE_SCORE`；6/6 Golden | 无 |
| RESULT 结果与风险规则 | `IN_PROGRESS` | 0–12/13–25 技术结果、52% 指标 PASS | 切点适用人群、解释和建议需专业审核 |
| REPORT Web 与报表布局 | `COMPLETED` | 日文 Web、Word、PDF、文本语义 PASS | 正式业务验收未完成 |
| GOLDEN 全部样例 | `COMPLETED` | 6/6 Golden PASS | 审批记录仍是合成技术数据 |
| DATABASE PostgreSQL 闭环 | `COMPLETED` | 发布、任务锁版、历史结果 SQL PASS；最新临时 schema `psy_e2e_1786697106_13841` | 生产发布需真实审批 |
| RUNTIME 暂存/提交/评分 | `COMPLETED` | 13 分、52% 指标、历史结果保护 PASS | 无 |
| I18N 中日英 | `IN_PROGRESS` | 三语题目、结果、报告 PASS | 正式三语审校未完成 |
| EXPORT Word/PDF/文本 | `COMPLETED` | WHO-5 技术闭环导出 PASS | 正式业务验收未完成 |
| SECURITY 租户/匿名/审计 | `COMPLETED` | 通用隐私、跨租户、审计 E2E PASS | 无 |
| APPROVAL 真实双审批 | `BLOCKED_EXTERNAL` | 技术工作流可执行 | 当前审批是隔离环境合成审批 |
| REGRESSION 全部历史量表 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509`：PHQ-9/K6/K10/SCL90-v2/WHO-5/GAD-7 全部 required checks PASS；结果、报表、幂等、并发、重评分和完整 trace 通过 | SCL-90 v1 历史草稿未进入 active run |
| ACCEPTANCE 正式发布验收 | `BLOCKED_EXTERNAL` | 尚未进入生产发布 | 需授权、专业审核和业务签收 |

#### `SCALE-SCL90-002` — `SCL90_USER_AUTHORIZED@authorized-profile-v1`

这是用户确认的个人/内部研究范围下的受限技术版本。它是版本化、可回归的 `ScalePackage`，但不把内部使用描述升级为公开授权，也不加载未经审校的常模或临床切点。

| 子任务 | 状态 | 证据/命令 | 阻塞或剩余问题 |
|---|---|---|---|
| INPUT 资料与授权 | `BLOCKED_EXTERNAL` | 用户确认个人自我观察、非商用算法研究；官方 Pearson 产品/权限/量表资料已登记为技术边界参考 | 授权范围需归档并核对原始资料；内部非商用不自动等于复制、改编或翻译许可 |
| PACKAGE 源包与确定性校验 | `COMPLETED` | `doc/scale-packages/scl90-v2-source-technical.json`；90题、10维度、3语言、SHA-256 `fa9580b9e5a5f3f926614e1b54a0715ad58dc77e00bdb0a59de2b37ad25b90f9`；专用 validator PASS | zh-CN/ja-JP 仍是技术草稿，不是正式翻译声明 |
| ENGINE 计分能力 | `COMPLETED` | `SCL90_PROFILE:1` restricted extension；0–4 原始分，十维 `AVERAGE`，GSI/PST/PSDI；共享 scoring trace 与 90 item trace PASS | 仅实现资料明确的 profile-only 算法，不推断未提供的版本或常模 |
| RESULT 结果与风险规则 | `COMPLETED` | 单一 `SCL90_PROFILE_ONLY`（0–360、RAW_SCORE、NORMAL）及 item 15/63 高风险信号；5/5 Golden PASS | 不含未经批准的临床切点/诊断；专业解释、危机责任人/SLA待审核 |
| REPORT Web 与报表布局 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509`：三语 Web、日文结果、profile-only 语义及 NORMATIVE_PROFILE 模板 PASS | 专业双审批和业务验收仍阻塞正式发布 |
| GOLDEN 全部样例 | `COMPLETED` | SCL90_ALL_ZERO、ALL_FOUR、SELF_HARM_SIGNAL、MISSING_REQUIRED、INVALID_OPTION 5/5 PASS | Golden 是技术边界，不是临床效度证明 |
| DATABASE PostgreSQL 闭环 | `COMPLETED` | 专用 `assert-scl90-registry-closure.sql`：发布包、90题/10维、算法、逐题/维度 trace、GSI/PST/PSDI、历史报告、版本锁定、幂等/并发/重评分 PASS | 仅隔离 schema；不修改生产 PostgreSQL |
| RUNTIME 暂存/提交/评分 | `COMPLETED` | 90题三语 payload、ALL_FOUR 提交、风险信号、rescore/history PASS | 高风险仅为人工复核信号，尚无真实危机流程 |
| I18N 中日英 | `IN_PROGRESS` | 三语题目/选项/维度/结果/高风险/非诊断文案及日文 Web/导出 PASS | 正式三语审校和翻译权未完成 |
| EXPORT Word/PDF/文本 | `COMPLETED` | 同一 report 语义驱动 TEXT/PDF/Word PASS；共享字体集合 PDF OTF 子集化缺陷已修复 | 正式业务验收未完成 |
| SECURITY 租户/匿名/审计 | `COMPLETED` | 全量通用 E2E 8/8，SCL SQL 租户/任务锁/幂等/并发/审计断言 PASS | 高风险人工处置责任与 SLA 尚未指定 |
| APPROVAL 真实双审批 | `BLOCKED_EXTERNAL` | 隔离 E2E 仅执行 disposable synthetic professional/business workflow | 缺真实专业资质、证据绑定、业务验收；合成审批不升级正式状态 |
| REGRESSION 全部历史量表 | `COMPLETED` | `REG-PLAYWRIGHT-20260814-135509`：PHQ-9/K6/K10/SCL90-v2/WHO-5/GAD-7 active entries 全部 required checks PASS | SCL90-v1 历史草稿仍为 PARTIAL，不得将其历史 `NOT_RUN` 与 v2 混算 |
| ACCEPTANCE 正式发布验收 | `BLOCKED_EXTERNAL` | 未进入正式生产发布 | 等待授权范围、翻译审校、专业双审批、常模/危机治理和业务签收 |

#### `SCALE-SCL90-001` — `SCL90_USER_DRAFT@v1`

| 子任务 | 状态 | 证据/命令 | 阻塞或剩余问题 |
|---|---|---|---|
| INPUT 资料与授权 | `BLOCKED_EXTERNAL` | 90 题草稿和公开参考已登记 | 正式版权/授权、正式三语、常模和危机责任人缺失 |
| PACKAGE 源包与确定性校验 | `COMPLETED` | 90 题、10 维度、3 语言、5 Golden 校验 PASS | 草稿不可作为正式授权证明 |
| ENGINE 计分能力 | `COMPLETED` | 受限 `SCL90_PROFILE:1`；GSI/PST/PSDI trace PASS | 正式手册和常模仍待审核 |
| RESULT 结果与风险规则 | `BLOCKED_EXTERNAL` | 仅保留指标和风险信号草稿 | 缺正式总体结果区间、常模和处置规则 |
| REPORT Web 与报表布局 | `BLOCKED_EXTERNAL` | 未创建可发布报告链路 | 结果解释和模板必须由专业人员确认 |
| GOLDEN 全部样例 | `COMPLETED` | 5/5 Golden PASS；3 个有效 trace 关键值 PASS | 尚无正式结果边界 Golden |
| DATABASE PostgreSQL 闭环 | `PARTIALLY_SUPPORTED` | 草稿导入、三语结构和算法绑定 PASS | 不允许创建正式发布任务 |
| RUNTIME 暂存/提交/评分 | `PARTIALLY_SUPPORTED` | Golden 评分可运行 | 正式任务、报告和历史锁版保持 `NOT_RUN` |
| I18N 中日英 | `IN_PROGRESS` | 草稿三语导入 PASS | 正式翻译审校未完成 |
| EXPORT Word/PDF/文本 | `BLOCKED_EXTERNAL` | 未执行 | 没有正式结果语义不能生成可发布报表 |
| SECURITY 租户/匿名/审计 | `BLOCKED_EXTERNAL` | 通用边界有覆盖 | SCL-90 高风险人工处置链路尚无责任人/SLA |
| APPROVAL 真实双审批 | `BLOCKED_EXTERNAL` | 发布门禁保持阻断 | 缺版权、专业、业务和危机治理证据 |
| REGRESSION 全部历史量表 | `PARTIALLY_SUPPORTED` | `REG-PLAYWRIGHT-20260814-094823`：源包/Golden/trace PASS，7 项 `NOT_RUN` | 不得把 `PARTIAL` 当作 PASS |
| ACCEPTANCE 正式发布验收 | `BLOCKED_EXTERNAL` | 未进入发布 | 等待外部输入后再创建新版本或补闭环 |

## 6. 回归运行记录

每次运行新增一行。旧 PASS 不得自动代表新提交仍通过；未实际执行写 `NOT_RUN`。

| Run ID | 日期 | 分支/提交 | 目标变化 | 注册量表数 | 后端/PostgreSQL | Web | Playwright | 逐量表结果 | 工件 | 结论 |
|---|---|---|---|---:|---|---|---|---|---|---|
| `REG-RUN-20260813-01` | 2026-08-13 | `feature/redo` / worktree at `44289a0` | K6技术闭环、SCL-90草稿和通用能力 | 尚无统一注册表 | 434 tests PASS | 104 tests + build PASS | 10/10 PASS | K6技术 PASS；SCL-90发布保持 BLOCKED | Gradle/Playwright 本地报告 | 历史记录；不能证明后续提交 |
| `REG-STATIC-20260814-145544` | 2026-08-14 | 当前工作树；更新注册表状态门禁和 E2E 失败工件入口 | 3 个注册包 | 未运行 PostgreSQL/后端（当前沙箱网络阻断） | `npm run build` PASS；Vitest 13 files / 104 tests PASS | 未运行 Playwright；入口预检失败工件 JSON 已验证 | 注册表校验、三份源包校验、Python 编译、Shell 语法和失败 JSON schema PASS | [失败工件](../../build/reports/scale-adaptation/failures/psy_e2e_failure_artifact_145500.json)；完整技术证据仍以 `REG-PLAYWRIGHT-20260814-064135` 为准 | 静态/UI 检查通过；PostgreSQL 全量回归等待允许访问数据库的环境 |

注册表元数据校验和技术回归已分别执行；元数据校验本身不计入全量回归：

| 校验 ID | 日期 | 注册表 | 命令 | 结果 |
|---|---|---|---|---|
| `REG-META-20260814-01` | 2026-08-14 | 3 个包 | `python3 scripts/validate_scale_adaptation_registry.py` | PASS；未运行 PostgreSQL、评分、Web 或导出回归 |
| `REG-META-20260814-02` | 2026-08-14 | 4 个包 | `python3 scripts/validate_scale_adaptation_registry.py` | PASS；K6、SCL-90、WHO-5、GAD-7 的源包哈希、Golden 期望哈希、状态和执行器绑定一致；不替代运行时回归 |
| `REG-META-20260814-03` | 2026-08-14 | 5 个包 | `python3 scripts/validate_scale_adaptation_registry.py` | PASS；PHQ-9、K6、SCL-90、WHO-5、GAD-7 的源包哈希、Golden 期望哈希、状态和执行器绑定一致；PHQ-9 技术状态与 `REG-PLAYWRIGHT-20260814-130054` 一致；不替代运行时回归 |
| `REG-META-20260814-04` | 2026-08-14 | 6 个版本化包（5 active 技术回归项） | `python3 scripts/validate_scale_adaptation_registry.py` | PASS；SCL90-v2 `TECHNICALLY_VERIFIED` 绑定 `REG-PLAYWRIGHT-20260814-132924`；SCL90-v1 明确为历史 `PARTIALLY_SUPPORTED` 且不进入 active run；不替代运行时回归 |
| `REG-META-20260814-05` | 2026-08-14 | 7 个版本化包（6 active 技术回归项） | `python3 scripts/validate_scale_adaptation_registry.py` | PASS；K10 `TECHNICALLY_VERIFIED` 绑定 `REG-PLAYWRIGHT-20260814-135509`；6个 active entry 均绑定该全量 PASS，SCL90-v1 明确为历史 `PARTIALLY_SUPPORTED` 且不进入 active run；不替代运行时回归 |

已建立注册表驱动的源包执行入口，但该入口不会把未执行的运行时检查标成通过：

| Run ID | 日期 | 命令 | 逐量表结果 | 未执行检查 | 结论 |
|---|---|---|---|---:|---|
| `REG-SOURCE-20260813-163008` | 2026-08-14（UTC 运行时间为 2026-08-13） | `python3 scripts/run_scale_adaptation_registry.py --mode source --report /tmp/scale-adaptation-source-regression-20260814.json` | K6 `SOURCE_VALIDATION_PASS`；SCL-90 `SOURCE_VALIDATION_PASS` | 每个量表 6 项运行时检查 | 源包完整性通过；不是 PostgreSQL、评分 trace、三语结果、报表、历史锁版或临床审批结论 |
| `REG-SOURCE-20260813-164829` | 2026-08-14（UTC 运行时间为 2026-08-13） | `python3 scripts/run_scale_adaptation_registry.py --mode source --report /tmp/scale-adaptation-source-regression-20260814-v3.json` | K6 `SOURCE_VALIDATION_PASS`；SCL-90 `SOURCE_VALIDATION_PASS`（5 Golden） | 每个量表 7 项 required checks 中 6 项运行时检查 | 源包完整性和注册表 required-check 名称通过；不是 PostgreSQL、评分 trace、三语结果、报表、历史锁版或临床审批结论 |
| `REG-SOURCE-20260814-102111` | 2026-08-14 | `python3 scripts/run_scale_adaptation_registry.py --mode source --report /tmp/lx-boot-scale-source-final-gad7.json` | 4 份源包全部 `SOURCE_VALIDATION_PASS`；GAD-7 8 个 Golden Case | 每个量表 10 项运行时检查均未在此命令执行 | 仅证明当前源包和注册表静态一致；运行时证据使用 `REG-PLAYWRIGHT-20260814-101521` |
| `REG-SOURCE-20260814-123851` | 2026-08-14 | 代码/文档收尾快速校验；`python3 scripts/run_scale_adaptation_registry.py --mode source --report /tmp/lx-boot-scale-source-final-check.json` | K6、SCL-90、WHO-5、GAD-7 全部 `SOURCE_VALIDATION_PASS`；注册表校验和 `git diff --check` PASS | PostgreSQL、Playwright、评分、结果和报表运行时检查未重复执行 | 本轮没有修改计分、结果、报表、迁移或数据库闭环代码，按收尾约定复用不可变指纹一致的 `REG-PLAYWRIGHT-20260814-101521`；不得把本行当作新的运行时 PASS |
| `REG-PLAYWRIGHT-20260813-163236` | 2026-08-14（UTC 运行时间为 2026-08-13） | `scripts/run-scale-package-e2e.sh`；隔离 schema + Java 21 + Playwright；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786638714_91673.json` | K6 `PASS`（6/6 Golden）；SCL-90 `PASS`（4/4 Golden）；Playwright 2/2；其余通用 E2E 8/8 | 每个量表尚未由执行器独立采集 6 项 scoring/result/report/lock/immutability 结构化断言 | 隔离 PostgreSQL/应用/Web 技术路径通过；不能标记量表 FULLY_SUPPORTED，也不能替代专业/版权/业务审批 |
| `REG-PLAYWRIGHT-20260813-163757` | 2026-08-14（UTC 运行时间为 2026-08-13） | `scripts/run-scale-package-e2e.sh`；隔离 schema + Java 21 + Playwright；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786639036_92152.json` | K6 `PASS`（6/6 Golden）；SCL-90 `PASS`（5/5 Golden）；Playwright 2/2；其余通用 E2E 8/8 | 每个量表尚未由执行器独立采集 6 项 scoring/result/report/lock/immutability 结构化断言 | 隔离 PostgreSQL/应用/Web 技术路径通过；不能标记量表 FULLY_SUPPORTED，也不能替代专业/版权/业务审批 |
| `REG-PLAYWRIGHT-20260813-164604` | 2026-08-14（UTC 运行时间为 2026-08-13） | `scripts/run-scale-package-e2e.sh`；隔离 schema + Java 21 + Playwright + 注册表 PostgreSQL 证据；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786639521_92897.json` | K6 `PASS`（6/6 Golden；7 项注册检查全部 PASS）；SCL-90 `PASS`（5/5 Golden；源包/Golden/数据库导入 PASS，5 项运行时检查仍 `NOT_RUN`）；Playwright 2/2；通用 E2E 8/8 | SCL-90 的结果/报表/任务锁版/历史兼容尚未执行，因为它仍是 DRAFT 且无正式结果区间 | K6 技术回归闭环通过；SCL-90 技术候选回归透明保持部分支持；不能标记任何量表 FULLY_SUPPORTED |
| `REG-PLAYWRIGHT-20260813-164932` | 2026-08-14（UTC 运行时间为 2026-08-13） | 最终本地验证：`scripts/run-scale-package-e2e.sh`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786639729_93282.json` | K6 `PASS`（6/6 Golden；7/7 required checks PASS，`runtimeChecksNotExecuted=0`）；SCL-90 `PASS`（5/5 Golden；源包/Golden/数据库导入 PASS，5 项 required checks `NOT_RUN`）；Playwright 2/2；通用 E2E 8/8；Java 21 bootJar 成功 | SCL-90 的 scoring trace、结果/报表、任务锁版、历史兼容继续未执行；报告显式保留 `NOT_RUN` | K6 注册表技术闭环通过；SCL-90 保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；未宣称临床、版权或业务审批 |
| `REG-PLAYWRIGHT-20260813-200115` | 2026-08-14（UTC 运行时间为 2026-08-13） | 当前提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786651234_95049`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786651234_95049.json` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（6/6 Golden；7/7 required checks PASS，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包、5/5 Golden 和草稿导入 PASS；5 项运行时 required checks `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS | SCL-90 没有正式总体结果区间和可发布任务，故评分 trace、正式三语结果、报表语义、任务锁版、历史结果保护保持 `NOT_RUN`；这不是失败，也不是正式支持 | K6 技术闭环未回归破坏；SCL-90 继续保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；外部授权、专业审核、业务验收仍未建立 |
| `REG-PLAYWRIGHT-20260814-040217` | 2026-08-14 | 当前提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786680096_97178`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786680096_97178.json` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden PASS，5 项运行时检查 `NOT_RUN`）；WHO-5 `PARTIAL`（源包/6 Golden/4 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS | WHO-5 当时仍是 DRAFT，因此三语结果、Web/Word/PDF/文本报表、任务锁版和历史不变性未执行；SCL-90 保持原阻塞；8 个通用业务 E2E 全部通过 | K6 既有技术闭环未被 WHO-5 通用指标改动破坏；该记录已被后续 WHO-5 完整技术回归 supersede；Android 排除 |
| `REG-PLAYWRIGHT-20260814-041120` | 2026-08-14 | 当前提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786680635_97711`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786680635_97711.json` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden PASS，5 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS | SCL-90 仍无正式总体结果区间/可发布任务，因此未执行其 scoring trace、结果/报表/锁版/历史检查；WHO-5 全链路技术证据来自隔离环境合成审批，不能替代真实专业/业务审批 | K6 既有技术闭环未被 WHO-5 适配破坏；WHO-5 达到 `TECHNICALLY_VERIFIED/BLOCKED_EXTERNAL`；Android 排除；WHO-5 真实授权范围、专业审核、业务验收和切点适用人群仍未建立 |
| `REG-PLAYWRIGHT-20260814-041808` | 2026-08-14 | 当前提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786681046_98413`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786681046_98413.json` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行，因为没有正式可发布结果规则；WHO-5 全链路技术证据来自隔离环境合成审批，不能替代真实专业/业务审批 | K6 和 WHO-5 技术闭环未被 SCL-90 scoring trace 断言改动破坏；SCL-90 仍保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；外部授权、专业审核、业务验收仍未建立 |
| `REG-PLAYWRIGHT-20260814-042317` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786681356_98947`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786681356_98947.json`；不可变注册表指纹 `24ad17f6bc13a2b745236ab20dc76c2b5ed34e7a62bffc73b7d18f2c639ae888` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行，因为没有正式可发布结果规则；WHO-5 全链路技术证据来自隔离环境合成审批，不能替代真实专业/业务审批 | 注册表不可变指纹与报告一致；K6 和 WHO-5 技术闭环未被 SCL-90 scoring trace 断言改动破坏；SCL-90 仍保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；外部授权、专业审核、业务验收仍未建立 |
| `REG-PLAYWRIGHT-20260814-043041` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786681799_99586`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786681799_99586.json`；不可变注册表指纹 `24ad17f6bc13a2b745236ab20dc76c2b5ed34e7a62bffc73b7d18f2c639ae888` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS；新增 SCL-90 trace 关键值断言通过 | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行，因为没有正式可发布结果规则；WHO-5 全链路技术证据来自隔离环境合成审批，不能替代真实专业/业务审批 | 注册表不可变指纹与报告一致；K6 和 WHO-5 技术闭环未被 SCL-90 精确 trace 断言改动破坏；SCL-90 仍保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；外部授权、专业审核、业务验收仍未建立 |
| `REG-PLAYWRIGHT-20260814-061621` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786688139_910`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786688139_910.json`；不可变注册表指纹 `b3821cfeccb25c21f8ecc3a5d5a67d1a7547b2a92551a8f8a559190b5447bca9` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS；注册表 expected 元数据和精确 SCL-90 trace 断言均通过 | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行，因为没有正式可发布结果规则；WHO-5 全链路技术证据来自隔离环境合成审批，不能替代真实专业/业务审批 | 注册表不可变指纹与报告一致；K6 和 WHO-5 技术闭环未被注册表 expected 元数据和 SCL-90 精确 trace 断言改动破坏；SCL-90 仍保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；外部授权、专业审核、业务验收仍未建立 |
| `REG-PLAYWRIGHT-20260814-062042` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786688403_1352`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786688403_1352.json`；不可变注册表指纹 `b3821cfeccb25c21f8ecc3a5d5a67d1a7547b2a92551a8f8a559190b5447bca9` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS；报告已自包含源包 SHA、算法、报表模板、三语和 expected 元数据 | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行，因为没有正式可发布结果规则；WHO-5 全链路技术证据来自隔离环境合成审批，不能替代真实专业/业务审批 | 注册表不可变指纹与报告一致；收紧 requiredChecks 和报告自包含元数据未破坏 K6/WHO-5 技术闭环；SCL-90 仍保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；外部授权、专业审核、业务验收仍未建立 |
| `REG-PLAYWRIGHT-20260814-063539` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786689299_2899`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786689299_2899.json`；不可变注册表指纹 `b3821cfeccb25c21f8ecc3a5d5a67d1a7547b2a92551a8f8a559190b5447bca9` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS；新增 K6/WHO-5 三语结果 locale、结果标题、完整 scoring trace 字段和历史结果断言通过 | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行，因为没有正式可发布结果规则；K6/WHO-5 外部授权、真实专业双审批和业务验收仍未建立 | K6/WHO-5 `runtimeChecksNotExecuted=0` 且 7/7 required checks PASS；SCL-90 保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；本次回归未修改生产数据库 |
| `REG-PLAYWRIGHT-20260814-064135` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；`scripts/run-scale-package-e2e.sh`；临时 PostgreSQL schema `psy_e2e_1786689654_3470`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786689654_3470.json`；不可变注册表指纹 `b3821cfeccb25c21f8ecc3a5d5a67d1a7547b2a92551a8f8a559190b5447bca9` | Java 21 `bootJar` 成功；通用 Playwright 8/8；K6 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；WHO-5 `PASS`（7/7 required checks，`runtimeChecksNotExecuted=0`）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 个有效 scoring trace PASS，4 项运行时检查 `NOT_RUN`）；注册表总体 `PARTIAL`；PostgreSQL core/publication closure PASS；K6/WHO-5 新增每题原始/反向/加权/有效分、缺失策略、折算因子、维度聚合和派生指标的完整 trace 断言通过 | SCL-90 的三语正式结果、报表语义、任务锁版和历史结果保护仍未执行；K6/WHO-5 外部授权、真实专业双审批和业务验收仍未建立 | K6/WHO-5 `runtimeChecksNotExecuted=0` 且 7/7 required checks PASS；SCL-90 保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL`；Android 排除；本次回归未修改生产数据库 |
| `REG-ATTEMPT-20260814-144922` | 2026-08-14 | 当前工作树；执行 `JAVA_HOME=/opt/homebrew/opt/openjdk@21 GRADLE_USER_HOME=/Users/sainm/.gradle scripts/run-scale-package-e2e.sh`；尚未创建 schema | 在脚本创建临时 schema 前，`psql localhost:5432` 返回 `Operation not permitted`；后端、Web、Playwright、注册表和 PostgreSQL 证据均为 `NOT_RUN`；未生成报告工件 | 当前沙箱禁止本次 PostgreSQL 网络连接，属于执行环境阻断，不是量表或应用断言结果；需在允许访问本地 PostgreSQL 的环境重试 | 不得把本次尝试当作回归 PASS/FAIL；保留上一份可用报告 `REG-PLAYWRIGHT-20260814-064135` 作为当前代码证据；未修改数据库 |
| `REG-ATTEMPT-20260814-145242` | 2026-08-14 | 当前工作树；显式 schema `psy_e2e_failure_artifact_144922`；执行更新后的 `scripts/run-scale-package-e2e.sh` | 同一 PostgreSQL 前置阻断在 `create_isolated_schema` 阶段复现；退出码 2；注册表和量表检查均为 `NOT_RUN`；脚本已保存失败 JSON `build/reports/scale-adaptation/failures/psy_e2e_failure_artifact_144922.json` | 环境阻断可被机器读取，JSON 明确 `businessDataChanged=false`、阶段和退出码；仍需在允许 PostgreSQL 访问的环境做正式重跑 | 不得把预检失败当作量表 FAIL；上一份 `REG-PLAYWRIGHT-20260814-064135` 仍是最新完整回归证据；未修改数据库 |
| `REG-ATTEMPT-20260814-145533` | 2026-08-14 | 当前工作树；显式 schema `psy_e2e_failure_artifact_145500`；再次执行 `scripts/run-scale-package-e2e.sh` | PostgreSQL 前置阻断再次在 `create_isolated_schema` 阶段复现；退出码 2；未启动后端/Web；失败 JSON `build/reports/scale-adaptation/failures/psy_e2e_failure_artifact_145500.json` 已生成 | 失败入口现在稳定保存阶段、退出码、临时目录、Android 排除和 `businessDataChanged=false`；正式 PostgreSQL 回归仍需在允许连接的环境重试 | 保留旧完整回归报告；不修改量表期望或数据库 |
| `REG-STATIC-20260814-150631` | 2026-08-14 | 当前工作树；结果标题闭环改动后 | 3 个注册包 | 后端 Gradle 未能启动：沙箱拒绝 Gradle native/file-lock socket；未运行 PostgreSQL | `npm run build` PASS；Vitest 13 files / 104 tests PASS；`git diff --check`、注册表/三份源包校验、Python 编译和 Shell 语法 PASS | 未运行 Playwright；结果标题的 ReportDetail、Web、Word/PDF/TEXT、API 和重生成断言只完成静态代码和测试源码更新，必须在允许 Gradle/PostgreSQL 的环境执行 | 不得用本次静态结果替代后端或隔离 PostgreSQL 回归；最新完整业务证据仍为 `REG-PLAYWRIGHT-20260814-064135`，且早于本次结果标题暴露改动 |
| `REG-ATTEMPT-20260814-161814` | 2026-08-14 | 当前工作树；结果标题改动后的环境复核 | 3 个注册包 | `lsof` 仍确认 PostgreSQL 在 127.0.0.1/::1:5432 监听，但 `pg_isready`/`psql` 被沙箱返回 `Operation not permitted`；Gradle 8.14 wrapper 在 lock 文件创建前阻断，Gradle 7.6.4 直接运行又在 daemon ServerSocket bind 阶段阻断 | Web/静态验证未受影响；本次未启动后端、未创建 schema、未运行 Playwright | 环境前置失败，不是数据库服务或量表断言失败；无业务数据影响 | 继续在允许本机网络和 Gradle socket 的本地/CI 环境重跑；不修改注册表期望值 | 保留 `REG-PLAYWRIGHT-20260814-064135` 作为改动前完整证据；未修改数据库 |
| `REG-PLAYWRIGHT-20260814-084548` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；Java 21；PostgreSQL 18.4；`scripts/run-scale-package-e2e.sh`；隔离 schema `psy_e2e_1786697106_13841`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786697106_13841.json`；不可变注册表指纹 `b3821cfeccb25c21f8ecc3a5d5a67d1a7547b2a92551a8f8a559190b5447bca9` | 结果标题贯通 ReportDetail/API/Web/Word/PDF/TEXT；3 个注册包 | 后端 `./gradlew test --no-daemon` BUILD SUCCESSFUL；隔离 PostgreSQL core/publication closure PASS | `npm run build` PASS；Vitest 13 files / 104 tests PASS | 通用业务 E2E 8/8；K6、SCL-90、WHO-5 Playwright 选择器各 1/1 PASS | K6 `PASS`（7/7，0 NOT_RUN）；WHO-5 `PASS`（7/7，0 NOT_RUN）；SCL-90 `PARTIAL`（源包/5 Golden/草稿导入/3 trace PASS，4 NOT_RUN） | [注册表报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786697106_13841.json)；Word/PDF/TEXT 语义由后端抽取测试验证 | 当前结果标题改动与既有 K6/WHO-5 均未发生回归；SCL-90 仍因外部资料保持部分支持；Android 排除；隔离 schema 清理，不修改生产数据库 |
| `REG-PLAYWRIGHT-20260814-094823` | 2026-08-14 | 当前工作树基线提交 `9ef1aea`；Java 21；PostgreSQL 18.4；`scripts/run-scale-package-e2e.sh`；隔离 schema `psy_e2e_1786700863_15884`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786700863_15884.json`；不可变注册表指纹 `4099ab9e0656ab82f58ee945e115468d8805fab5783f7fae542b460bb60659a5` | K6/WHO-5 迁移至共享 `GENERIC_SINGLE_CHOICE`；逐量表 required checks 从 7 扩展到 10 | Java 21 `bootJar` PASS；隔离 PostgreSQL core/publication closure PASS | Web build PASS；通用业务 E2E 8/8 | 共享通用闭环 K6 1/1、WHO-5 1/1；SCL-90 草稿 1/1 | K6 `PASS`（10/10，0 NOT_RUN）；WHO-5 `PASS`（10/10，0 NOT_RUN）；SCL-90 `PARTIAL`（3 PASS，7 NOT_RUN） | [注册表报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786700863_15884.json)；失败前驱报告完整保留 | 已知通用单选量表可通过包+注册表 closure Case 复用完整技术链路；未修改生产数据库；Android 排除；外部审批状态不变 |
| `REG-PLAYWRIGHT-20260814-101521` | 2026-08-14 | 当前工作树基线提交`9ef1aea`；Java 21；PostgreSQL 18.4；隔离schema `psy_e2e_1786702480_17887`；报告`build/reports/scale-adaptation/registry-psy_e2e_1786702480_17887.json`；不可变注册表指纹`40e924f826823a19bb8fafe1f97ce86155c9ccf11a3168286c99c56482149ed7` | 新增GAD-7；K6/WHO-5/GAD-7统一到共享源包校验、Playwright及PostgreSQL证据；SCL-90专用证据拆离；匿名通知证据改为JSON ID精确匹配 | Java 21 `bootJar` PASS；Flyway空schema安装、core/publication closure及全部逐量表PostgreSQL证据PASS | 通用业务E2E 8/8；Web运行正常 | K6、WHO-5、GAD-7共享闭环各1/1；SCL-90草稿1/1 | K6 `PASS` 10/10；WHO-5 `PASS` 10/10；GAD-7 `PASS` 10/10；SCL-90 `PARTIAL` 3 PASS+7 NOT_RUN | [注册表报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786702480_17887.json)；前驱失败报告`registry-psy_e2e_1786702059_16936.json`及`registry-psy_e2e_1786702218_17290.json`保留 | 三份通用量表无量表专属业务/E2E/SQL分支；所有隔离schema已清理，未改生产数据库；合成审批不替代真实审批；Android排除 |
| `REG-STATIC-20260814-203045` | 2026-08-14 | `REG-PLAYWRIGHT-20260814-101521` 后的最终文档与源码收口 | 4 个注册包；README 章节归属修正 | Java 21 `./gradlew test --no-daemon` BUILD SUCCESSFUL；未重复运行 PostgreSQL | `npm run build` PASS；Vitest 13 files / 104 tests PASS | 未重复运行；沿用同一注册表指纹的 `REG-PLAYWRIGHT-20260814-101521` | 注册表/4 份源包、Python 编译、Shell 语法、`git diff --check` PASS；当前指纹与完整报告一致 | [完整运行报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786702480_17887.json) | 文档收口未改变不可变回归输入；隔离 schema 数量复核为 0；未修改生产数据库；Android 排除 |
| `REG-SOURCE-20260814-125844` | 2026-08-14 | 当前工作树；PHQ-9 包、注册表和共享高风险校验器 | 5 个注册包 | 未运行 PostgreSQL/Playwright | Python 源包/注册表校验全部 `SOURCE_VALIDATION_PASS`；PHQ-9 10 Golden Case | 每个量表10项运行时检查均未在此命令执行 | [源包报告](/tmp/lx-boot-scale-source-phq9-v2.json)；不替代运行时回归或审批 | 仅证明源包结构、SHA、Golden expectation 和 profile 绑定一致 |
| `REG-PLAYWRIGHT-20260814-125806` | 2026-08-14 | 当前工作树；首次 PHQ-9 全量尝试；临时 schema `psy_e2e_1786712245_22983`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786712245_22983.json` | 新增 PHQ-9 题9高风险 profile | Java 21 bootJar PASS；core 未到达 | 通用业务 E2E 8/8；PHQ-9 selector FAIL；K6/WHO-5/GAD-7 PASS；SCL-90 PARTIAL | PHQ-9 preview/import 在写入 issue code 时因 `JAPANESE_ELECTRONIC_USE_RIGHTS_REVIEW_PENDING` 超过 `varchar(64)` 返回500，PHQ-9其余检查 `NOT_RUN` | [失败报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786712245_22983.json) | 配置/字段长度缺陷，不是计分变化；隔离 schema 清理，无生产数据影响；未改旧 Golden |
| `REG-PLAYWRIGHT-20260814-125929` | 2026-08-14 | 当前工作树；缩短阻塞码后的 PHQ-9 全量尝试；临时 schema `psy_e2e_1786712329_23331`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786712329_23331.json` | PHQ-9 高风险结果断言 | Java 21 bootJar PASS；core 未到达 | 通用业务 E2E 8/8；PHQ-9 selector FAIL；K6/WHO-5/GAD-7 PASS；SCL-90 PARTIAL | 题9阳性将最终 riskLevel 提升为 HIGH，但总分1的基础结果规则仍为0–4 NORMAL；共享 E2E 用最终 riskLevel 查基础结果，断言在运行前失败 | [失败报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786712329_23331.json) | 测试断言缺陷，不是评分器/数据库回归；隔离 schema 清理，无生产数据影响；未改期望 |
| `REG-PLAYWRIGHT-20260814-130054` | 2026-08-14 | 当前工作树；PHQ-9 corrected profile；Java 21；PostgreSQL 18.4；临时 schema `psy_e2e_1786712413_23649`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786712413_23649.json`；注册表 SHA 指纹随报告记录 | PHQ-9 五份量表接入、共享单题高风险校验和全量兼容回归 | Java 21 bootJar PASS；Flyway 空 schema、core/publication closure PASS；未修改生产数据库 | Web build PASS；通用业务 Playwright 8/8 | PHQ-9 `PASS` 10/10；K6 `PASS` 10/10；WHO-5 `PASS` 10/10；GAD-7 `PASS` 10/10；SCL-90 `PARTIAL`（3项 PASS，7项 `NOT_RUN`） | [完整注册表报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786712413_23649.json) | PHQ-9/K6/WHO-5/GAD-7 技术闭环通过；SCL-90 外部资料阻塞使总体保持 `PARTIAL`；合成审批不等于正式支持；Android 排除 |
| `REG-SOURCE-20260814-133138` | 2026-08-14 | 当前工作树；`python3 scripts/run_scale_adaptation_registry.py --mode source --report /tmp/lx-boot-scale-source-final.json` | 6个注册版本，5个 active 技术回归项 | 未运行 PostgreSQL/Playwright | 未运行 Web | PHQ-9/K6/SCL90-v2/WHO-5/GAD-7 全部 `SOURCE_VALIDATION_PASS`；SCL90-v1 历史草稿不进入 active source run | `/tmp/lx-boot-scale-source-final.json`；不替代运行时回归或审批 | 源包结构、SHA、Golden expectation 和 profile 绑定一致；不是 PostgreSQL、评分 trace、报表或临床审批结论 |
| `REG-SOURCE-20260814-135936` | 2026-08-14 | 当前工作树；`python3 scripts/run_scale_adaptation_registry.py --mode source --report build/reports/scale-adaptation/registry-source-k10-final.json` | 7个注册版本，6个 active 技术回归项 | 未运行 PostgreSQL/Playwright | 未运行 Web | PHQ-9/K6/K10/SCL90-v2/WHO-5/GAD-7 全部 `SOURCE_VALIDATION_PASS`；SCL90-v1 历史草稿不进入 active source run；每个 active entry 的10项运行时检查均明确 `NOT_RUN` | [源包报告](../../build/reports/scale-adaptation/registry-source-k10-final.json)；不替代运行时回归或审批 | K10 源包、注册表 SHA、9 Golden expectation、reverse-score profile 绑定一致；不是 PostgreSQL、评分 trace、报表或临床审批结论 |
| `REG-PLAYWRIGHT-20260814-132924` | 2026-08-14 | 当前工作树；Java 21；PostgreSQL 18.4；临时 schema `psy_e2e_1786714120_26536`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786714120_26536.json`；`registrySha256` 由报告固化 | SCL-90 `authorized-profile-v1` 技术版本、共享 PDF 字体兼容修复和全量 active 回归 | Java 21 `bootJar` PASS；backend `./gradlew test --no-daemon` PASS；Flyway 空 schema；core/publication closure PASS；临时 schema 清理，未修改生产 PostgreSQL | `npm run build` PASS；通用业务 Playwright 8/8，1 skipped（既有非目标项） | PHQ-9 10/10 PASS；K6 10/10 PASS；SCL90-v2 10/10 PASS；WHO-5 10/10 PASS；GAD-7 10/10 PASS；注册表总体 PASS | [完整注册表报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786714120_26536.json) | 5个 active 技术版本的 scoring trace、结果解释、三语内容、Web/Word/PDF/文本、任务锁版、历史兼容、幂等/并发/重评分全部通过；SCL90-v1 历史草稿仍为 PARTIAL；治理和正式支持保持阻塞；Android 排除 |
| `REG-PLAYWRIGHT-20260814-135509` | 2026-08-14 | 当前工作树；Java 21；PostgreSQL 18.4；临时 schema `psy_e2e_1786715667_29939`；报告 `build/reports/scale-adaptation/registry-psy_e2e_1786715667_29939.json`；`registrySha256` `57b9cde4813470b28ebb8588e190681d1a277172f7044741d2514e37734f2b1c`（运行时前注册表） | K10 官方 30 天/5 点版本接入后重新执行全量 active 回归 | Java 21 `bootJar` PASS；Flyway 空 schema；core/publication closure PASS；临时 schema 清理，未修改生产 PostgreSQL | `npm run build` PASS；通用业务 Playwright 8/8，1 skipped（既有非目标项） | PHQ-9 10/10、K6 10/10、K10 10/10、SCL90-v2 10/10、WHO-5 10/10、GAD-7 10/10 required checks 全部 PASS；注册表总体 PASS | [完整注册表报告](../../build/reports/scale-adaptation/registry-psy_e2e_1786715667_29939.json) | 6个 active 技术版本的 scoring trace、结果解释、三语内容、Web/Word/PDF/文本、任务锁版、历史兼容、幂等/并发/重评分全部通过；K10 9 Golden Case 含 REVERSE；SCL90-v1 历史草稿仍为 PARTIAL；治理和正式支持保持阻塞；Android 排除 |

## 7. 回归问题记录

| Issue ID | 发现日期 | 状态 | 受影响量表/版本 | 触发变化 | 期望 | 实际 | 根因 | 数据影响 | 修复/兼容方案 | 回滚 | 证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `REG-ISSUE-20260814-001` | 2026-08-14 | `CLOSED` | K6、SCL-90 注册表回归执行器 | PostgreSQL 证据脚本第一次执行 | 每个注册项由 `psql -v scale_code` 传入量表代码并完成断言 | PL/pgSQL dollar-quoted 块不会替换 psql 变量，两个量表证据均在 SQL 解析阶段失败；Playwright 仍通过 | psql 变量替换边界被误判 | 临时 schema 在清理前未写入业务数据；失败报告保留在 `build/reports/scale-adaptation` | 改为 `set_config` 后由 `current_setting` 读取；重跑 `REG-PLAYWRIGHT-20260813-164604` 通过 | 删除本次新增 runner/SQL 文件即可回滚，不修改业务数据/迁移 | 失败报告 `registry-psy_e2e_1786639351_92502.json`；修复后报告 `registry-psy_e2e_1786639521_92897.json` |
| `REG-ISSUE-20260814-002` | 2026-08-14 | `CLOSED` | `SCALE-SCL90-001` / `SCL90_USER_DRAFT@v1` | 尝试将 SCL-90 草稿接入完整发布/任务/结果技术用例 | 没有正式总体结果区间时不得创建可发布的 BOUNDARY 结果规则 | `SCL90_ALL_FOUR` 被标为 `BOUNDARY`，但源包没有总体 result rule，发布门禁返回 `GOLDEN_CASE_BOUNDARY_INVALID` | 真实资料只提供维度/指标草稿和风险规则，没有授权的总体解释区间 | 临时 schema 清理；没有生产数据影响 | 撤回合成技术发布用例；保留草稿导入和 5 个 Golden Case；继续保持 `PARTIALLY_SUPPORTED/BLOCKED_EXTERNAL` | 删除失败用例即可回滚；不修改既有量表或迁移 | Playwright 运行日志 `GOLDEN_CASE_BOUNDARY_INVALID`；当前注册表报告恢复为 SCL-90 结果/报表/锁版 `NOT_RUN` |

| `REG-ISSUE-20260814-003` | 2026-08-14 | `CLOSED` | 注册表 Playwright 执行器 / SCL-90 草稿导入 | 全套 8 个通用 E2E 完成后运行 SCL-90 选择器 | 页面应加载并完成源包导入、Golden Case 和草稿门禁检查 | 第一次全量尝试在 `Loading admin app...` 超时，SCL-90 运行时检查未执行；独立发布用例重跑 1/1 通过，第二次完整隔离回归 8/8 通过且 SCL-90 选择器 PASS | 浏览器/前端启动时序抖动，非量表算法或数据断言；第二次隔离运行未复现 | 临时 schema 清理；没有生产数据影响 | 不修改业务代码；保留失败工件并以第二次完整运行 `REG-PLAYWRIGHT-20260813-200115` 作为当前证据；执行器对未执行检查保持 `PARTIAL/NOT_RUN` | 失败时可删除对应临时报告；不修改既有量表、迁移或用户数据 | 失败工件 `registry-psy_e2e_1786649151_94818.json`；独立发布重跑 1/1；最终报告 `registry-psy_e2e_1786651234_95049.json` |
| `REG-ISSUE-20260814-004` | 2026-08-14 | `CLOSED` | `SCALE-WHO5-001` / `WHO5_WELL_BEING@who-2024-open-access-v1` | 首次 WHO-5 Playwright Golden Case 运行 | 无效选项 Case 应验证 `OPTION_NOT_FOUND`，且合法答案 Case 应产生完整 trace | 首次源包把无效选项 Case 写成仅 4 个答案，系统按既定校验顺序先返回 `MISSING_REQUIRED_ANSWER`；修复生成器后 6/6 Golden PASS | 测试数据生成错误，不是业务代码回退 | 临时 schema 清理；生产数据无影响 | 将无效 Case 改为第1题非法选项 + 第2–5题合法选项，重新计算源包 SHA 并更新注册表；新增 SQL 只对 4 个有效 Case 检查 trace | 回滚生成器、源包和注册表即可；不修改业务迁移 | 失败报告 `registry-psy_e2e_1786665090_96512.json`；修复后 `registry-psy_e2e_1786680096_97178.json` |
| `REG-ISSUE-20260814-005` | 2026-08-14 | `CLOSED` | 注册表 PostgreSQL 证据脚本 / `SCALE-SCL90-001` | 为满足 Prompt 的“完整 scoring trace”要求增加 SCL-90 中间值断言 | 所有逐量表 SQL 断言应在隔离 PostgreSQL 中执行并输出明确 PASS/NOT_RUN | K6、WHO-5 通过；SCL-90 因新增单 Case `DISTINCT ON` 查询的 `ORDER BY` 未先包含 `case_code`，SQL 在 PostgreSQL 解析阶段失败，注册表正确标记 `FAIL` | 测试夹具 SQL 缺少 PostgreSQL `DISTINCT ON` 排序约束，不是计分或业务数据缺陷 | 仅临时 schema 受影响；失败工件保留；生产 PostgreSQL 无修改 | 将三个单 Case 查询改为 `ORDER BY case_code, revision_no DESC, id DESC`；重跑 `REG-PLAYWRIGHT-20260814-043041`，K6/WHO-5 7/7、SCL-90 scoring trace PASS | 回滚该 SQL 断言即可恢复旧结构检查；不修改 Flyway、源包或生产数据 | 失败报告 `registry-psy_e2e_1786681721_99379.json`；修复后 `registry-psy_e2e_1786681799_99586.json` |
| `REG-ISSUE-20260814-006` | 2026-08-14 | `CLOSED` | K6/WHO-5 技术 E2E 与注册表回归 | 新增三语结果对象比较、其他 locale 报告和结果标题断言 | 三语结果标题/描述/建议、locale 报告语义和 report title 均应可验证，且不误报 | 第一次断言把源包 `reviewStatus` 一并比较；第二次假设结果标题出现在 `report_content`，实际标题保存在 `report_title`；K6/WHO-5 两次选择器运行失败，PostgreSQL 未执行 | E2E 断言字段边界与报告持久化模型理解不一致，非业务或评分缺陷 | 只比较 API 返回的结果语义字段；其他 locale 验证描述/建议/非诊断文本；SQL 改为比较 `report_title` 与已审批日文结果标题；最终全量回归 `REG-PLAYWRIGHT-20260814-064135` 中 K6/WHO-5 7/7 PASS、SCL-90 PARTIAL | 回滚新增断言和 SQL 语义检查即可；不修改 Flyway、源包或生产数据 | 失败报告 `registry-psy_e2e_1786689109_2401.json`、`registry-psy_e2e_1786689197_2652.json`；修复后 `registry-psy_e2e_1786689299_2899.json`、`registry-psy_e2e_1786689654_3470.json` |
| `REG-ISSUE-20260814-007` | 2026-08-14 | `CLOSED` | 全量回归执行入口 | 当前工作树重新执行 `scripts/run-scale-package-e2e.sh` | 应创建隔离 schema 并运行 Java、Web、Playwright、注册表 PostgreSQL 证据 | 早先环境在首次 `psql` 返回 `Operation not permitted`；权限恢复后 PostgreSQL 18.4 可连接，完整入口成功 | 先前 Codex 执行沙箱限制，本地数据库服务本身无故障 | 失败尝试未创建 schema；成功运行仅使用并清理隔离 schema，不修改生产业务数据 | 保留失败工件；权限恢复后不改期望值直接重跑，`REG-PLAYWRIGHT-20260814-084548` 完整通过 | 无业务代码回滚；无需数据库恢复 | 失败工件 `psy_e2e_failure_artifact_144922.json`、`psy_e2e_failure_artifact_145500.json`；关闭证据 `registry-psy_e2e_1786697106_13841.json` |
| `REG-ISSUE-20260814-008` | 2026-08-14 | `CLOSED` | 后端回归执行环境 | 结果标题补充到 `ReportDetail`、Web、Word/PDF/TEXT 后执行 Gradle 测试 | 应运行 Java 21 后端单测并确认报告标题改动可编译、可测试 | 早先环境拒绝 Gradle file-lock/socket；权限恢复后 Java 21 的 `./gradlew test --no-daemon` 成功，完整 E2E 的 `bootJar` 也成功 | 先前 Codex 执行沙箱限制，不是 Kotlin、Gradle 配置或测试断言缺陷 | 无生产数据库影响 | 保留原失败记录；以真实 Gradle test 和 `REG-PLAYWRIGHT-20260814-084548` 关闭 | 无业务代码回滚；无需数据库恢复 | 后端 BUILD SUCCESSFUL（26 tasks）；最新全量报告 `registry-psy_e2e_1786697106_13841.json` |
| `REG-ISSUE-20260814-009` | 2026-08-14 | `CLOSED` | K6、WHO-5 通用闭环执行器 | 将两个逐量表 Playwright 闭环迁移到 `GENERIC_SINGLE_CHOICE` 数据驱动执行器 | 通用执行器应只比较持久化业务语义，并生成符合数据库长度约束的新版本号 | 第一次 K6/WHO-5 在结果翻译比较处 FAIL；收窄字段后均运行至最后的新版本创建，但把完整正式版本号拼入测试版本号，超过 `varchar(32)` 而返回 500；旧逐量表闭环和通用业务 E2E 始终通过 | 通用执行器先把源包 `reviewStatus` 混入 DTO 比较，随后又未遵守 `version_no` 长度约束；均属新测试基础设施缺陷，不是量表结果回归 | 仅隔离 schema，均已清理；生产数据无影响 | expected 只比较标题/描述/建议；测试新版本号改为短前缀；保持源包和 Golden 期望不变；`REG-PLAYWRIGHT-20260814-094823` 全量通过 | 删除共享 profile/spec 并恢复逐量表 selector；不修改源包或数据库 | 失败报告 `registry-psy_e2e_1786700682_15362.json`、`registry-psy_e2e_1786700771_15616.json`；关闭报告 `registry-psy_e2e_1786700863_15884.json` |
| `REG-ISSUE-20260814-010` | 2026-08-14 | `CLOSED` | `SCALE-GAD7-001`；全量入口核心匿名隐私证据 | 第四份量表首次复用 `GENERIC_SINGLE_CHOICE` 完整闭环 | GAD-7源包应通过后端枚举门禁并完成10项闭环；后续注册量表产生的正常通知不得被匿名Case误判 | 首轮GAD-7使用后端不支持的自定义治理状态而被预览拒绝；修正后GAD-7闭环PASS，但核心SQL又把JSON ID按文本前缀匹配而误报4条匿名通知；最终完整回归中GAD-7/K6/WHO-5各10/10，匿名隐私和core/publication closure均PASS | 源包配置缺陷 + 测试证据缺陷；不是计分、结果解释或匿名业务代码回归 | 仅隔离schema且均已清理；生产数据库无影响 | 通用静态校验器同步校验后端治理枚举；预览失败输出完整errors；匿名通知断言改为解析JSON后精确比较resultId/taskId；Golden期望不变 | 回滚GAD-7包/注册项、静态校验增强和JSON精确断言即可；无迁移和生产数据恢复 | 失败`registry-psy_e2e_1786702059_16936.json`、`registry-psy_e2e_1786702218_17290.json`；关闭`registry-psy_e2e_1786702480_17887.json`（`REG-PLAYWRIGHT-20260814-101521`） |
| `REG-ISSUE-20260814-011` | 2026-08-14 | `CLOSED` | `SCALE-PHQ9-001` / `PHQ9_FREE_USE@pfizer-public-domain-severity-v1` | PHQ-9 首次全量注册表回归的源包预览/导入 | 阻塞码必须满足 PostgreSQL `varchar(64)` 字段约束，预览/导入应返回可读门禁结果 | `JAPANESE_ELECTRONIC_USE_RIGHTS_REVIEW_PENDING` 超过 64 字符，预览导入返回 HTTP 500；PHQ-9 其余运行时检查保持 `NOT_RUN` | 量表包配置缺陷：阻塞码命名未遵守持久化字段长度；不是计分、结果或历史数据回归 | 仅隔离 schema 受影响且已清理；无生产数据写入 | 缩短为 `JAPANESE_ELECTRONIC_RIGHTS_REVIEW_PENDING`，重新生成源包、更新 SHA-256，并以 `REG-PLAYWRIGHT-20260814-130054` 完成 PHQ-9 10/10；保留原失败工件 | 回滚 PHQ-9 生成器、源包和注册表项即可；无迁移或生产数据恢复 | 失败报告 `registry-psy_e2e_1786712245_22983.json`；修复后完整报告 `registry-psy_e2e_1786712413_23649.json` |
| `REG-ISSUE-20260814-012` | 2026-08-14 | `CLOSED` | `SCALE-PHQ9-001` / 共享 `GENERIC_SINGLE_CHOICE` 闭环 | PHQ-9 题9阳性 Golden Case 的高风险结果断言 | 基础结果区间应按总分匹配；题9高风险信号应由独立 high-risk rule 断言 | 题9阳性使最终 `riskLevel=HIGH`，但总分1仍属于 `PHQ9_MINIMAL`（0–4）；共享 E2E 用最终风险等级查基础结果，运行前断言失败 | 新增高风险语义后的共享 E2E 断言缺陷；不是评分器、数据库或量表期望回归 | 仅隔离 schema 受影响且已清理；无生产数据写入 | 基础结果规则改为按分数区间选择，高风险规则单独按 `highRiskRuleCode` 比较；未改变 Golden 期望；`REG-PLAYWRIGHT-20260814-130054` 全量通过 | 回滚共享 E2E 断言改动即可；不修改源包、迁移或生产数据 | 失败报告 `registry-psy_e2e_1786712329_23331.json`；修复后完整报告 `registry-psy_e2e_1786712413_23649.json` |
| `REG-ISSUE-20260814-013` | 2026-08-14 | `CLOSED` | `SCALE-SCL90-002` / `SCL90_USER_AUTHORIZED@authorized-profile-v1` | 第一次 SCL-90 restricted profile technical closure | profile-only 包不应被未经审校的历史常模行阻断一次性技术流程；正式治理仍须阻塞 | `REG-PLAYWRIGHT-20260814-132249` 在 synthetic professional review 前返回 `SCALE_PUBLICATION_EVIDENCE_INCOMPLETE`，其余10项 `NOT_RUN` | 新版本从历史草稿继承了待审 factor norm rows；出版门禁按治理规则正确拒绝，不是评分器故障 | 仅临时 schema 受影响并清理；生产数据库无写入 | v2 技术包明确 `NOT_LOADED_PROFILE_ONLY`、清空未审校常模行，保留 `POPULATION_SPECIFIC_NORMS_PENDING` 治理阻塞；最终 `REG-PLAYWRIGHT-20260814-132924` 通过 | 恢复 v2 生成器的 norms 字段即可回到阻塞状态；不修改旧 SCL-90 v1 | 失败报告 `registry-psy_e2e_1786713727_25274.json`；修复后 `registry-psy_e2e_1786714120_26536.json` |
| `REG-ISSUE-20260814-014` | 2026-08-14 | `CLOSED` | `SCALE-SCL90-002` / 共享 technical closure helper | SCL-90 Golden expected JSON 的 `totalScore` 以字符串保存 | 所有量表的报告与重评分断言应按数值语义比较，不受 JSON 数字/字符串表示影响 | `REG-PLAYWRIGHT-20260814-132555` 报表返回数字 `360`，helper 期望字符串 `"360"`，断言失败，10项运行时检查未执行 | restricted source package 的 expected schema 使用字符串，通用 helper 缺少数值归一化 | 仅临时 schema 受影响并清理；生产数据库无写入 | helper 在匹配结果规则、report、rescore 断言前统一 `Number(...)`；源包期望不改；最终回归通过 | 回滚 helper 的数字归一化即可恢复旧断言；不修改后端计分 | 失败报告 `registry-psy_e2e_1786713914_25769.json`；修复后 `registry-psy_e2e_1786714120_26536.json` |
| `REG-ISSUE-20260814-015` | 2026-08-14 | `CLOSED` | 共享 PDF 导出 / SCL-90 日文 profile report | CJK 报表 PDF 下载 | Word/PDF/TEXT 必须由同一 report 语义成功生成，且支持三语 | `REG-PLAYWRIGHT-20260814-132735` 的 SCL-90 PDF 导出返回 HTTP 500，PDFBox 抛出 `OTF fonts do not have a glyf table`；Web、Word、TEXT 先前已通过 | macOS CJK TTC 字体被 PDFBox 以 TrueType 子集化，OpenType 字体缺少 `glyf` 表 | 仅隔离 schema 受影响并清理；生产数据库无写入 | `ExportService` 对字体集合面改为完整嵌入而不做子集化；最终 `REG-PLAYWRIGHT-20260814-132924` 的5个 active 版本 PDF/Word/TEXT 全部通过 | 回滚 ExportService 字体参数可复现失败；不改 ScalePackage 或计分结果 | 失败报告 `registry-psy_e2e_1786714013_26156.json`；后端日志记录 PDFBox stack trace；修复后完整报告 `registry-psy_e2e_1786714120_26536.json` |
| `REG-ISSUE-20260814-016` | 2026-08-14 | `CLOSED` | 静态 Python 校验命令 | 运行 `python3 -m py_compile` 作为收尾检查 | 应在不写入用户缓存目录的情况下完成脚本语法校验 | 系统 Python 默认把 pyc 写入受限的 macOS 缓存路径，第一次返回 `PermissionError`；代码本身未报告语法错误 | 执行环境缓存路径权限，不是量表或应用回归 | 无业务/数据库影响 | 设置 `PYTHONPYCACHEPREFIX=/tmp/lx-boot-pycache` 后重新运行，Python 编译、JSON、Shell 与 `git diff --check` 全部 PASS | 只需重跑带临时缓存前缀的静态命令；不改源包或业务代码 | 重跑命令成功；全量技术报告 `REG-PLAYWRIGHT-20260814-132924` 不受影响 |
| `REG-ISSUE-20260814-017` | 2026-08-14 | `CLOSED` | `SCALE-K10-001` / `K10_OFFICIAL_FREE_USE@official-self-admin-30day-5point-v1` | K10 首次全量回归的版本化导入确认 | `version_no` 必须满足后端/数据库 `varchar(32)`，源包应完成 preview/import | 第一次 K10 版本号 `official-self-admin-30day-5point-v1` 超过 `varchar(32)`，隔离导入确认失败；K10 后续 required checks `NOT_RUN` | K10 版本标识虽语义清晰但未按持久化字段长度约束设计；不是计分、结果或历史数据缺陷 | 仅临时 schema 受影响并清理；生产 PostgreSQL 无写入 | 缩短并锁定为 `official-30day-5point-v1`，重新生成源包 SHA-256 和注册表元数据；最终 `REG-PLAYWRIGHT-20260814-135509` K10 10/10 PASS | 回滚 K10 生成器、源包和注册表项即可；无迁移或生产数据恢复 | 失败报告 `registry-psy_e2e_1786715358_28696.json`；修复后完整报告 `registry-psy_e2e_1786715667_29939.json` |
| `REG-ISSUE-20260814-018` | 2026-08-14 | `CLOSED` | `SCALE-K10-001` / 共享 generic publication gate | K10 使用 `reverseScore` 后首次执行完整发布闭环 | 反向计分量表必须有显式 `REVERSE` Golden Case，才能证明展示顺序与有效分重编码 | 第二次 K10 全量尝试在发布评审前返回 `GOLDEN_CASE_TYPE_MISSING:REVERSE`，专业/业务审批和其余 required checks 未执行 | 共享发布门禁要求能力类型与 Golden Case 类型一一对应；K10 初始 8 案例只覆盖边界/正常/缺失/非法，未覆盖 reverse | 仅临时 schema 受影响并清理；生产 PostgreSQL 无写入 | 新增 `K10_REVERSE_RECODE`（期望总分14）并更新源包/注册表 Golden SHA；最终 `REG-PLAYWRIGHT-20260814-135509` 全部 active 版本 PASS | 删除该 Golden Case 并恢复旧 SHA 可复现阻塞；不修改计分器或生产数据 | 失败报告 `registry-psy_e2e_1786715459_29140.json`；修复后完整报告 `registry-psy_e2e_1786715667_29939.json` |
| `REG-ISSUE-20260814-019` | 2026-08-14 | `CLOSED` | `SCALE-K10-001` / K10 源包与隔离 fixture snapshot | 新增 REVERSE Golden 后立即重复全量尝试 | 新的注册表和源包指纹必须同时进入新隔离 schema，预览 readiness 应显示 `K10_REVERSE_RECODE` | 中间尝试 `REG-PLAYWRIGHT-20260814-135333` 仍固化旧 registry/source 指纹，readiness 继续报告 `GOLDEN_CASE_TYPE_MISSING:REVERSE`；不是计分器回归 | 本次尝试复用了新增 Golden 写入前的临时输入快照，属于回归编排时序问题 | 仅临时 schema 受影响并清理；生产 PostgreSQL 无写入 | 重新生成 K10 包、刷新注册表期望并从干净隔离 schema 重跑；`REG-PLAYWRIGHT-20260814-135509` K10 及全部 active 版本 PASS | 保留旧失败工件即可复现；无需业务/数据库回滚 | 失败报告 `registry-psy_e2e_1786715573_29542.json`；最终报告 `registry-psy_e2e_1786715667_29939.json` |
| `REG-ISSUE-20260814-020` | 2026-08-14 | `OPEN` | `SCALE-PSS10-001` / PSS-10 intake | 为下一份量表寻找真实版本和电子使用边界 | 只有原始版本、授权/使用范围、反向题、三语内容和非诊断解释可核验后才能适配 | Mapi/ePROVIDE 将 PSS-10 作为受控问卷分发；CMU 要求许可请求，Mapi 对电子实现和复制/改写/翻译设有条件；当前未得到本项目可核验许可或正式三语电子版本，因此没有伪造源包 | 外部资料/授权和版本输入缺失，不是计分器或应用故障 | 未创建 ScalePackage、未改注册表、未运行 PostgreSQL；无业务数据影响 | 保持 `INPUT_PENDING`，仅记录官方入口；收到许可和正式版本后再按 PSS-10 10题/反向题规则建立新版本并全量回归 | 可直接删除候选台账行和本 issue，不影响既有 7 个注册版本 | [Mapi/ePROVIDE PSS-10 conditions](https://eprovide.mapi-trust.org/instruments/perceived-stress-scale-10-items)；[CMU permission page](https://www.cmu.edu/dietrich/psychology/stress-immunity-disease-lab/scales/index.html) |

状态只能使用：`OPEN`、`INVESTIGATING`、`FIXED_PENDING_REGRESSION`、`CLOSED`、`ACCEPTED_NEW_VERSION`。

## 8. 当前下一步

当前收尾执行边界：K10 已完成一个明确版本 `K10_OFFICIAL_FREE_USE@official-30day-5point-v1` 的技术适配和全量隔离回归，最新证据为 `REG-PLAYWRIGHT-20260814-135509`；6 个 active 技术版本（PHQ-9、K6、K10、SCL90-v2、WHO-5、GAD-7）全部 required checks PASS。新增或修改 ScalePackage、Golden 期望、计分/结果/报表语义或数据库闭环后，必须重新执行全量注册表回归。K10 和 SCL90-v2 当前均为 `TECHNICALLY_VERIFIED/BLOCKED_EXTERNAL`，不代表正式支持；SCL90-v1 仍作为历史草稿 `PARTIALLY_SUPPORTED` 保留，不得混算。

1. 继续以“已知量表快速支持”为优先目标：下一份资料完整、通用单选计分的量表复用 `GENERIC_SINGLE_CHOICE`，只新增ScalePackage、closure Golden Case和注册表项；不新增业务流程、Playwright或PostgreSQL分支。
2. 将远程CI的`scale-adaptation-regression-reports`工件纳入发布门禁复核；本地Java 21、PostgreSQL 18.4、Gradle和Playwright已由`REG-PLAYWRIGHT-20260814-135509`验证，仍需远程工作流实际证据。
3. 对 PHQ-9、K6、K10、WHO-5、GAD-7 和 SCL90-v2 保持统一回归；SCL90-v1 仅保留历史草稿证据；多选、矩阵、跳题、常模和其他专用算法在证明可复用前不得套用单选profile。
4. 完成 PHQ-9、WHO-5 与 GAD-7 的真实三语审校、适用人群、license scope、结果解释和正式双审批；PHQ-9 另需题9危机响应责任人/SLA及日文电子使用权确认；隔离技术链路不能升级为生产批准。
5. 新量表达到 `TECHNICALLY_VERIFIED` 后，必须先确保所有既有 active 量表回归 PASS，才能进入真实审批；K10 还必须补齐引用/使用范围归档、三语审校、切点/人群适用范围、专业双审批和业务验收；SCL90-v2 还必须补齐授权范围、翻译审校、专业双审批、常模/危机治理和业务验收，之后才可考虑正式发布。
