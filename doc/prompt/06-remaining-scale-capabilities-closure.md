# 剩余量表适配能力补齐：验收记录

测量日期：2026-08-13
工程：`lx-boot`  分支：`feature/redo`  基线提交：`e6d0b3c`

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

- `backend ./gradlew test`：409 tests，0 failures，0 errors，15 skipped
  （跳过项为真实 PostgreSQL 门控的 `FlywayMigrationPostgresTest`）。
- 未新增 Flyway 迁移，复用 V12 已有的 `input_schema_json jsonb` 列，迁移计数
  断言无需变更。

## 3. 边界（未做）

- 任务 2 已覆盖「维度总分 → 分段重编码」（`RECODE_SUM_TO_0_3`）、「睡眠时长 →
  分段重编码」（`SLEEP_DURATION_RECODE_0_3`）和「睡眠效率 → 分段重编码」
  （`SLEEP_EFFICIENCY_RECODE_0_3`）。以上均为受限白名单规则，PSQI 各分量的具体
  题号与分段阈值仍须依据授权手册配置，代码不内置任何量表具体映射。
- 任务 3 未实现量表内「条件跳题/分支」能力，只做了算法白名单阻断 + 高风险规则
  人工复核收口的最小闭环；非自评题型同样在源包导入阶段被题目类型白名单阻断。
- 他评量表（HAMD/HAMA 等）需要全新的「访谈员评分」答题模式，仍未实现。

## 4. 是否阻断发布

否。改动均为新增校验或新增能力，不改变未声明 `recode` 的既有量表行为；白名单只对
声明了 `recode` 或专用算法的源包生效。
