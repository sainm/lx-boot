# 逐量表适配与全量兼容回归 Prompt

## 目标

基于当前 `lx-boot` 的真实代码、PostgreSQL 结构和运行测试，按照“一次只完成一份明确版本的真实量表”的方式持续扩充量表能力，最终形成：

- 一套统一业务代码和 API；
- 一套通用计分、结果解释、Web 展示和报表框架；
- 每份量表独立、版本化、不可变的 `ScalePackage`；
- 少数复杂量表使用受控、版本化的专用算法扩展；
- 每增加或修改一份量表，都自动重跑全部已完成量表，证明既有评分、结果、展示和报表没有被破坏。

Android 当前明确排除。任务状态和回归结果必须更新到
[逐量表适配任务与回归台账](../process/09-scale-adaptation-task-tracker.md)，不得只在对话或提交说明中记录。

实施目的以“已知量表快速支持”为主：资料完整且落入已有白名单能力的量表，应通过版本化 ScalePackage、Golden Case 和注册表配置复用统一闭环；不能要求每份量表复制业务代码和整套 E2E。快速只减少重复工程，不降低授权、三语、专业审批、结果解释或回归门禁。

## 可直接执行的 Prompt

```text
请在当前 lx-boot 工程中，按照“逐量表适配、全量历史回归”的方式持续完成心理量表适配。

一、最终目标

系统最终保持一套统一代码，不按量表复制 Controller、任务、答卷、报告或导出业务流程：

统一业务流程
  -> 读取任务锁定的 ScalePackage 版本和内容摘要
  -> 通用计分器或受限专用算法
  -> 量表版本自己的三语结果解释和建议
  -> 统一报告模型
  -> 受控报告布局和可视化
  -> Web / Word / PDF / 文本

“已适配”只表示指定代码和版本的量表经过资料、代码、数据库、Golden Case、三语、报表、专业审核和业务验收，不表示未知量表可以自动安全运行。

二、强制实施原则

1. 每次只选择一份明确代码和版本的目标量表，不并行编造多个正式量表包。
2. 先读取 doc/process/09-scale-adaptation-task-tracker.md，确认当前任务、已有量表和未解决回归问题。
3. 先检查真实手册、来源、版权/授权、三语资料、适用人群、计分规则和审核人；缺少时标记 INPUT_PENDING 或 BLOCKED_EXTERNAL。
4. 不得根据常识、量表名称、博客或相似量表猜测题目、阈值、常模、解释或授权。
5. 优先使用现有通用引擎；只有正式手册无法被现有白名单表达时才扩展引擎。
6. 不允许按 scaleCode 写散落的 if/else；专用算法必须有稳定代码、版本、受限输入输出和注册表。
7. 不允许任意脚本访问数据库、网络或文件完成运行时计分。
8. 已发布或被任务引用的量表版本不可原地修改，只能生成新版本。
9. 不修改已经执行过的 Flyway；数据库变化只能增加新的不可变迁移。
10. 不删除、重建或清空用户已有 PostgreSQL 数据。
11. Android 不在本目标范围内。
12. 测试期模拟审批只能证明工作流，不得写成真实专业审批或业务验收。
13. 不得为了让回归变绿而直接更新旧量表期望值。任何期望变化必须先形成回归问题、影响分析和审批结论。

三、目标量表输入门禁

开始编码前，必须收集并记录：

- 量表代码、正式名称、版本、发布日期、适用人群和排除条件；
- 原始来源、版权状态、授权类型、授权语言、地域、期限和使用范围；
- 中文、日语、英语正式说明、题目、选项、结果解释、建议和帮助资源；
- 正式计分规则：题型、分值、反向题、权重、维度、缺失、跳题、效度和风险规则；
- 总分、维度分、标准分、Z/T/百分位或其他派生指标的定义；
- 常模来源、样本量、适用条件、版本、有效期和授权；
- 结果区间和边界包含关系；
- 高风险信号的人工复核人、SLA、升级路径和处置责任；
- 心理专业审核人和业务验收人。

任何关键输入不完整时，不得进入 FORMALLY_APPROVED 或 FULLY_SUPPORTED。

四、单份量表实施任务

对每份量表按以下任务顺序执行，并在台账逐项更新：

1. INPUT：资料、授权和三语输入审查。
2. PACKAGE：建立确定性、版本化 ScalePackage 及校验器。
3. ENGINE：确认通用引擎可表达；必要时增加最小受限扩展。
4. RESULT：实现总体/维度结果区间、风险、三语解释和建议。
5. REPORT：选择 SINGLE_SCORE、DIMENSION_PROFILE、NORMATIVE_PROFILE、RISK_TRIAGE 或 DEFAULT_SCREENING，必要时增加受控模板代码。
6. GOLDEN：建立正常、边界、反向、缺失、无效、高风险、常模和专属算法 Case。
7. DATABASE：在隔离 PostgreSQL 完成导入、确认、双审批、发布和任务锁版。
8. RUNTIME：完成暂存、恢复、幂等提交、评分、结果、报告和重新评分验证。
9. I18N：验证 zh-CN、ja-JP、en 的题目、错误、结果、Web 和报表。
10. EXPORT：验证 Word、PDF、文本与 Web 使用同一版本语义。
11. SECURITY：验证同租户、跨租户、匿名、审计和高风险处置边界。
12. APPROVAL：由真实专业人员和业务负责人分别提交证据绑定的审批。
13. ACCEPTANCE：正式发布并完成生产或受控验收。
14. REGRESSION：运行全部历史量表兼容回归，而非只运行新增量表。

五、统一全量回归注册表

在增加第二份正式量表前，建立一个机器可读的已完成量表注册表和统一执行器。每个条目至少包含：

- scaleCode、versionNo、源包路径和源包 SHA-256；
- algorithmCode、algorithmVersion、reportTemplate；
- Golden Case 清单及不可变期望；
- 要验证的三语、结果区间、派生指标、风险规则和报表格式；
- 正式支持状态及外部审批状态。

统一执行器必须在全新隔离 PostgreSQL 中：

1. 执行全部 Flyway 迁移。
2. 依次导入注册表中所有量表包。
3. 运行每份量表的全部 Golden Case。
4. 比较完整 scoring trace，而不只比较最终总分。
5. 验证三语结果标题、描述、建议和非诊断说明。
6. 验证 Web 报告语义以及 Word/PDF/文本导出语义。
7. 验证历史任务继续锁定旧量表版本和内容摘要。
8. 验证创建新版本不会改变历史答卷、结果和报告。
9. 验证同一提交令牌、并发提交和重新评分的历史兼容性。
10. 输出逐量表 PASS/FAIL，不允许只输出整体进程退出码。

六、回归比较范围

每份已完成量表至少比较：

- 题目有效集合和跳题路径；
- 每题原始分、反向分、权重分和有效分；
- 缺失率、质量状态、按比例折算因子；
- 总分、维度分、标准分、Z/T 分和受支持的派生指标；
- 常模代码、选择原因和结果规则；
- 风险等级、高风险规则和人工处置入口；
- 三语结果标题、结果描述、建议和非诊断说明；
- reportTemplate、指标、维度、参考范围和可视化语义；
- Word/PDF/文本的关键语义内容；
- scale content hash、release fingerprint、算法版本和 scoring trace；
- 任务锁定版本、历史结果版本和报告版本。

七、回归失败处理

发现旧量表变化时，必须先在台账“回归问题记录”新增条目，状态设为 OPEN 或 INVESTIGATING，并记录：

- 触发提交和命令；
- 受影响量表、版本、Golden Case 和输出字段；
- 期望值与实际值；
- 是否影响历史已发布结果；
- 根因分类：引擎缺陷、包配置缺陷、测试缺陷、预期业务变更或环境问题；
- 修复方案、兼容方案、数据影响和回滚方式；
- 专业/业务是否批准改变结果语义。

未得到明确结论前：

- 不得覆盖旧 Golden Case；
- 不得修改旧源包掩盖回归；
- 不得发布新量表；
- 不得把失败标记为“无影响”。

如果变化是批准后的业务语义变更，必须创建新量表版本，并保留旧版本回归基线。

八、状态定义

- NOT_STARTED：未开始。
- INPUT_PENDING：缺少真实资料、授权或三语输入。
- IN_PROGRESS：正在实现，尚未通过完整验证。
- PARTIALLY_SUPPORTED：部分技术能力存在，但不能完成该量表正式闭环。
- TECHNICALLY_VERIFIED：代码、数据库和自动化闭环通过，但外部审批未完成。
- BLOCKED_EXTERNAL：等待版权、专业、业务、常模或安全治理输入。
- FORMALLY_APPROVED：真实专业与业务审批完成，但尚未完成最终发布验收。
- FULLY_SUPPORTED：资料、代码、数据库、回归、专业审批和业务验收全部完成。
- REGRESSION_FAILED：新增变化破坏至少一个既有量表。
- UNSUPPORTED：当前模型明确不能安全表达，发布门禁必须阻止。

九、每轮实际执行要求

每轮开始：

1. 读取任务台账和所有 OPEN/INVESTIGATING 回归问题。
2. 检查当前分支、HEAD、dirty worktree 和迁移版本。
3. 选定唯一目标量表和唯一当前任务。
4. 列出计划修改文件、数据库影响、兼容性风险和回滚方式。

每轮结束：

1. 记录实际修改文件和迁移。
2. 记录实际执行命令、时间、测试数和结果。
3. 更新目标量表任务状态和证据链接。
4. 更新全部量表回归结果；未运行必须写 NOT_RUN，不能沿用旧 PASS。
5. 更新回归问题状态。
6. 列出仍需专业、业务、法务或运维完成的事项。

十、完成判定

单份量表只有同时满足以下条件才能标记 FULLY_SUPPORTED：

- 真实资料与授权有效；
- 三语内容完成审核；
- 计分、结果、风险和报表代码实现；
- Golden Case 全部通过；
- PostgreSQL、Web 和 Playwright 实际通过；
- Word/PDF/文本与 Web 语义一致；
- 全部历史量表兼容回归通过；
- 真实心理专业审批和业务验收完成；
- 发布、任务锁版和历史数据兼容性验收完成。

整个目标只有在计划内所有量表完成或被业务明确移出范围、且回归问题为零时才能结束。
```


