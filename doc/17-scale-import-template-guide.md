# 量表导入模板说明

> 说明：本文档用于指导测评管理员填写量表导入模板，并与仓库中的模板文件、系统下载模板保持一致。

## 1. 交付文件

当前目录配套提供两个 Excel 文件：
- `doc/templates/scale-import-template.xlsx`
  - 空白模板，保留表头与填写结构。
- `doc/templates/scale-import-sample.xlsx`
  - 示例模板，提供一份可参考的单选题样例数据。

系统接口 `GET /api/v1/scales/import-template` 下载的模板应视为当前标准模板。

## 2. 当前支持范围

当前导入确认阶段实际生效的能力：
- 单个量表导入
- `xlsx` 文件
- 新增模式
- `SINGLE_CHOICE` 单选题
- `MULTI_SELECT` 多选题
- `SLIDER` 滑杆题
- `MATRIX` 矩阵题
- `TEXT_WITH_OPTION` 文本 + 选项组合题
- 原始分/标准分口径：`RAW_SCORE` / `Z_SCORE` / `T_SCORE`
- 常模换算与高危题预警生效

当前模板中已预留，但尚未在正式导入确认阶段启用的能力：
- 多量表批量导入
- 覆盖更新已有量表
- 增量合并已有题目

当前仍不支持：
- 多量表批量导入
- 覆盖更新已有量表
- 增量合并已有题目
- 直接通过 Excel 完整导入并启用复杂题型与复杂评分链路

## 3. 工作表说明

模板包含 7 个工作表：
1. `scale`
2. `dimensions`
3. `questions`
4. `options`
5. `result_rules`
6. `norms`
7. `high_risk_rules`

说明：
- `scale` 只允许填写一条量表主记录。
- `dimensions` 可为空。
- `questions`、`options`、`result_rules` 为当前主工作表。
- `norms`、`high_risk_rules` 为后续复杂评分与题项级预警预留。

## 4. 各工作表字段

### 4.1 `scale`

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `scaleCode` | 是 | 量表编码，全局唯一 | `PHQ9` |
| `scaleName` | 是 | 量表名称 | `PHQ-9 抑郁筛查量表` |
| `description` | 否 | 量表简介 | `用于抑郁风险初筛` |
| `applicableTarget` | 否 | 适用对象 | `student` |
| `versionNo` | 否 | 版本号 | `v1` |
| `scoreMethod` | 是 | 当前实际启用的评分方式 | `SIMPLE_SUM` |
| `scoreCoefficient` | 否 | 分数换算系数，默认 `1.0` | `1.0` |
| `anonymousSupported` | 否 | 是否允许匿名 | `false` |
| `reportTemplate` | 否 | 默认报告模板说明 | `标准心理测评报告模板` |
| `normStrategy` | 否 | 预留字段，后续用于常模换算策略 | `Z_SCORE` |
| `normDefaultGroup` | 否 | 预留字段，后续用于默认常模组 | `STUDENT_DEFAULT` |
| `highRiskWarningEnabled` | 否 | 预留字段，后续用于开启高危题预警 | `true` |

### 4.2 `dimensions`

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `dimensionCode` | 是 | 维度编码，量表内唯一 | `MOOD` |
| `dimensionName` | 是 | 维度名称 | `情绪状态` |
| `description` | 否 | 维度说明 | `抑郁情绪相关维度` |
| `sortNo` | 否 | 排序号 | `1` |

### 4.3 `questions`

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `questionNo` | 是 | 题号，量表内唯一 | `1` |
| `questionTitle` | 是 | 题干 | `做事时提不起劲或没有兴趣` |
| `questionType` | 是 | 支持 `SINGLE_CHOICE` / `MULTI_SELECT` / `SLIDER` / `MATRIX` / `TEXT_WITH_OPTION` | `SINGLE_CHOICE` |
| `dimensionCode` | 否 | 所属维度编码 | `MOOD` |
| `requiredFlag` | 否 | 是否必答 | `true` |
| `reverseScoreFlag` | 否 | 是否反向计分 | `false` |
| `weightValue` | 否 | 权重，默认 `1.0` | `1.0` |
| `sortNo` | 否 | 排序号 | `1` |
| `optionSelectionLimit` | 否 | 预留字段，多选题可选数量上限 | `2` |
| `sliderMin` | 否 | 预留字段，滑杆题最小值 | `0` |
| `sliderMax` | 否 | 预留字段，滑杆题最大值 | `10` |
| `sliderStep` | 否 | 预留字段，滑杆题步长 | `1` |
| `textInputEnabled` | 否 | 预留字段，是否允许文本补充输入 | `false` |
| `textInputPlaceholder` | 否 | 预留字段，文本输入提示语 | `请补充说明` |
| `matrixGroupCode` | 否 | 预留字段，矩阵题分组编码 | `SLEEP_MATRIX` |
| `rowCode` | 否 | 预留字段，矩阵题行编码 | `ROW_1` |
| `columnCode` | 否 | 预留字段，矩阵题列编码 | `COL_A` |

