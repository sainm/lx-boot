package org.sainm.psy.visualization.service

import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.visualization.domain.ChartDataSet
import org.sainm.psy.visualization.domain.ChartPoint
import org.sainm.psy.visualization.domain.ReportVisualization
import org.sainm.psy.visualization.domain.ScaleVisualizationConfigDraft
import org.sainm.psy.visualization.repository.VisualizationRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class VisualizationService(
    private val visualizationRepository: VisualizationRepository
) {

    fun findConfigs(scaleId: Long) =
        if (visualizationRepository.hasTable()) visualizationRepository.findConfigs(scaleId) else emptyList()

    fun replaceConfigs(scaleId: Long, drafts: List<ScaleVisualizationConfigDraft>) {
        visualizationRepository.replaceConfigs(scaleId, drafts)
    }

    fun copyConfigs(sourceScaleId: Long, newScaleId: Long) {
        if (visualizationRepository.hasTable()) {
            visualizationRepository.copyConfigs(sourceScaleId, newScaleId)
        }
    }

    fun buildReportVisualizations(detail: ReportDetail): List<ReportVisualization> {
        val scaleId = detail.scaleId ?: return emptyList()
        if (!visualizationRepository.hasTable()) return emptyList()
        val configs = visualizationRepository.findConfigs(scaleId, "REPORT_DETAIL", enabledOnly = true)
        if (configs.isEmpty()) return emptyList()
        return configs.mapNotNull { config ->
            val dataSets = when (config.dataSource) {
                "DIMENSION_SCORE" -> listOf(ChartDataSet("dimensionScores", visualizationRepository.findReportDimensionPoints(detail.resultId)))
                "ANSWER_SCORE_DISTRIBUTION" -> listOf(ChartDataSet("answerScoreDistribution", answerScoreDistribution(detail)))
                "RISK_DISTRIBUTION" -> listOf(ChartDataSet("riskCue", riskCue(detail)))
                "NORM_COMPARE" -> listOf(ChartDataSet("normCompare", visualizationRepository.findNormComparePoints(detail.resultId, scaleId)))
                else -> emptyList()
            }
            config.toVisualization(dataSets).takeIf { dataSets.any { set -> set.points.isNotEmpty() } }
        }
    }

    fun buildGroupVisualizations(summary: GroupReportSummary): List<ReportVisualization> {
        if (!visualizationRepository.hasTable()) return emptyList()
        val configs = visualizationRepository.findConfigs(summary.scaleId, "GROUP_REPORT", enabledOnly = true)
        if (configs.isEmpty()) return emptyList()
        return configs.mapNotNull { config ->
            val dataSets = when (config.dataSource) {
                "COMPLETION_RATE" -> listOf(
                    ChartDataSet(
                        "completionRate",
                        listOf(
                            ChartPoint(
                                key = summary.groupId.toString(),
                                label = summary.groupName,
                                value = summary.completionRate,
                                series = "COMPLETION"
                            )
                        )
                    )
                )
                "RISK_DISTRIBUTION" -> listOf(
                    ChartDataSet(
                        "riskDistribution",
                        summary.riskDistribution.map { item ->
                            ChartPoint(item.key, item.key, BigDecimal.valueOf(item.value), series = item.key, group = summary.groupName)
                        }
                    )
                )
                "DIMENSION_SCORE" -> listOf(
                    ChartDataSet(
                        "dimensionScores",
                        summary.dimensionStats.map { dimension ->
                            ChartPoint(
                                key = (dimension.dimensionId ?: dimension.dimensionName).toString(),
                                label = dimension.dimensionName,
                                value = dimension.averageScore,
                                group = summary.groupName
                            )
                        }
                    )
                )
                "GROUP_SCORE_RANKING" -> listOf(
                    ChartDataSet(
                        "scoreRanking",
                        listOfNotNull(
                            summary.averageScore?.let {
                                ChartPoint(summary.groupId.toString(), summary.groupName, it, series = "AVERAGE_SCORE")
                            },
                            ChartPoint("${summary.groupId}:HIGH", summary.groupName, BigDecimal.valueOf(summary.highRiskCount), series = "HIGH_RISK_COUNT")
                        )
                    )
                )
                else -> emptyList()
            }
            config.toVisualization(dataSets).takeIf { dataSets.any { set -> set.points.isNotEmpty() } }
        }
    }

    private fun answerScoreDistribution(detail: ReportDetail): List<ChartPoint> =
        detail.answerDetails
            .mapNotNull { it.scoreValue }
            .groupBy { it.stripTrailingZeros().toPlainString() }
            .toSortedMap(compareBy { it.toBigDecimalOrNull() ?: BigDecimal.ZERO })
            .map { (score, values) ->
                ChartPoint(key = score, label = score, value = BigDecimal.valueOf(values.size.toLong()))
            }

    private fun riskCue(detail: ReportDetail): List<ChartPoint> = buildList {
        add(ChartPoint(key = detail.riskLevel, label = detail.riskLevel, value = BigDecimal.ONE, series = "RISK_LEVEL"))
        if (detail.highRiskFlag) {
            add(ChartPoint(key = "HIGH_RISK_ITEM", label = detail.highRiskRuleCode ?: "HIGH_RISK_ITEM", value = BigDecimal.ONE, series = "HIGH_RISK"))
        }
    }

    private fun org.sainm.psy.visualization.domain.ScaleVisualizationConfig.toVisualization(dataSets: List<ChartDataSet>) =
        ReportVisualization(
            configId = id,
            chartType = chartType,
            chartTitle = chartTitle,
            viewScope = viewScope,
            dataSource = dataSource,
            configJson = configJson,
            dataSets = dataSets
        )
}
