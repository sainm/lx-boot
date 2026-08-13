# M1+M2：真实量表闭环与通用计分适配范围冻结

测量日期：2026-08-11  
工程：`lx-boot`  分支：`feature/redo`  基线提交：`e6d0b3c`  
Android：本批次明确不处理

本文是 M1+M2 批次的范围冻结和验收记录。它不把技术演示数据写成临床量表，也不把缺少授权、正式常模或专业审核的内容标记为正式支持。

## 1. 当前真实边界

当前仓库已经有 ScalePackage、三语翻译、导入预览/确认、Golden Case revision、双审批、发布指纹、任务版本锁定和 PostgreSQL Flyway V1–V23。`SCL90_TECH_DEMO` 以及 `E2E_CORE_TECH_FIXTURE` 是仓库内的技术 fixture；它们没有构成正式量表授权、临床常模或专业审核证据。

本批次冻结的正式量表清单为空。原因是仓库中没有可核验的“已获授权的简单量表”资料包（手册/来源、版权授权、三语正式内容、常模授权和专业审核记录）。在这些外部输入到位前，不新增或伪造 PHQ-9、GAD-7、SAS、SDS、SCL-90 等正式内容。

仓库内技术验证清单如下；它们只用于验证引擎和治理流程，不能进入正式支持清单：

| 代码及版本 | 来源/授权 | 题型与维度 | 计分/质量 | 三语 | 六类 Golden Case | 状态 |
|---|---|---|---|---|---|---|
| `E2E_CORE_TECH_FIXTURE` / `v1` | 仓库合成 fixture；仅限隔离自动化 | 单选、无临床维度 | `SIMPLE_SUM`、反向题、高风险选项、`REJECT` | 已有合成三语 | PostgreSQL/Playwright 已运行 | `TECHNICALLY_SUPPORTED_PENDING_REVIEW`（`TECH_DEMO`，不属于正式支持） |
| `SCL90_TECH_DEMO` / `v1` | 用户提供的技术结构示例；授权、常模和专业审核未核验 | 单选、10 个技术维度 | 结构上可用通用求和/维度平均；正式 SCL-90 效度、常模和解释未实现 | 仅技术种子，非正式翻译 | 未形成正式六类证据 | `PARTIALLY_SUPPORTED`（`TECH_DEMO`，保持 `DRAFT`） |

## 2. M2 计分能力冻结矩阵

只实现目标 fixture 和未来简单量表实际需要的通用能力；专用算法和任意脚本不在本批次范围。

| 能力 | 当前状态 | 证据/限制 |
|---|---|---|
| `SIMPLE_SUM`、`REVERSE_SUM` | `SUPPORTED` | `ScoreCalculator` 及单元测试；反向范围由选项最小/最大分值计算 |
| `WEIGHTED_SUM`、`AVERAGE`、`WEIGHTED_AVERAGE` | `SUPPORTED` | 题目权重、系数和维度聚合在通用引擎中处理 |
| 多维度、一题多维度映射 | `SUPPORTED` | 当前模型允许题目归属一个维度；同题多维度仍需正式目标量表输入后再扩展 |
| `REJECT`、`ALLOW`、`PRORATE`、缺失率和必答 | `SUPPORTED` | V22 质量策略、答卷质量结果和 PRORATE 评分路径；`PENDING_PROFESSIONAL_REVIEW` 发布阻断 |
| 作答时长下限/上限 | `SUPPORTED` | V22 质量策略；人工提交和超时自动提交均执行 |
| 原始分、反向分、加权分、维度分、常模选择的评分追溯 | `SUPPORTED_WITH_AUDIT_TRACE` | V23 `scoring_trace_json` 保存算法/版本、逐题中间分、维度聚合、常模选择和规则匹配；不向被测者报告暴露原始敏感数据 |
| 量表结果解释、Web 展示和个人报表布局 | `SUPPORTED_WITH_GOVERNANCE_GATE` | `report_template` 只接受 `DEFAULT_SCREENING`、`SINGLE_SCORE`、`DIMENSION_PROFILE`、`NORMATIVE_PROFILE`、`RISK_TRIAGE`；报告内容保留量表版本生成的三语结果描述/建议，Web、Word/PDF/文本按模板决定维度、常模、高风险区块；未知模板在发布时阻断 |
| Z 分、T 分和按条件选择常模 | `SUPPORTED_WITH_GOVERNANCE_GATE` | 仅使用已审核、来源/版本/样本量完整且条件匹配的常模 |
| 百分位 | `UNSUPPORTED` | 当前数据库和引擎没有目标量表所需的百分位规则/数据模型，发布必须阻止 |
| 分段换算、条件公式、自定义公式 | `UNSUPPORTED` | 没有目标量表实际需求；禁止静默降级或加入任意脚本 |
| 一致性、矛盾答案、反应模式效度 | `UNSUPPORTED` | 现有 `validity_rule` 只作为发布门禁，运行时未注册实现 |
| 专用算法（SCL/EPQ/16PF/MMPI 等） | `UNSUPPORTED` | 没有正式手册、授权常模和版本化白名单扩展点，不得宣称支持 |
| 单选、多选、矩阵、数字/滑块、文本、跳题 | `PARTIALLY_SUPPORTED` | 当前答题校验支持单选、多选、矩阵、滑块、文本和带选项文本；跳题规则尚无目标量表需求，保持发布门禁 |

## 3. 外部输入阻塞项

以下事项不能由代码代理猜测完成，完成前不得把任何量表标记为 `FULLY_SUPPORTED`：

1. 一份明确代码和版本的已授权简单量表资料包。
2. 中文、日语、英语正式题目、选项、说明、结果解释和建议，以及翻译审核人。
3. 正式计分手册、反向题/维度/缺失/效度规则和算法版本。
4. 常模来源、授权范围、适用条件、样本量、版本和有效期。
5. 专业审核人和业务审核人的独立账号及审批结论。
6. 高风险规则的人工复核对象、响应时限、升级路径和处置责任人。

收到资料后，只能通过受控 ScalePackage 导入创建新版本；已经发布或被任务引用的版本不可原地修改。

## 4. 本批次验收与停止条件

- 技术 fixture 的六类 Golden Case、双审批、发布指纹和任务锁定继续由现有 PostgreSQL/Playwright Case 验证。
- 新增评分 trace 必须与结果的量表内容摘要、算法版本、质量判定、常模选择和答案明细可稳定重建；历史结果保持追加式。当前技术 E2E 的数据库后置断言已验证 trace JSONB 结构和算法版本。
- 量表报告模板必须和量表版本一起冻结；重新生成报告不得丢失原量表的结果解释和建议，未知/历史模板只能走兼容默认布局，不能执行任意模板代码。
- 发布门禁必须阻止未审核常模、未知计分方法、无效质量策略、未实现效度规则和缺失三语内容。
- PostgreSQL 空库迁移、V22 基线升级、后端完整测试、Web 测试/production build 和 `git diff --check` 必须在最终回归中实际执行。
- 在授权资料和专业/业务审批到位前，M1 的正式量表闭环状态只能是 `BLOCKED_EXTERNAL`/`TECHNICALLY_SUPPORTED_PENDING_REVIEW`；不得继续追加量表或算法。

## 5. 回滚

本批新增数据库变更只允许使用新的 Flyway 版本。应用回滚到旧版本时，新增评分 trace 列保持可空，旧应用继续读写原有评分列；数据库回滚不通过删除历史结果实现。如需撤销本批代码，先停止使用新 trace 写入，再按发布平台回滚应用制品，并保留迁移和已有审计证据。
