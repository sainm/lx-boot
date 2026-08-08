# 量表计分设计文档

## 1. 设计目标

支持主流心理测评量表（PHQ-9、GAD-7、SCL-90、SAS、SDS 等）的计分逻辑，满足：

- 有维度的量表：输出总分 + 各维度分，分别匹配对应风险规则
- 无维度的量表：只输出总分，匹配全局风险规则
- 部分题目需要反向计分（如 SAS 第 5、9、13、17、19 题）
- 部分量表需要对粗分乘以系数换算为标准分（如 SAS/SDS 粗分 × 1.25）

---

## 2. 量表级配置字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `score_method` | varchar(32) | 计分算法，见下表 |
| `score_coefficient` | decimal(6,4) | 粗分换算系数，默认 1.0 |

### score_method 取值

| 值 | 说明 | 适用量表示例 |
|---|---|---|
| `SIMPLE_SUM` | 各题选项分值直接求和 | PHQ-9、GAD-7 |
| `REVERSE_SUM` | 标记了反向计分的题做翻转（min + max - 原分），再求和 | SAS、SDS |
| `WEIGHTED_SUM` | 各题有效分值 × weight_value 后求和 | 自定义权重量表 |
| `AVERAGE` | 题目有效分值求平均 | SCL-90 总均分、部分症状量表 |
| `WEIGHTED_AVERAGE` | 加权总分除以权重总和 | 不等权重的平均分量表 |

### score_coefficient 说明

粗分 × score_coefficient = 最终总分（用于规则匹配和存储）。

| 量表 | score_coefficient |
|---|---|
| PHQ-9 / GAD-7 / SCL-90 | 1.0（不换算） |
| SAS / SDS | 1.25（粗分 × 1.25 = 标准分） |

---

## 3. 题目级配置字段

| 字段 | 说明 |
|---|---|
| `dimension_id` | 所属维度 ID，NULL 表示该题不属于任何维度 |
| `reverse_score_flag` | 是否反向计分；可与求和、平均、加权方法组合 |
| `weight_value` | 题目权重（WEIGHTED_SUM / WEIGHTED_AVERAGE），默认 1.0 |

---

## 4. 计分流程

```
提交答卷（submit）
│
├─ 1. 保存原始作答记录（option 的 score_value 入库）
│
├─ 2. 加载量表配置：score_method、score_coefficient
│
├─ 3. 加载题目元数据：dimension_id、reverse_score_flag、weight_value
│
├─ 4. 计算每题有效分（effectiveScore）
│     SIMPLE_SUM   → effectiveScore = score_value
│     REVERSE_SUM  → reverseScoreFlag=true 时：min + max - score_value
│                    reverseScoreFlag=false 时：score_value
│     WEIGHTED_SUM → effectiveScore = score_value × weight_value
│     AVERAGE → effectiveScore = 反向处理后的 score_value
│     WEIGHTED_AVERAGE → effectiveScore = 反向处理后的 score_value × weight_value
│
├─ 5. 计算总分
│     粗分 totalRaw = 所有题 effectiveScore 之和（含无维度题）
│     AVERAGE = totalRaw / 题目数
│     WEIGHTED_AVERAGE = totalRaw / 权重总和
│     最终总分 totalScore = totalRaw × score_coefficient
│
├─ 6. 计算维度分（有维度时）
│     dimensionScore = 该维度内所有题 effectiveScore 的 平均值
│     （无维度的题不参与任何维度分计算，但参与总分）
│
├─ 7. 风险匹配
│     总分  → 查询 result_rule（dimension_id IS NULL，totalScore BETWEEN score_min AND score_max）
│     维度分 → 查询 result_rule（dimension_id = X，dimScore BETWEEN score_min AND score_max）
│
└─ 8. 结果入库
      psy_assessment_result            ← total_score、risk_level、result_summary
      psy_assessment_result_dimension  ← 每个维度的 dimension_score、risk_level（无维度则为空）
```

---

## 5. 主流量表配置示例

### PHQ-9（患者健康问卷抑郁量表）

| 配置 | 值 |
|---|---|
| score_method | AVERAGE |
| score_coefficient | 1.0 |
| 维度 | 无 |
| 题目数 | 9 |
| 选项分值 | 0 / 1 / 2 / 3 |

result_rule 配置（全局，dimension_id = NULL）：

