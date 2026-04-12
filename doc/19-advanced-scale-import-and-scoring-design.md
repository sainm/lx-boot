# 复杂题型与复杂评分 Excel 导入设计

> 目的：把“复杂题型支持”和“复杂评分算法”正式落入 Excel 量表导入设计，作为后续后端、前端、模板、测试和运营协作的统一基线。
> 当前结论：模板结构先完成，能力分阶段开放；不要求一次性全部上线。

## 1. 文档范围

本文档只解决 4 件事：
- Excel 模板如何表达复杂题型。
- Excel 模板如何表达复杂评分算法。
- 导入解析、校验、确认导入时分别允许到什么程度。
- 后续实现时，数据模型、评分引擎、预警引擎应该怎样落地。

本文档不直接替代：
- [16-scale-import-design.md](./16-scale-import-design.md)
- [17-scale-import-template-guide.md](./17-scale-import-template-guide.md)
- [scoring-design.md](./scoring-design.md)

关系说明：
- `16` 号文档管“导入功能总体设计”。
- `17` 号文档管“模板怎么填”。
- `scoring-design` 管“评分逻辑本身”。
- 本文档管“复杂题型/复杂评分如何进入 Excel 导入链路”。

## 2. 目标

### 2.1 业务目标

让量表配置不再只支持：
- 单选题
- 原始分区间规则

而是逐步支持：
- 多选题
- 滑杆题
- 矩阵题
- 文本 + 选项组合题
- 常模换算
- `Z_SCORE`
- `T_SCORE`
- 按年龄 / 性别 / 组织类型分层常模
- 高危题单独触发预警

### 2.2 工程目标

保证以下几件事同时成立：
- 模板结构稳定，避免频繁改 Excel 表头。
- 旧模板仍可继续导入。
- 未开放的能力可以先“预留列位”，但不会误导成已可用。
- 后续每开放一个能力，尽量不重做整条导入链路。

## 3. 设计原则

### 3.1 向后兼容

当前已上线能力仍以：
- `SINGLE_CHOICE`
- `RAW_SCORE`

为主，不破坏已有量表导入流程。

### 3.2 模板先行

先稳定模板结构，再逐步开放：
- 解析识别
- 预览展示
- 确认导入
- 正式评分
- 正式预警

### 3.3 分层开放

每个新能力都按 4 层推进：
1. 模板列位预留
2. 解析与预览识别
3. 确认导入落库
4. 答题与评分链路正式生效

### 3.4 风险可控

对于未开放能力，系统必须显式提示：
- 该列为预留列
- 当前版本尚未启用
- 解析可识别，但确认导入不可放行，或仅允许 warning 不生效

## 4. 分阶段实施顺序

### 4.1 第一阶段：模板与文档先完成

目标：
- 模板增加复杂题型与复杂评分预留列
- 下载模板、样例模板、模板说明文档一致
- 当前导入确认阶段仍只正式放行 `SINGLE_CHOICE`

交付物：
- `GET /api/v1/scales/import-template`
- `doc/templates/scale-import-template.xlsx`
- `doc/templates/scale-import-sample.xlsx`
- [17-scale-import-template-guide.md](./17-scale-import-template-guide.md)

### 4.2 第二阶段：先开放最有价值的两类题型

目标：
- 开放 `MULTI_SELECT`
- 开放 `SLIDER`
- 升级答案存储结构
- 评分引擎支持多值题和数值题
- 状态：已完成
- 支持高危题规则的基础触发

### 4.3 第三阶段：开放复杂评分

目标：
- 开放 `norms`
- 开放 `Z_SCORE`
- 开放 `T_SCORE`
- 开放按年龄 / 性别 / 组织类型分层常模
- 报告和统计展示标准分
- 状态：已完成

### 4.4 第四阶段：开放复杂组合题

目标：
- 开放 `MATRIX`
- 开放 `TEXT_WITH_OPTION`
- 优化报告展示和预览交互
- 状态：已完成（建议在预发环境验证展示细节）

## 5. 题型范围

### 5.1 题型枚举

