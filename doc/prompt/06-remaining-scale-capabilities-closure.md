# 剩余量表适配能力补齐：验收记录

测量日期：2026-08-13
工程：`lx-boot`  分支：`feature/redo`  本轮复核基线提交：`44289a0`

本记录覆盖上一轮「源包导入通用化」之后仍未覆盖的量表适配能力。目标是把
「危机分级访谈」和「含分量表重编码」的量表以最小闭环补齐，同时把不支持的
量表明确阻断，不硬编码、不伪造、可验证。

## 1. 完成情况

### 任务 1：派生指标白名单校验（禁止静默降级）

- `ScaleSourcePackageValidation` 增加校验：`scoring.indices` 非空且算法不是
  `SCL90_PROFILE` 时阻断（`SOURCE_PACKAGE_INDICES_UNSUPPORTED`）。
- 补齐 `messageKey` 与三语文案 `scale.source_package.indices_unsupported`。
- 单测：`rejects derived indices for non scl90 algorithm`、
  `scl90 derived indices are not blocked by index whitelist`。

### 任务 2：分量表重编码（声明式、白名单）

- 源包 DTO 新增 `SourceDimensionRecode` / `SourceRecodeBand`，`SourceDimension`
  增加 `recode` 字段。
- 只接受白名单规则 `RECODE_SUM_TO_0_3`，校验分段（bands）为空、越界、重叠。
- 导入时把重编码序列化进已有的 `input_schema_json`（`dimensionRecodes`），
  未新增 Flyway 迁移。
- `ScoreCalculator` 读取 `input_schema_json`，在维度聚合后应用分段映射
  （`applyRecode`）；新增 `loadAlgorithmBinding`、`parseDimensionRecodes`、
  `loadDimensionCodes`。
- 单测：`rejects unsupported dimension recode rule`、
  `rejects overlapping dimension recode bands`、
  `accepts supported dimension recode rule`、
  `dimension recode maps aggregate sum into declared bands`。

### 补充：题目类型白名单 + TIME 题型

- `ScaleSourcePackageValidation` 增加题目类型白名单校验，支持 `SINGLE_CHOICE /
  MULTI_SELECT / SLIDER / MATRIX / TEXT_WITH_OPTION / TEXT / TIME`，之外的题型
  （例如访谈式题型）在导入预览时即被 `SOURCE_PACKAGE_QUESTION_TYPE_UNSUPPORTED` 阻断。
- 新增 `TIME` 题型用于「时刻」答案；`QuestionScoreContext` / `loadQuestionScoringMeta`
  透传 `answerText`/`answerValue`，`ScoreCalculator` 新增
  `SLEEP_DURATION_RECODE_0_3`（跨午夜睡眠时长）与
  `SLEEP_EFFICIENCY_RECODE_0_3`（睡眠效率百分比）白名单规则，计算并分段映射。
- 前端 `TaskQuestionPage` 新增 `TIME` 题型的 `TimePicker` 渲染、答案序列化/回显与
  进度统计（`answerProgress.ts`），时间答案以 `answerText` 存为 `HH:mm`。
- 单测：`rejects unsupported question type`、
  `rejects sleep duration recode without question references`、
  `sleep duration recode computes cross-midnight duration and maps it`、
  `sleep efficiency recode computes efficiency percentage and maps it`。

### 补充：量表内条件跳题

- 源包 DTO 新增 `SourceSkipRule`（`whenQuestionNo` + `whenOptionCode` +
  `skipQuestionNos`），仅声明式白名单，不执行任意分支/脚本。
- 校验、导入存储（V24 `psy_scale.skip_rules_json`）、后端 `TaskQuestionPayload`
  返回、前端答题跳过逻辑（`visibleQuestions`）打通。
- 后端保存、手工提交、超时提交和重新评分均重新解析分支，只持久化当前有效题目；
  被跳过的必答题不再导致拒绝，缺失率、按比例折算和维度题数也只按有效分支计算。
- 跳题规则纳入内容摘要并随量表版本复制，任务锁定的历史版本不会因后续规则变化而改变。
- 校验补充触发选项存在性、重复规则和重复目标检查；前端仅负责交互，后端是最终规则边界。
- 单测覆盖非法规则、分支必答、隐藏旧答案过滤和内容摘要变化。

### 补充：总分与维度聚合解耦

- 源包 `scoring.dimensionAggregation` 使用受限枚举声明维度聚合，不再假定维度与总分使用同一种算法。
- `ScoreCalculator` 支持总分 `SIMPLE_SUM`、维度 `AVERAGE` 等组合，并在计分 trace 中记录真实维度聚合。
- SCL-90 保持 90 题总分求和，同时 10 个因子按题数计算均分；这条回归由 Playwright
  的 `SCL90_ALL_FOUR` Golden Case 实际发现并修复。
- 未声明该字段的既有量表继续按原 `scoreMethod` 处理，保持兼容；不支持的维度聚合在导入预览阶段阻断。

### 补充：评估模式声明（他评早期阻断）

- `SourceScale` 新增 `assessmentMode`（`SELF`/`RATER`）；源包校验对 `RATER`
  模式返回 `SOURCE_PACKAGE_ASSESSMENT_MODE_UNSUPPORTED`，在导入预览阶段即阻断，
  避免把访谈员评分量表误当作自评量表导入。
- 单测：`rejects rater assessment mode`。

### 补充：真实 K6 源包与三语安全文案

- 新增官方免费使用的 K6 源包、确定性生成器和校验器；源包引用官方英文、中文和日文
  表单及官方计分说明，包含 6 题、0～24 分换算、两个结果区间和 6 个 Golden Case。
- 源包的量表级翻译新增用途、可见范围、非诊断说明和帮助资源等本地化字段；导入时不再
  把英文治理说明复制到中日英三种翻译。
