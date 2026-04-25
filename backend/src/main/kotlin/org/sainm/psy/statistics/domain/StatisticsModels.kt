package org.sainm.psy.statistics.domain

import org.sainm.psy.visualization.domain.ReportVisualization
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class DashboardMetricCard(
    val key: String,
    val label: String,
    val value: BigDecimal,
    val suffix: String? = null,
    val description: String? = null
)

data class DashboardTrendPoint(
    val day: LocalDate,
    val count: Long
)

data class KeyValueCount(
    val key: String,
    val value: Long
)

data class DashboardRecentWarningItem(
    val warningId: Long,
    val resultId: Long,
    val taskName: String,
    val scaleName: String,
    val warningLevel: String,
    val warningPriority: String,
    val status: String,
    val totalScore: BigDecimal,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false,
    val highRiskRuleCode: String? = null,
    val createdAt: LocalDateTime
)

data class DashboardRecentReportItem(
    val reportId: Long,
    val resultId: Long,
    val taskName: String,
    val scaleName: String,
    val reportType: String,
    val riskLevel: String,
    val totalScore: BigDecimal,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false,
    val highRiskRuleCode: String? = null,
    val createdAt: LocalDateTime
)

data class DashboardStatisticsResponse(
    val generatedAt: LocalDateTime,
    val overviewCards: List<DashboardMetricCard>,
    val taskStatusDistribution: List<KeyValueCount>,
    val riskDistribution: List<KeyValueCount>,
    val submissionTrend: List<DashboardTrendPoint>,
    val warningTrend: List<DashboardTrendPoint>,
    val recentWarnings: List<DashboardRecentWarningItem>,
    val recentReports: List<DashboardRecentReportItem>
)

data class GroupDimensionStat(
    val dimensionId: Long?,
    val dimensionName: String,
    val averageScore: BigDecimal,
    val answerCount: Long
)

data class GroupUserComparison(
    val userId: Long,
    val displayName: String?,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false,
    val highRiskRuleCode: String? = null,
    val scoreGapToAverage: BigDecimal? = null
)

data class GroupReportSummary(
    val taskId: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val groupId: Long,
    val groupName: String,
    val memberCount: Long,
    val submittedCount: Long,
    val completionRate: BigDecimal,
    val averageScore: BigDecimal?,
    val highRiskCount: Long,
    val warningCount: Long,
    val riskDistribution: List<KeyValueCount>,
    val latestSubmittedAt: LocalDateTime?,
    val compareUserResult: GroupUserComparison? = null,
    val dimensionStats: List<GroupDimensionStat> = emptyList(),
    val visualizations: List<ReportVisualization> = emptyList()
)