本轮安全闭环补强：共享 `GENERIC_SINGLE_CHOICE`/`SCL90_RESTRICTED_PROFILE` closure 已把跨租户不可见、匿名未授权和 respondent 角色边界纳入每个 active 量表的 `security_boundaries` required check；最新 `REG-PLAYWRIGHT-20260815-144313` 中 7 个 active 版本均 17/17 PASS，并新增逐量表质量结果（`VALID`、缺失率 `0`、无质量问题）、三语 respondent payload 的有效题目集合/跳题声明、结果规则签名/常模选择语义、审批共享 release fingerprint，以及 `security_audit`（导入、双审批、重评分、报告查看、三种导出、高风险 warning）证据；高风险 warning 还要求状态为 `PENDING`，并通过 warning→result→answer sheet→task 链验证同租户归属。该技术 marker 不替代真实专业审批、危机处置责任或业务验收。
本轮执行器补强（`REG-018`～`REG-044`）：`run-scale-package-e2e.sh` 对 schema drop 失败强制返回失败并写入 `schemaCleanup`，不安全 schema 在创建临时目录前拒绝；E2E 临时关闭导出任务清理并提高仅测试超时，生产默认清理保持开启；隔离清理不可验证时不得报告全量 PASS；逐量表高风险 warning 断言补齐 `PENDING` 状态和同租户归属，并将 answer sheet/result 的质量状态、缺失率和质量问题、有效题目集合/跳题声明、结果规则/常模选择语义和发布指纹纳入 required checks；`.github/workflows/ci.yml` 已删除 Android job，保持 Android 明确排除；`ExportServiceTest` 已覆盖四种受控报表模板的 Word/PDF/TEXT 关键语义；registry runner 现在解析共享测试 XML 并以 `export_semantics=PASS` 逐量表登记；`generic-score-method-registry.json` 锁定五种通用计分方法、`REJECT/ALLOW/PRORATE` 缺失策略及平均类方法按已回答项/权重求平均且不额外放大的语义，质量矩阵现按五种方法 × REJECT/ALLOW/PRORATE 十五组合输出逐项 PostgreSQL marker；新增 `generic-recode-method-registry.json` 锁定三条不含原题的维度/时间重编码规则，合成 `TIME`/`SLIDER` 矩阵同时由 Playwright 与 PostgreSQL marker 验证；REG-027 修正非加权/加权 PRORATE 因子和 ALLOW/PRORATE trace 记录，REG-028 增加平均/加权平均单测、契约校验和 weighted-average trace 标签，REG-029 将 ALLOW/PRORATE 质量策略持久化回归扩展为全方法交叉矩阵，REG-030 又补齐 REJECT 半答卷拒绝与无结果持久化证据，REG-031 将 REJECT 的“拒绝提交且不产生结果”写入机器可读方法契约并重新执行全量回归；REG-037 又闭合 `TIME` 在批量建题、XLSX 导入和源包校验路径的统一字段约束；REG-038 补齐管理端建题下拉/题型显示、ReportDetail 题型标签和中日英导入提示；REG-039 闭合 `TEXT_WITH_OPTION` 可选文本和选项数量跨入口边界；REG-040 统一 source-package、批量建题和旧 XLSX 对七种题型冲突元数据的拒绝契约并规范化导入题型；REG-043 将逐题显示 marker 提升为机器 required check；REG-044 统一 Golden Case 与提交路径的声明式跳题语义并新增级联跳题技术夹具；定向后端检查与 `REG-PLAYWRIGHT-20260815-144313` 全量 active 回归同步通过。`REG-PLAYWRIGHT-20260815-144313` 是当前本地证据；REG-032 的 current-pointer 门禁、REG-033 的远程 artifact gate 均保留，但本轮不执行 CI artifact。本轮不把 CI artifact 作为完成条件。