| 题型 | 编码 | 状态 | 说明 |
| --- | --- | --- | --- |
| 单选题 | `SINGLE_CHOICE` | 已启用 | 一题一选项 |
| 多选题 | `MULTI_SELECT` | 规划中 | 一题多选项 |
| 滑杆题 | `SLIDER` | 规划中 | 数值型答案 |
| 矩阵题 | `MATRIX` | 规划中 | 行列型结构题 |
| 文本 + 选项组合题 | `TEXT_WITH_OPTION` | 规划中 | 选项题附带文本输入 |

### 5.2 优先级

建议优先顺序：
1. `MULTI_SELECT`
2. `SLIDER`
3. `high_risk_rules`
4. `norms`
5. `Z_SCORE` / `T_SCORE`
6. `MATRIX`
7. `TEXT_WITH_OPTION`

原因：
- 多选和滑杆最常见，业务价值高，且结构相对独立。
- 常模和高危题规则建立在评分链路改造之上。
- 矩阵题和文本组合题对答题端、存储端、报告端影响更大。

## 6. Excel 模板设计

### 6.1 工作表列表

复杂题型与复杂评分设计下，模板包含 7 个工作表：
1. `scale`
2. `dimensions`
3. `questions`
4. `options`
5. `result_rules`
6. `norms`
7. `high_risk_rules`

### 6.2 工作表职责

`scale`
- 量表级配置
- 评分策略
- 是否启用常模
- 是否启用高危题预警

`dimensions`
- 维度定义

`questions`
- 题干与题型
- 题型特有参数

`options`
- 选项定义
- 多选互斥项
- 选项分组

`result_rules`
- 风险规则
- 原始分规则或标准分规则

`norms`
- 常模定义
- 分层条件

`high_risk_rules`
- 高危题触发规则

## 7. 字段设计

### 7.1 `scale`

新增或重点关注字段：

| 列名 | 是否当前启用 | 说明 |
| --- | --- | --- |
| `scoreMethod` | 是 | 当前已有 `SIMPLE_SUM / REVERSE_SUM / WEIGHTED_SUM` |
| `scoreCoefficient` | 是 | 原始分换算系数 |
| `normStrategy` | 预留 | `RAW_SCORE / Z_SCORE / T_SCORE` |
| `normDefaultGroup` | 预留 | 默认常模组编码 |
| `highRiskWarningEnabled` | 预留 | 是否启用题项级高危预警 |

建议规则：
- 未启用常模时，`normStrategy` 默认为 `RAW_SCORE`
- 未启用高危题预警时，`highRiskWarningEnabled=false`

### 7.2 `questions`

基础字段保留：
- `questionNo`
- `questionTitle`
- `questionType`
- `dimensionCode`
- `requiredFlag`
- `reverseScoreFlag`
- `weightValue`
- `sortNo`

扩展字段：

| 列名 | 适用题型 | 说明 |
| --- | --- | --- |
| `optionSelectionLimit` | `MULTI_SELECT` | 多选题最多可选数 |
| `sliderMin` | `SLIDER` | 最小值 |
| `sliderMax` | `SLIDER` | 最大值 |
| `sliderStep` | `SLIDER` | 步长 |
| `textInputEnabled` | `TEXT_WITH_OPTION` | 是否允许文本输入 |
| `textInputPlaceholder` | `TEXT_WITH_OPTION` | 文本输入提示 |
| `matrixGroupCode` | `MATRIX` | 矩阵题分组编码 |
| `rowCode` | `MATRIX` | 行编码 |
| `columnCode` | `MATRIX` | 列编码 |

### 7.3 `options`

基础字段保留：
- `questionNo`
- `optionCode`
- `optionLabel`
- `scoreValue`
- `sortNo`

扩展字段：

| 列名 | 适用题型 | 说明 |
| --- | --- | --- |
| `exclusiveFlag` | `MULTI_SELECT` | 互斥项，如“以上都没有” |
| `optionGroupCode` | `MULTI_SELECT` / `MATRIX` | 选项分组 |

### 7.4 `result_rules`

基础字段保留：
- `dimensionCode`
- `riskLevel`
- `scoreMin`
- `scoreMax`
- `resultTitle`
- `resultDescription`
- `suggestionText`
- `sortNo`

扩展字段：

