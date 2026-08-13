package org.sainm.psy.report.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import org.sainm.psy.visualization.domain.ReportVisualization
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReportDetail(
    val reportId: Long,
    val resultId: Long,
    val scaleId: Long? = null,
    val scaleCode: String? = null,
    val scaleVersionNo: String? = null,
    @get:JsonIgnore
    val userId: Long?,
    @get:JsonIgnore
    val answerSheetId: Long? = null,
    val username: String? = null,
    val displayName: String? = null,
    val scaleName: String? = null,
    /**
     * A controlled presentation code carried by the scale version.  The
     * report renderer treats unknown/legacy values as the default screening
     * layout; arbitrary templates are never executed as code.
     */
    val reportTemplate: String? = null,
    val createdAt: LocalDateTime? = null,
    val reportType: String,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val content: String,
    /** Scale-specific sections extracted from the immutable report snapshot. */
    val resultDescription: String? = null,
    val suggestionText: String? = null,
    val localeCode: String? = null,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false,
    val highRiskRuleCode: String? = null,
    val calculationVersion: Int? = null,
    val scaleContentHash: String? = null,
    val scoringEngineVersion: String? = null,
    /** Internal audit JSON; derived metrics are exposed through [metrics], not this field. */
    @get:JsonIgnore
    val scoringTraceJson: String? = null,
    val metrics: List<ReportMetric> = emptyList(),
    val dimensionResults: List<ReportDimensionResult> = emptyList(),
    val answerDetails: List<ReportAnswerDetail> = emptyList(),
    val visualizations: List<ReportVisualization> = emptyList(),
    @get:JsonIgnore
    val tenantId: Long? = null
)

data class ReportMetric(
    val code: String,
    val rawValue: BigDecimal,
    val displayValue: String,
    val referenceText: String? = null,
    val interpretationCode: String = "REFERENCE_NOT_CONFIGURED",
    val reviewStatus: String = "PENDING_PROFESSIONAL_REVIEW"
)

data class ReportDimensionResult(
    val dimensionCode: String,
    val dimensionName: String,
    val score: BigDecimal,
    val riskLevel: String? = null,
    val resultTitle: String? = null,
    val referenceText: String? = null,
    val reviewStatus: String = "PENDING_PROFESSIONAL_REVIEW"
)

data class ReportAnswerDetail(
    val questionId: Long,
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val dimensionCode: String? = null,
    val dimensionName: String? = null,
    val optionCode: String? = null,
    val optionLabel: String? = null,
    val answerText: String? = null,
    val answerValue: BigDecimal? = null,
    val scoreValue: BigDecimal? = null,
    val sliderMin: BigDecimal? = null,
    val sliderMax: BigDecimal? = null,
    val sliderStep: BigDecimal? = null,
    val matrixGroupCode: String? = null,
    val rowCode: String? = null,
    val columnCode: String? = null
)

data class MyReportSummary(
    val reportId: Long,
    val resultId: Long,
    val taskId: Long,
    val taskName: String,
    val scaleName: String,
    val reportType: String,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val createdAt: LocalDateTime,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false
)

data class ReportSearchQuery(
    val userId: Long? = null,
    val groupId: Long? = null,
    val scaleId: Long? = null,
    val taskId: Long? = null,
    val page: Int = 1,
    val size: Int = 20
)

data class StaffReportSummary(
    val reportId: Long,
    val resultId: Long,
    val userId: Long,
    val username: String,
    val displayName: String?,
    val groupId: Long?,
    val groupName: String?,
    val taskId: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val reportType: String,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val createdAt: LocalDateTime,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false
)
