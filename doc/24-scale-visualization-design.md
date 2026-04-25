# 量表可视化配置化设计

## 目标

量表报告图表不再由页面按量表编码硬编码，而由量表版本维护自己的图表配置。报告详情、群体报告读取后端返回的 `visualizations`，前端统一用 ECharts 渲染。

## 数据模型

新增表 `psy_scale_visualization_config`：

| 字段 | 说明 |
| --- | --- |
| `scale_id` | 所属量表版本 |
| `chart_type` | 图表类型，如 `RADAR`、`DIMENSION_BAR` |
| `chart_title` | 展示标题 |
| `view_scope` | `REPORT_DETAIL` 或 `GROUP_REPORT` |
| `data_source` | 数据来源，如 `DIMENSION_SCORE`、`RISK_DISTRIBUTION` |
| `config_json` | 前端渲染配置，JSONB |
| `enabled` | 是否启用 |
| `sort_no` | 展示顺序 |

配置跟随量表版本。创建新版本时复制旧版本配置，历史报告按其量表版本对应配置展示。

## 接口约定

量表详情 `GET /api/v1/scales/{id}` 增加：

```json
{
  "visualizationConfigs": [
    {
      "id": 1,
      "scaleId": 10,
      "chartType": "RADAR",
      "chartTitle": "维度画像",
      "viewScope": "REPORT_DETAIL",
      "dataSource": "DIMENSION_SCORE",
      "configJson": "{\"maxStrategy\":\"auto\"}",
      "enabled": true,
      "sortNo": 1
    }
  ]
}
```

报告详情 `GET /api/v1/reports/{id}` 和 `GET /api/v1/reports/by-result/{resultId}` 增加：

```json
{
  "visualizations": [
    {
      "chartType": "RADAR",
      "chartTitle": "维度画像",
      "viewScope": "REPORT_DETAIL",
      "dataSource": "DIMENSION_SCORE",
      "configJson": "{}",
      "dataSets": [
        {
          "name": "dimensionScores",
          "points": [
            { "key": "DEP", "label": "抑郁", "value": 3.2 }
          ]
        }
      ]
    }
  ]
}
```

群体报告 `GET /api/v1/statistics/group-reports` 在每条 `GroupReportSummary` 增加同结构 `visualizations`。

## 支持图表

单人报告：

- `RADAR`: 维度雷达图，数据源 `DIMENSION_SCORE`
- `DIMENSION_BAR`: 维度条形图，数据源 `DIMENSION_SCORE`
- `ANSWER_SCORE_DISTRIBUTION`: 作答分值分布，数据源 `ANSWER_SCORE_DISTRIBUTION`
- `NORM_COMPARE`: 用户分数与常模均值对比，数据源 `NORM_COMPARE`
- `RISK_CUE`: 风险提示，数据源 `RISK_DISTRIBUTION`

群体报告：

- `GROUP_COMPLETION_BAR`: 群组完成率，数据源 `COMPLETION_RATE`
- `GROUP_RISK_STACK`: 群体风险结构，数据源 `RISK_DISTRIBUTION`
- `GROUP_DIMENSION_HEATMAP`: 组维度画像，数据源 `DIMENSION_SCORE`
- `GROUP_SCORE_RANKING`: 群体得分排行，数据源 `GROUP_SCORE_RANKING`

## SCL-90 默认配置

`data-psy.sql` 为 SCL-90 初始化报告详情和群体报告配置：

- 报告详情：雷达图、维度条形图、作答分布、常模对比、风险提示
- 群体报告：完成率、风险结构、组维度画像、得分排行

## 验收标准

- 没有配置的量表报告正常打开，只显示空图表状态。
- SCL-90 报告详情能看到维度、作答分布、风险和常模相关图表。
- SCL-90 群体报告每条记录能返回可视化数据。
- 量表草稿版本可在量表详情中维护图表配置。
- `npm run build` 和后端测试通过。