- 发布前强制每种语言具有非空的量表专属非诊断说明；PostgreSQL 集成测试核对 K6
  导入后的三种说明与源包逐字一致。
- `validate_k6_source_package.py` 同时校验三个非诊断说明存在且互不相同，防止再次出现
  “字段齐全但语言错误”的假三语。

### 补充：量表专属报告快照与 CJK PDF

- 提交和重新评分生成报告时，读取该量表版本、该作答语言、且已审批的
  `non_diagnostic_text`，写入不可变报告内容；系统通用安全说明继续保留作为兜底。
- 文本、PDF、Word 三种渲染器回归测试核对量表专属结果解释和建议没有在格式转换时丢失。
- PDF 字体加载支持 TTC/TTF 和显式 `PSY_PDF_FONT_PATH`；含中文/日文内容时不再静默
  回退到 Helvetica，找不到可编码字体会返回稳定错误 `EXPORT_PDF_FONT_MISSING`。
- 新增 K6 专属 Playwright 场景，实际验证日语结果解释、量表专属非诊断说明、Web 展示
  以及文本/PDF/Word 下载。

### 任务 3：分级/分支访谈最小闭环

- C-SSRS 等访谈量表不作为自评量表进入源包导入，访谈计分保持 UNSUPPORTED。
- 收口路径：自评量表通过 `high_risk_rule` 触发高风险预警，转入现有「人工复核 +
  升级」处置流程。
- 单测：`readiness blocks a clinical interview algorithm` 锁定 `CSSRS_INTERVIEW`
  算法在发布就绪检查被 `ALGORITHM_RUNTIME_UNSUPPORTED` 阻断。

### 任务 4：人格类保持 UNSUPPORTED 并固化证据

- 16PF、EPQ、大五、MBTI、YG、A 型行为继续冻结。
- 单测：`readiness blocks a dedicated personality algorithm` 锁定 `16PF_PROFILE`
  算法在发布就绪检查被 `ALGORITHM_RUNTIME_UNSUPPORTED` 阻断。

## 2. 验证结果

- Java 21：使用 `/opt/homebrew/opt/openjdk@21` 实际编译和运行。
- 后端全量（含隔离 PostgreSQL）：434 tests，0 failures，0 errors，0 skipped。
- Flyway：空库执行 V1～V26；已有 V1 基线执行 V2～V26；测试 schema 用后即删除，未修改业务 schema。V25 只新增并回填可确定的审批人快照，不伪造历史审批证据；V26 以 `NOT VALID` CHECK 保留历史行，同时阻止新写入的无审批人、无证据、无审查范围或无专业资质引用的 `APPROVED` 记录。
- K6：真实源包导入为租户 DRAFT 后，6 个 Golden Case 均由真实 `ScoreCalculator` 执行并通过。
- Web：Vitest 13 files / 104 tests 全部通过；TypeScript + Vite 生产构建通过。
- Playwright：10/10 通过；除并发暂存/提交、匿名隐私、三语报告、SCL-90 导入、
  双审批发布、任务版本锁定和预警干预关闭外，还实际完成 K6 的“源包导入 → 三语校验
  → 6 个 Golden Case → 双角色审批 → 发布 → 任务锁版 → 13 分边界评分 → 日语结果解释
  → Web → 文本/PDF/Word”技术闭环。
- K6 Playwright 中的审批只验证角色分离、门禁和数据库状态流转，测试 schema 运行后删除；
  不等同于真实心理专业审核或生产业务验收。
- 正式 `APPROVED` 审批现在必须保存审批人快照、受控证据引用和明确审查范围；专业审批还必须
  保存资质记录引用。同一 `reviewToken` 只能重放完全相同的审批负载，改变评论、资质、证据
  或范围会返回 `SCALE_PUBLICATION_REVIEW_TOKEN_CONFLICT`。回退到不识别这些字段的旧应用时，
  V26 会继续以数据库约束拒绝不完整审批，因此回退期间必须暂停量表发布，不能绕过该约束。

## 3. 边界（未做）

- 任务 2 已覆盖「维度总分 → 分段重编码」（`RECODE_SUM_TO_0_3`）、「睡眠时长 →
  分段重编码」（`SLEEP_DURATION_RECODE_0_3`）和「睡眠效率 → 分段重编码」
  （`SLEEP_EFFICIENCY_RECODE_0_3`）。以上均为受限白名单规则，PSQI 各分量的具体
  题号与分段阈值仍须依据授权手册配置，代码不内置任何量表具体映射。
- 任务 3 已实现量表内「条件跳题」能力（源包声明 → 校验 → 存储 → API → 前后端一致执行），
  但仍为声明式白名单；任意分支/脚本、访谈计分仍未实现。
- 他评量表（HAMD/HAMA 等）已在源包层声明并早期阻断，但完整的「访谈员评分」
  答题模式（角色/任务分配/评分 UI）仍未实现，需要单独立项。

## 4. 是否阻断发布

代码兼容性不阻断：未声明 `recode`、`skipRules` 或 `dimensionAggregation` 的既有量表保持原行为。

真实量表发布仍受外部治理约束：K6 的中日英内容、13+ 切点适用人群和临床解释必须完成
独立专业审核与业务审批；SCL-90 还受版权授权、常模来源和危机处置责任/SLA 阻断。上述事项
没有证据前只能保持 DRAFT，不能标记为生产闭环。

因此当前结论是：K6 已达到“代码、数据库和自动化运行证据上的完整技术闭环”，但尚未达到
“真实专业签字和生产业务验收后的正式发布闭环”。不得把隔离 E2E 中的测试审批记录复制到
业务数据库，也不得据此对外宣称临床有效性已经完成审核。