| 列名 | 说明 |
| --- | --- |
| `scoreSource` | `RAW_SCORE / Z_SCORE / T_SCORE` |
| `normCode` | 关联 `norms.normCode` |

说明：
- 当前阶段默认只正式支持 `RAW_SCORE`
- 后续开放常模后，同一量表可同时存在原始分规则与标准分规则

### 7.5 `norms`

| 列名 | 说明 |
| --- | --- |
| `normCode` | 常模编码 |
| `normName` | 常模名称 |
| `dimensionCode` | 为空表示总分常模 |
| `applicableTarget` | 适用对象 |
| `ageMin` | 最小年龄 |
| `ageMax` | 最大年龄 |
| `gender` | 性别条件 |
| `orgType` | 组织类型条件 |
| `meanScore` | 原始分均值 |
| `stdDeviation` | 原始分标准差 |
| `tScoreMean` | T 分均值，通常为 `50` |
| `tScoreStdDeviation` | T 分标准差，通常为 `10` |
| `sortNo` | 排序 |

### 7.6 `high_risk_rules`

| 列名 | 说明 |
| --- | --- |
| `ruleCode` | 规则编码 |
| `questionNo` | 关联题号 |
| `optionCode` | 选项型题的触发条件 |
| `scoreThreshold` | 数值型题的阈值 |
| `warningLevel` | 预警等级 |
| `resultTitle` | 规则标题 |
| `resultDescription` | 规则说明 |
| `suggestionText` | 干预建议 |
| `sortNo` | 排序 |

## 8. 题型级校验规则

### 8.1 `SINGLE_CHOICE`

- 至少有 2 个选项
- 不允许填写 `sliderMin / sliderMax / sliderStep`
- 不允许填写 `matrixGroupCode / rowCode / columnCode`

### 8.2 `MULTI_SELECT`

- 至少有 2 个选项
- `optionSelectionLimit` 可为空
- 若存在 `exclusiveFlag=true` 的选项，建议仅允许 1 个
- 若有互斥项，则 `optionSelectionLimit` 不能小于 1

### 8.3 `SLIDER`

- 可以没有 `options`
- 必填 `sliderMin`
- 必填 `sliderMax`
- `sliderMin < sliderMax`
- `sliderStep > 0`
- `scoreThreshold` 类规则可以直接用于高危题判定

### 8.4 `MATRIX`

- 必填 `matrixGroupCode`
- 同一 `matrixGroupCode` 下必须形成完整行列关系
- 行列定义不可重复

### 8.5 `TEXT_WITH_OPTION`

- 至少保留 1 个选项
- 若 `textInputEnabled=true`，建议填写 `textInputPlaceholder`
- 文本输入默认不直接计分，除非后续明确扩展文本评分策略

## 9. 评分设计

### 9.1 分层计算链路

建议评分链路拆成 5 步：
1. 计算原始答案得分
2. 计算题目有效分
3. 汇总总分与维度分
4. 常模换算得到 `Z_SCORE` / `T_SCORE`
5. 匹配结果规则并触发高危题预警

### 9.2 原始分与标准分

基础定义：
- `rawScore`：原始分
- `zScore`：标准分 Z
- `tScore`：标准分 T

建议公式：
- `zScore = (rawScore - meanScore) / stdDeviation`
- `tScore = tScoreMean + zScore * tScoreStdDeviation`

### 9.3 分层常模匹配规则

建议匹配顺序：
1. `dimensionCode`
2. `applicableTarget`
3. `ageMin/ageMax`
4. `gender`
5. `orgType`
6. `sortNo`

匹配原则：
- 优先最精确命中的常模
- 若无精确命中，则回退到默认组 `normDefaultGroup`
- 仍无命中时，不执行标准分换算，回退到 `RAW_SCORE`

### 9.4 高危题规则优先级

建议优先级：
1. 先算高危题规则
2. 再算总分/维度结果规则
3. 若高危题命中，允许直接提升最终 `warningLevel`

建议结果保留：
- 命中的规则编码
- 命中的题号
- 命中的选项或阈值
- 提升前等级
- 提升后等级

## 10. 解析、预览、确认导入策略

### 10.1 解析阶段

解析阶段应做到：
- 识别全部预留列
- 识别全部预留工作表
- 输出结构化预览
- 对未开放能力给出清晰提示