REG-036 已在 `REG-PLAYWRIGHT-20260815-100832` 中 supersede 上述执行器状态：新增七种合成题型路径的 source-package 字段、answer persistence、scoring trace 和 report markers 均由隔离 Playwright/PostgreSQL 通过；该技术夹具不建立任何真实候选量表支持。
REG-037 已在 `REG-PLAYWRIGHT-20260815-102614` 中 supersede REG-036 的 current evidence：批量建题、旧 XLSX 导入和 source-package 校验均接受受控 `TIME` 题型并拒绝冲突元数据；三个定向后端测试和 7 个 active 版本全量回归均通过，未引入原题或新的量表包。
REG-038 已在 `REG-PLAYWRIGHT-20260815-111056` 中 supersede REG-037 的 current evidence：管理端 `ScaleListPage` 可创建并显示 `TIME`，`ReportDetail` 可用三语标签展示 `TIME`，三语导入提示同步列出 `TEXT`/`TIME`；前端 tsc、104 个 Vitest 和 7 个 active 版本全量回归通过，未引入原题或新的量表包。

当前执行状态补充：`REG-001`～`REG-044` 的工程任务已完成；最新 current evidence 为 `REG-PLAYWRIGHT-20260815-144313`，并已验证每个 active generic/SCL90 包逐题 respondent 显示/导航、中文/日文/英文报告 Web，以及合成 q1=0→跳过 q2 的 Web/API/数据库分支。`REG-ISSUE-20260815-044`、`REG-ISSUE-20260815-045`、`REG-ISSUE-20260815-047` 和 `REG-ISSUE-20260815-048` 的合成夹具/SQL 证据缺口均已修复并由 PASS run 关闭；唯一保持 OPEN 的外部回归问题仍是 `REG-ISSUE-20260814-020`（PSS-10 缺真实版本/授权/三语电子资料），因此不生成新的真实 ScalePackage；已有技术版本仍按 `TECHNICALLY_VERIFIED/BLOCKED_EXTERNAL` 管理，不能升级为正式支持。