| score_min | score_max | risk_level | result_title |
|---|---|---|---|
| 0 | 4 | NORMAL | 无抑郁症状 |
| 5 | 9 | LOW | 轻度抑郁 |
| 10 | 14 | MODERATE | 中度抑郁 |
| 15 | 19 | HIGH | 中重度抑郁 |
| 20 | 27 | HIGH | 重度抑郁 |

---

### GAD-7（广泛性焦虑障碍量表）

| 配置 | 值 |
|---|---|
| score_method | SIMPLE_SUM |
| score_coefficient | 1.0 |
| 维度 | 无 |
| 题目数 | 7 |
| 选项分值 | 0 / 1 / 2 / 3 |

result_rule 配置（全局）：

| score_min | score_max | risk_level | result_title |
|---|---|---|---|
| 0 | 4 | NORMAL | 无焦虑症状 |
| 5 | 9 | LOW | 轻度焦虑 |
| 10 | 14 | MODERATE | 中度焦虑 |
| 15 | 21 | HIGH | 重度焦虑 |

---

### SAS（Zung 焦虑自评量表）

| 配置 | 值 |
|---|---|
| score_method | REVERSE_SUM |
| score_coefficient | 1.25 |
| 维度 | 无 |
| 题目数 | 20 |
| 选项分值 | 1 / 2 / 3 / 4 |
| 反向计分题 | 第 5、9、13、17、19 题设 reverse_score_flag = true |

result_rule 配置（基于标准分，即粗分 × 1.25）：

| score_min | score_max | risk_level | result_title |
|---|---|---|---|
| 0 | 49.99 | NORMAL | 正常 |
| 50 | 59.99 | LOW | 轻度焦虑 |
| 60 | 69.99 | MODERATE | 中度焦虑 |
| 70 | 100 | HIGH | 重度焦虑 |

---

### SCL-90（症状自评量表）

| 配置 | 值 |
|---|---|
| score_method | SIMPLE_SUM |
| score_coefficient | 1.0 |
| 维度 | 9 个因子（躯体化、强迫、人际敏感、抑郁、焦虑、敌对、恐怖、偏执、精神病性） |
| 题目数 | 90 |
| 选项分值 | 1 / 2 / 3 / 4 / 5 |

result_rule 配置：

全局规则（dimension_id = NULL，基于总均分 = totalScore / 90）：

> 注：SCL-90 采用 `AVERAGE` 后，`totalScore` 即总均分，可直接按均分范围配置全局规则。

| score_min | score_max | risk_level | result_title |
|---|---|---|---|
| 0 | 1.5 | NORMAL | 正常 |
| 1.5001 | 5.0 | MODERATE | 阳性（建议评估） |

各维度规则（dimension_id = 对应维度 ID，基于维度均分）：

| score_min | score_max | risk_level | result_title |
|---|---|---|---|
| 0 | 2.0 | NORMAL | 正常 |
| 2.0001 | 5.0 | MODERATE | 该因子阳性 |

---

## 6. 数据存储结构

```
psy_scale
  score_method        varchar(32)        计分方法
  score_coefficient   decimal(6,4)       换算系数

psy_scale_question
  reverse_score_flag  boolean            是否反向计分
  weight_value        decimal            题目权重（WEIGHTED_SUM 用）
  dimension_id        bigint nullable    所属维度（NULL=不属于任何维度）

psy_scale_result_rule
  dimension_id        bigint nullable    NULL=全局规则，非 NULL=维度规则
  score_min           decimal            得分区间下限（含）
  score_max           decimal            得分区间上限（含）
  risk_level          varchar(32)        NORMAL / LOW / MODERATE / HIGH

psy_assessment_result
  total_score         decimal            最终总分（已含 score_coefficient 换算）
  risk_level          varchar(32)        总体风险等级

psy_assessment_result_dimension
  result_id           bigint             关联 psy_assessment_result
  dimension_id        bigint             维度 ID
  dimension_score     decimal(10,4)      该维度平均分
  risk_level          varchar(32)        该维度风险等级
  result_title        varchar(255)       该维度结果标题
```

---

## 7. 风险等级说明

| 等级 | 含义 | 是否触发预警 |
|---|---|---|
| `NORMAL` | 正常 | 否 |
| `LOW` | 低风险 | 否 |
| `MODERATE` | 中风险 | 是 |
| `HIGH` | 高风险 | 是 |

> 总体 risk_level != NORMAL 时，系统自动生成 `psy_warning_record` 并推送通知。
