# 量表导入设计

> 说明：本文件描述的是“量表导入”能力的目标设计，用于后续实现与联调，不代表当前仓库已经完成该功能。

## 1. 设计目标

量表导入功能用于降低量表初始化与维护成本，使测评管理员可以基于标准模板一次性导入：

- 量表基本信息
- 维度
- 题目
- 选项
- 结果规则

第一版设计目标：

- 只支持单个量表导入
- 只支持 `xlsx` 模板
- 只支持新增模式
- 支持解析预览
- 支持人工确认后正式导入
- 支持导入历史和错误报告

不在第一版范围内：

- 多量表打包导入
- 覆盖式更新已有量表
- 增量合并已有题目
- 异步超大文件导入

## 2. 用户与权限

角色：

- `ASSESSMENT_ADMIN`
- `SYS_ADMIN`

权限要求：

- 只有具备量表维护权限的后台角色可以下载模板、上传文件、查看导入结果、确认导入
- 导入操作应进入安全审计

## 3. 功能流程

建议流程：

1. 下载模板
2. 填写模板
3. 上传文件并解析
4. 结构校验
5. 业务校验
6. 展示预览与错误列表
7. 用户确认导入
8. 事务性落库
9. 返回导入结果
10. 进入导入历史可追踪

其中第 7 步和第 8 步必须分开，避免上传即入库。

## 4. 模板设计

第一版建议使用一个 `xlsx` 文件，包含 5 个 sheet：

1. `scale`
2. `dimensions`
3. `questions`
4. `options`
5. `result_rules`

### 4.1 scale

每次导入只允许一行：

| 列名 | 必填 | 说明 |
| --- | --- | --- |
| `scaleCode` | 是 | 量表编码，全局唯一 |
| `scaleName` | 是 | 量表名称 |
| `description` | 否 | 量表简介 |
| `applicableTarget` | 否 | 适用对象 |
| `versionNo` | 否 | 版本号 |
| `scoreMethod` | 是 | `SIMPLE_SUM` / `REVERSE_SUM` / `WEIGHTED_SUM` |
| `scoreCoefficient` | 否 | 默认 `1.0` |
| `anonymousSupported` | 否 | `true` / `false` |
| `reportTemplate` | 否 | 默认报告模板说明 |

### 4.2 dimensions

| 列名 | 必填 | 说明 |
| --- | --- | --- |
| `dimensionCode` | 是 | 维度编码，量表内唯一 |
| `dimensionName` | 是 | 维度名称 |
| `description` | 否 | 维度说明 |
| `sortNo` | 否 | 排序，默认按行号 |

### 4.3 questions

| 列名 | 必填 | 说明 |
| --- | --- | --- |
| `questionNo` | 是 | 题号，量表内唯一 |
| `questionTitle` | 是 | 题干 |
| `questionType` | 是 | 第一版建议固定 `SINGLE_CHOICE` |
| `dimensionCode` | 否 | 所属维度编码 |
| `requiredFlag` | 否 | `true` / `false` |
| `reverseScoreFlag` | 否 | `true` / `false` |
| `weightValue` | 否 | 默认 `1.0` |
| `sortNo` | 否 | 排序 |

### 4.4 options

| 列名 | 必填 | 说明 |
| --- | --- | --- |
| `questionNo` | 是 | 关联题号 |
| `optionCode` | 是 | 选项编码，题目内唯一 |
| `optionLabel` | 是 | 选项内容 |
| `scoreValue` | 是 | 选项分值 |
| `sortNo` | 否 | 排序 |

### 4.5 result_rules

| 列名 | 必填 | 说明 |
| --- | --- | --- |
| `dimensionCode` | 否 | 为空表示总分规则 |
| `riskLevel` | 是 | 风险等级 |
| `scoreMin` | 是 | 最小分 |
| `scoreMax` | 是 | 最大分 |
| `resultTitle` | 否 | 结果标题 |
| `resultDescription` | 否 | 结果说明 |
| `suggestionText` | 否 | 建议内容 |
| `sortNo` | 否 | 排序 |

## 5. 校验规则

校验分两层。

### 5.1 结构校验

- 必须包含全部必需 sheet
- 必填列不能缺失
- `scale` sheet 只能有一行有效数据
- 数值列必须可解析
- 布尔列必须是合法布尔值

### 5.2 业务校验

- `scaleCode` 不能与现有量表重复
- 同一量表内 `dimensionCode` 不能重复
- 同一量表内 `questionNo` 不能重复
- 同一题目内 `optionCode` 不能重复
- `questions.dimensionCode` 必须在 `dimensions` 中存在
- `options.questionNo` 必须在 `questions` 中存在
- `result_rules.dimensionCode` 如非空，必须在 `dimensions` 中存在
- `scoreMin` 不能大于 `scoreMax`
- 同一作用域内结果规则区间不能重叠
- 第一版只允许单选题
- 每道题至少有两个选项