研究模式边界：本轮目标是验证适配系统的显示、端到端测试、算法/scoring trace、结果解释和报表语义，不以外部授权作为无题目技术夹具或通用框架测试的前置条件；但原题、翻译、手册阈值、常模和对外分发仍只在用户提供相应资料后导入。缺少这些资料时只保持技术状态和治理阻塞，不把研究闭环写成该量表的正式支持或授权结论。

REG-039 当前补充已 supersede 上述 REG-038 状态：七种题型跨管理端批量建题、source-package、旧 XLSX、respondent、发布 Golden 和报表路径的静态审计闭合 `TEXT_WITH_OPTION` 可选文本与单选/多选/矩阵至少两个选项边界；`ScaleServiceTest`、`ScaleSourcePackageValidationTest`、发布治理定向测试通过。`REG-PLAYWRIGHT-20260815-113359` 在隔离 PostgreSQL 中验证 8 个注册版本/7 个 active 版本各 16/16，Playwright 10 passed/1 skipped，五方法/十五策略/三重编码/七题型矩阵、三语 Web/Word/PDF/TEXT、任务锁版、历史兼容、schema cleanup=0 和 current pointers 全部 PASS；报告 SHA-256 为 `fec0a2a57b39ee900d5aaf854e5ebf1affbc87cf0f217164a966c8b083afd3ef`。未引入任何量表原题或新的 ScalePackage；CI 不执行，Android 继续排除。

