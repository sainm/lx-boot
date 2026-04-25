package org.sainm.psy.visualization.domain

import java.math.BigDecimal

data class ScaleVisualizationConfig(
    val id: Long,
    val scaleId: Long,
    val chartType: String,
    val chartTitle: String,
    val viewScope: String,
    val dataSource: String,
    val configJson: String,
    val enabled: Boolean,
    val sortNo: Int
)

data class ScaleVisualizationConfigDraft(
    val chartType: String,
    val chartTitle: String,
    val viewScope: String,
    val dataSource: String,
    val configJson: String = "{}",
    val enabled: Boolean = true,
    val sortNo: Int = 0
)

data class ReportVisualization(
    val configId: Long?,
    val chartType: String,
    val chartTitle: String,
    val viewScope: String,
    val dataSource: String,
    val configJson: String,
    val dataSets: List<ChartDataSet>
)

data class ChartDataSet(
    val name: String,
    val points: List<ChartPoint>
)

data class ChartPoint(
    val key: String,
    val label: String,
    val value: BigDecimal? = null,
    val series: String? = null,
    val group: String? = null
)