### 4.4 `options`

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `questionNo` | 是 | 关联题号 | `1` |
| `optionCode` | 是 | 选项编码，题目内唯一 | `A` |
| `optionLabel` | 是 | 选项内容 | `完全不会` |
| `scoreValue` | 是 | 选项分值 | `0` |
| `sortNo` | 否 | 排序号 | `1` |
| `exclusiveFlag` | 否 | 预留字段，多选题中是否互斥 | `false` |
| `optionGroupCode` | 否 | 预留字段，选项分组编码 | `FREQ` |

### 4.5 `result_rules`

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `dimensionCode` | 否 | 为空表示总分规则，填写表示维度规则 | `MOOD` |
| `riskLevel` | 是 | 风险等级 | `LOW` |
| `scoreMin` | 是 | 最小分值 | `0` |
| `scoreMax` | 是 | 最大分值 | `4` |
| `resultTitle` | 否 | 结果标题 | `无明显风险` |
| `resultDescription` | 否 | 结果说明 | `当前量表结果处于较低风险区间` |
| `suggestionText` | 否 | 建议内容 | `保持规律作息与日常观察` |
| `sortNo` | 否 | 排序号 | `1` |
| `scoreSource` | 否 | 预留字段，区分原始分/标准分口径 | `RAW_SCORE` |
| `normCode` | 否 | 预留字段，关联常模编码 | `STUDENT_DEFAULT` |

### 4.6 `norms`

当前状态：
- 常模工作表已启用，导入确认阶段会写入业务表

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `normCode` | 是 | 常模编码 | `STUDENT_DEFAULT` |
| `normName` | 否 | 常模名称 | `大学生默认常模` |
| `dimensionCode` | 否 | 为空表示总分常模，填写表示维度常模 | `MOOD` |
| `applicableTarget` | 否 | 适用对象 | `student` |
| `ageMin` | 否 | 最小年龄 | `18` |
| `ageMax` | 否 | 最大年龄 | `25` |
| `gender` | 否 | 性别分层 | `FEMALE` |
| `orgType` | 否 | 组织类型分层 | `school` |
| `meanScore` | 否 | 均值 | `10` |
| `stdDeviation` | 否 | 标准差 | `3` |
| `tScoreMean` | 否 | T 分均值 | `50` |
| `tScoreStdDeviation` | 否 | T 分标准差 | `10` |
| `sortNo` | 否 | 排序号 | `1` |

### 4.7 `high_risk_rules`

当前状态：
- 高危题规则工作表已启用，导入确认阶段会写入业务表并触发预警逻辑

| 字段名 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- |
| `ruleCode` | 是 | 规则编码 | `SELF_HARM_1` |
| `questionNo` | 是 | 关联题号 | `9` |
| `optionCode` | 否 | 命中的选项编码 | `D` |
| `scoreThreshold` | 否 | 数值阈值，适用于滑杆或分值型题 | `8` |
| `warningLevel` | 是 | 预警等级 | `HIGH` |
| `resultTitle` | 否 | 规则标题 | `高危题项命中` |
| `resultDescription` | 否 | 规则说明 | `存在明显高危信号，需要优先跟进` |
| `suggestionText` | 否 | 干预建议 | `立即通知咨询师进行人工复核` |
| `sortNo` | 否 | 排序号 | `1` |

## 5. 填写规则

### 5.1 编码规则

- `scaleCode` 必须全局唯一
- `dimensionCode` 在同一量表内唯一
- `questionNo` 在同一量表内唯一
- `optionCode` 在同一题目内唯一

### 5.2 关联规则

- `questions.dimensionCode` 必须能在 `dimensions` 中找到
- `options.questionNo` 必须能在 `questions` 中找到
- `result_rules.dimensionCode` 如非空，必须能在 `dimensions` 中找到

### 5.3 结果区间规则

- `scoreMin` 不能大于 `scoreMax`
- 同一作用域下区间不能重叠
  - 作用域一：总分规则
  - 作用域二：同一维度下的规则

## 6. 建议填写顺序

1. 先填 `scale`
2. 再填 `dimensions`
3. 再填 `questions`
4. 然后填 `options`
5. 最后填 `result_rules`
6. 如需前置设计未来能力，再补 `norms`
7. 最后补 `high_risk_rules`

## 7. 常见错误

- `scale` sheet 填了多行
- `questionType` 填写不属于已启用题型
- 某道题少于两个选项
- `dimensionCode` 拼写不一致
- 结果规则区间重叠
- `scaleCode` 与系统已有量表重复

## 8. 复杂题型与复杂评分说明

- 当前系统下载模板已同步复杂题型、常模换算和高危题规则相关列位
- 这些列位已经可导入生效，但仍建议先在预发环境验证评分与预警效果

## 9. 导入建议

- 先用 `scale-import-sample.xlsx` 理解结构
- 再复制为自己的导入文件
- 正式导入前，先走“上传解析 + 预览确认”
- 不要直接在样例文件上混填多个量表