REG-040 当前补充已 supersede 上述 REG-039 状态：审计并统一 source-package、管理端批量建题和旧 XLSX 导入对七种题型冲突元数据的拒绝契约（`TIME` 不接收 slider/option/matrix/text，`SLIDER` 不接收 options/selection/text/matrix，非 `MATRIX` 不接收 matrix 坐标，单选/多选/矩阵不接收 text 元数据），同时保持 `TEXT_WITH_OPTION` 文本可选并规范化导入题型。新增定向后端测试通过；`REG-PLAYWRIGHT-20260815-124234` 在隔离 PostgreSQL 中验证 8 个注册版本/7 个 active 版本各 16/16，Playwright 10 passed/1 skipped，五方法/十五策略/三重编码/七题型矩阵、合成 respondent 逐题显示/导航、三语 Web/Word/PDF/TEXT、任务锁版、历史兼容、schema cleanup=0 和 current pointers 全部 PASS；报告 SHA-256 为 `86ae21cf906523843efeef565f2616ff065fdaa0ea33195a301d1adf12d82ef4`。未引入任何量表原题或新的 ScalePackage；CI 不执行，Android 继续排除。
REG-041 当前补充：新增 [`scale-capability-catalog.json`](../scale-packages/scale-capability-catalog.json) 和 `validate_scale_capability_catalog.py`，将剩余候选按已验证的通用单选、合成时间/重编码技术夹具或明确不支持的他评/访谈能力分类，并列出待核验输入。该目录不含题目、翻译、阈值、常模、Golden Case 或授权声明，不改变 active 注册表，也不把候选升级为正式支持；后续每份真实版本化资料仍须单独建立 ScalePackage 并执行全量回归。

REG-042/REG-043/REG-044 当前补充：共享 closure 已从 payload 级检查扩展为每个无跳题 active 包的 respondent 逐题题干/选项/下一题导航，并在 zh-CN、ja-JP、en-US 报告 Web 页面核对本地化量表名、结果标题、解释和建议；`question_display` 已成为注册表 required check；`REG-PLAYWRIGHT-20260815-144313` 7 个 active 版本各 17/17，合成跳题由独立 Web/API/SQL 夹具覆盖。该证据只证明通用 renderer/链路技术能力，不改变真实内容、授权或正式支持状态。

## 当前执行入口

REG-035 前一轮本地全量证据（2026-08-15）为 `REG-PLAYWRIGHT-20260815-094237`，报告为 `build/reports/scale-adaptation/registry-psy_e2e_1786786908_77117.json`（SHA-256 `ad270baade632e6fc686e44e37930d5f66d64df0e6d540409fe83f419b6927c0`）；该段落仅保留用于审计。该运行在隔离 PostgreSQL 中完成 8 个注册版本/7 个 active 技术版本，7 个 active 版本均 16/16 required checks PASS，五方法矩阵、`REJECT/ALLOW/PRORATE` 十五组合质量矩阵和三条通用维度/时间重编码规则矩阵均 PASS，schema cleanup 通过，7 个 active current pointers 已指向本次 run；registry immutable fingerprint SHA-256 为 `faaddb213c617d29624fdf134f7c3a9dfe3005ddbda0d0763b85ef727c5d8776`，通用计分契约 SHA-256 为 `bfccf8546f331901a4af631118690d2244ac5ea1b0cb58a4d37bb53202d5eff5`，通用重编码契约 SHA-256 为 `c2284c7113441bd96cbfa97d2c1ef0d3bc398a2315cb445e86f8f01dbd127e1f`；旧 `REG-PLAYWRIGHT-20260815-070829` 仅保留为历史证据。本次不执行 CI。