## 6. 错误与警告

建议返回统一明细：

| 字段 | 说明 |
| --- | --- |
| `sheetName` | 所在 sheet |
| `rowNo` | 行号 |
| `columnName` | 列名 |
| `errorCode` | 错误码 |
| `message` | 用户可读错误信息 |
| `severity` | `ERROR` / `WARNING` |

建议策略：

- 有 `ERROR` 时禁止确认导入
- 只有 `WARNING` 时允许确认导入

## 7. 页面设计

量表管理页建议新增“导入量表”入口。

### 7.1 页面模块

- 模板下载按钮
- 文件上传区
- 导入模式说明
- 解析结果摘要卡片
- 错误明细表
- 警告明细表
- 预览确认按钮
- 导入历史列表

### 7.2 关键交互

- 上传完成后不直接入库，只先解析
- 解析成功后展示统计摘要
- 如果有错误，突出显示错误行与错误原因
- 用户确认时二次提醒“将正式创建量表”
- 导入成功后跳转量表详情页或历史记录页

## 8. 接口设计

建议接口：

- `GET /api/v1/scales/import-template`
- `POST /api/v1/scales/imports/parse`
- `POST /api/v1/scales/imports/{id}/confirm`
- `GET /api/v1/scales/imports/{id}`
- `GET /api/v1/scales/imports`

## 9. 数据库设计建议

为支持预览、确认、历史与错误追踪，建议增加两张表。

### 9.1 导入主表 `psy_scale_import_job`

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `file_name` | varchar(255) | 原始文件名 |
| `file_hash` | varchar(128) | 文件摘要，可选 |
| `import_mode` | varchar(32) | `CREATE_ONLY` |
| `draft_flag` | boolean | 是否草稿导入 |
| `status` | varchar(32) | `UPLOADED` / `PARSED` / `PARSE_FAILED` / `CONFIRMED` / `SUCCESS` / `FAILED` |
| `summary_json` | text | 解析摘要 |
| `preview_json` | text | 预览结构化结果 |
| `error_count` | int | 错误数 |
| `warning_count` | int | 警告数 |
| `created_scale_id` | bigint | 导入成功后的量表 ID |
| `operator_user_id` | bigint | 操作人 |
| `parsed_at` | timestamp | 解析时间 |
| `confirmed_at` | timestamp | 确认时间 |
| `finished_at` | timestamp | 完成时间 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### 9.2 导入错误表 `psy_scale_import_issue`

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `import_job_id` | bigint | 关联导入主表 |
| `severity` | varchar(16) | `ERROR` / `WARNING` |
| `sheet_name` | varchar(64) | sheet 名 |
| `row_no` | int | 行号 |
| `column_name` | varchar(64) | 列名 |
| `error_code` | varchar(64) | 错误码 |
| `message` | varchar(500) | 错误信息 |
| `created_at` | timestamp | 创建时间 |

## 10. 后端实现建议

建议新增模块职责：

- `ScaleImportController`
- `ScaleImportService`
- `ScaleImportParser`
- `ScaleImportValidator`
- `ScaleImportRepository`

建议流程：

1. Controller 接收文件
2. Parser 转成结构化预览对象
3. Validator 生成错误与警告
4. 结果写入 `psy_scale_import_job` 与 `psy_scale_import_issue`
5. 用户确认后再调用现有 `ScaleService` 或仓储逻辑正式创建量表
6. 正式导入必须放在事务中

## 11. 审计与安全

- 导入模板下载可不审计
- 上传解析与确认导入建议写入安全审计
- 导入失败的错误信息不能暴露内部堆栈
- 上传文件应限制大小与扩展名
- 第一版仅接受 `xlsx`

## 12. 测试建议

至少覆盖：

- 正常导入
- 模板缺少 sheet
- 必填列缺失
- 量表编码重复
- 维度编码重复
- 题号重复
- 结果规则区间冲突
- 引用不存在的 `dimensionCode`
- 引用不存在的 `questionNo`
- 只有 warning 的导入
- 有 error 的禁止确认
- 确认导入后的事务回滚

## 13. 第一版交付边界

第一版建议只做到：

- 下载模板
- 上传解析
- 错误预览
- 人工确认
- 新增量表
- 导入历史

等第一版稳定后，再考虑：

- 批量量表导入
- 覆盖更新
- 增量合并
- 导入版本比对
- 异步大文件处理