建议输出：
- `ERROR`：当前版本完全不能接受
- `WARNING`：模板合法，但当前版本暂不启用

### 10.2 预览阶段

预览页建议明确标识：
- 当前已启用能力
- 预留字段
- 未启用字段
- 将被忽略的字段

### 10.3 确认导入阶段

建议按开关控制：
- `MULTI_SELECT_ENABLED`
- `SLIDER_ENABLED`
- `MATRIX_ENABLED`
- `TEXT_WITH_OPTION_ENABLED`
- `NORM_SCORING_ENABLED`
- `HIGH_RISK_RULE_ENABLED`

未启用时：
- 可以允许解析
- 但不允许确认导入生效
- 或确认导入时明确忽略并记录 warning

## 11. 数据模型落点

### 11.1 量表配置侧

建议扩展：
- `psy_scale`
  - `norm_strategy`
  - `norm_default_group`
  - `high_risk_warning_enabled`

- `psy_scale_question`
  - `question_type`
  - `option_selection_limit`
  - `slider_min`
  - `slider_max`
  - `slider_step`
  - `text_input_enabled`
  - `text_input_placeholder`
  - `matrix_group_code`
  - `row_code`
  - `column_code`

- `psy_scale_question_option`
  - `exclusive_flag`
  - `option_group_code`

### 11.2 常模与高危题规则

建议新增：
- `psy_scale_norm`
- `psy_scale_high_risk_rule`

### 11.3 作答数据侧

当前若答案仍是“单题单 option”，将无法支撑多选、滑杆、文本组合题。

建议演进为：
- 一题支持多条 answer item
- 或一题一条 answer item，但支持：
  - `selected_option_ids`
  - `numeric_value`
  - `text_value`

## 12. Excel 示例约定

### 12.1 多选题示例

`questions`

| questionNo | questionType | optionSelectionLimit |
| --- | --- | --- |
| 1 | `MULTI_SELECT` | 2 |

`options`

| questionNo | optionCode | optionLabel | scoreValue | exclusiveFlag |
| --- | --- | --- | --- | --- |
| 1 | A | 睡眠差 | 1 | false |
| 1 | B | 食欲差 | 1 | false |
| 1 | C | 注意力差 | 1 | false |
| 1 | D | 以上都没有 | 0 | true |

### 12.2 滑杆题示例

`questions`

| questionNo | questionType | sliderMin | sliderMax | sliderStep |
| --- | --- | --- | --- | --- |
| 2 | `SLIDER` | 0 | 10 | 1 |

### 12.3 常模示例

`norms`

| normCode | dimensionCode | ageMin | ageMax | gender | orgType | meanScore | stdDeviation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| STUDENT_F_18_22 |  | 18 | 22 | FEMALE | school | 10 | 3 |

### 12.4 高危题示例

`high_risk_rules`

| ruleCode | questionNo | optionCode | warningLevel |
| --- | --- | --- | --- |
| SELF_HARM_Q9_D | 9 | D | HIGH |

## 13. 验收标准

### 13.1 文档与模板层

- 模板、样例、说明文档、设计文档互相一致
- 每个预留列都有明确用途
- 每个未开放能力都有明确状态说明

### 13.2 解析层

- 能识别全部新增列
- 能识别 `norms` 和 `high_risk_rules`
- 能对未启用能力给出清晰 warning 或 error

### 13.3 实现层

至少在后续迭代中分批达到：
- `MULTI_SELECT` 可导入、可作答、可评分
- `SLIDER` 可导入、可作答、可评分
- `high_risk_rules` 可触发预警
- `norms` 可换算 `Z_SCORE / T_SCORE`

## 14. 当前结论

这份设计确定了一个原则：
- 模板结构现在就到位
- 能力按阶段开放
- 先做 `MULTI_SELECT` 和 `SLIDER`
- 再做 `high_risk_rules` 和 `norms`
- 最后补 `MATRIX` 与 `TEXT_WITH_OPTION`

后续如果继续实现，我建议直接按这 4 个开发包推进：
1. 题型模型与答案存储升级
2. 导入解析与预览升级
3. 评分引擎与常模换算升级
4. 高危题预警链路升级