REG-038 阶段的历史证据（已被 REG-040 supersede）为 `REG-PLAYWRIGHT-20260815-111056`，报告为 `build/reports/scale-adaptation/registry-psy_e2e_1786792209_81979.json`（SHA-256 `3b01b4ea06f39c88c17cc23839a4e3ee2466a8b7b92653bce1655cc16bf2d6d5`）。该 run 在隔离 PostgreSQL 中完成 8 个注册版本/7 个 active 技术版本且各 16/16 PASS，Playwright 10 passed/1 skipped、所有 runtime checks 执行，并继续锁定五种通用计分方法、REJECT/ALLOW/PRORATE 十五组合、三条合成重编码规则和七种无原题题型路径；管理端 TIME 入口、ReportDetail TIME 标签和三语导入提示改动后的三语/报表/任务锁版/历史兼容均通过，schema cleanup=0，current pointers 已自动写回。当前 registry immutable fingerprint 为 `a6d3bec1024b7a5234e3edd03dc509462236bc1be98106d6d1dda98daf2ff1d5`；这仍不是任何候选量表的正式支持。本次不执行 CI。

本节后文保留的 `REG-PLAYWRIGHT-20260815-070829`/REG-033/`REG-PLAYWRIGHT-20260815-094237`/`REG-PLAYWRIGHT-20260815-100832`/`REG-PLAYWRIGHT-20260815-102614`/`REG-PLAYWRIGHT-20260815-115320` 描述均为历史实现与前一轮证据，不覆盖当前 `REG-PLAYWRIGHT-20260815-144313`。

REG-033 的远程 artifact gate 实现保留在仓库中，但按本轮边界不执行、不等待远程工作流证据；本轮只以本地 wrapper、隔离 PostgreSQL、Playwright、报告验证器和 current-pointer 复核作为完成依据。

下方较早的“当前执行状态”段落保留作历史审计；本轮最终 current evidence 以稍后明确列出的 `REG-PLAYWRIGHT-20260815-144313` 为准。

当前执行状态（历史段落保留审计）：`REG-001`～`REG-044` 的工程任务已完成；最终 current evidence 为 `REG-PLAYWRIGHT-20260815-144313`，7 个 active 版本各 17/17，current pointers 均自动指向该 run；本次还验证了 q1=0→跳过 q2 的 respondent Web/API/数据库分支。注册表校验器会阻止 `TECHNICALLY_VERIFIED/FULLY_SUPPORTED` 与最新 `PARTIAL/FAIL` 回归状态不一致，全量入口在 PostgreSQL 预检失败时保存阶段、退出码和数据影响 JSON，独立报告验证器会阻止缺少 immutable fingerprint、required checks、五方法逐项 marker、五方法×REJECT/ALLOW/PRORATE 十五组合策略 marker、三条通用重编码 marker 或七种受控题型 `question_types` marker 的工件进入门禁。已知通用单选量表现在使用共享 `GENERIC_SINGLE_CHOICE` 闭环、共享源包校验器和共享 PostgreSQL 证据，不再复制逐量表 Controller、Service、Playwright 或 SQL 分支；SCL-90 使用明确的 `SCL90_RESTRICTED_PROFILE`，只增加受限算法绑定、专用源包校验和专用 PostgreSQL 语义断言。K10 和 SCS-SF 已加入同一 ScalePackage/通用计分、结果、Web、Word、PDF、文本框架，通用校验与证据执行器由 `generic-score-method-registry.json` 锁定后端支持的 `SIMPLE_SUM`、`REVERSE_SUM`、`WEIGHTED_SUM`、`AVERAGE`、`WEIGHTED_AVERAGE` 及 `REJECT/ALLOW/PRORATE` 缺失策略，并按权重/聚合元数据重算 scoring trace；`generic-recode-method-registry.json` 另锁定不含原题的 `RECODE_SUM_TO_0_3`、`SLEEP_DURATION_RECODE_0_3`、`SLEEP_EFFICIENCY_RECODE_0_3`，并通过合成 `TIME`/`SLIDER` ScalePackage 夹具验证规则、维度 trace 和报告结果；REG-027 已验证非加权 PRORATE 按题数、加权 PRORATE 按权重且 ALLOW/PRORATE 策略写入 trace，REG-028 已验证平均/加权平均在 ALLOW/PRORATE 下按已回答项/权重求平均且不额外放大并区分 weighted-average trace 标签，REG-029 已将质量策略持久化/报告/维度/任务锁定证据扩展到五方法×ALLOW/PRORATE 十组合，REG-031 进一步把 REJECT 的“拒绝提交且不产生结果”写入机器可读契约并在新全量报告中验证，REG-037 又验证 `TIME` 在批量建题、XLSX 导入和 source-package 校验中的统一配置边界，REG-043 将 `question_display` 作为 required check，REG-044 将 Golden Case 与提交路径的声明式跳题语义统一。报告还包含每个版本的 `export_semantics=PASS`（共享 `ExportServiceTest` XML：8 tests/0 skipped/0 failures/0 errors）、五方法 marker、十五个方法/策略 marker、三条重编码 marker、七种 `question_types` marker、`all_methods_policies` 以及 `policy_REJECT`/`policy_ALLOW`/`policy_PRORATE`/`all_policies`；报告必须继续标记 `android=EXCLUDED`。改动前的历史报告只用于审计；所有量表的正式授权/使用范围、三语审校、专业双审批、常模/危机治理和业务验收仍不能由技术 PASS 代替。

最终技术回归已由 `REG-PLAYWRIGHT-20260815-144313` 收口：报告 `build/reports/scale-adaptation/registry-psy_e2e_1786804943_3752.json`，SHA-256 `40c6afafed0fd050edf4dd5bf47e3d47af9695173ea60645e8d54cf80021a06c`，registry immutable fingerprint `a5f7f965f6e3c0f449a8c105d792e182ff25778a2e6a5e6a0aa9330531e172cd`；7 个 active 版本各 17/17，五方法/十五策略/三条重编码/七种题型、每个 active 包逐题 respondent 显示/导航、中文/日文/英文报告 Web、q1=0→跳过 q2 的 respondent Web/API/数据库分支、scoring trace、结果解释、三语 Web、Word/PDF/TEXT、任务锁版、历史兼容、schema cleanup 和 current pointers 全部 PASS，`ExportServiceTest` 8 tests/0 skipped/0 failures/0 errors。该报告仍是技术框架/合成夹具证据，不建立真实量表正式支持；`REG-ISSUE-20260815-047`、`REG-ISSUE-20260815-048`、`REG-ISSUE-20260815-049` 已关闭，外部资料问题 `REG-ISSUE-20260814-020` 仍保持 OPEN。

下一项工程目标是继续让“已知且资料完整”的量表走已经由 PHQ-9、K6、K10、WHO-5、GAD-7、SCS-SF 证明的快速适配路径：下一候选为 PSS-10，但在真实版本、反向题规则、三语内容和使用范围资料齐全前保持 `INPUT_PENDING`。提供真实版本化 ScalePackage、三语内容、Golden Case、结果规则和外部治理资料后，先判断能否使用 `GENERIC_SINGLE_CHOICE` 或后续受控通用 profile；只有手册无法由白名单表达时才增加专用算法。SCS-SF仍等待中日文正式翻译审校、目标人群/无临床常模边界、真实专业双审批和业务验收；K10仍等待正式三语审校、切点/适用人群和引用/使用范围归档以及真实双审批；PHQ-9仍等待正式三语审校、日文电子使用权、适用人群、题9危机响应责任人/SLA、结果解释与真实双审批；GAD-7和WHO-5仍等待正式三语审校、适用人群、license scope、结果解释与真实双审批；SCL90-v2继续等待授权范围归档、三语翻译权/审校、人口常模、危机处置责任和真实双审批/业务验收，且不得把 profile-only 版本说成临床正式支持；Android继续排除。
