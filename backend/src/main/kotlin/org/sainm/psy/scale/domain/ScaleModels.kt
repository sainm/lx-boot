package org.sainm.psy.scale.domain

import org.sainm.psy.visualization.domain.ScaleVisualizationConfig
import java.math.BigDecimal
import java.time.LocalDateTime

data class ScaleSummary(
    val id: Long,
    val scaleCode: String,
    val scaleName: String,
    val applicableTarget: String?,
    val versionNo: String?,
    val versionGroupId: Long?,
    val currentVersionFlag: Boolean,
    val status: String,
    val scoreMethod: String,
    val scoreCoefficient: BigDecimal,
    val normStrategy: String,
    val normDefaultGroup: String?,
    val highRiskWarningEnabled: Boolean,
    val anonymousSupported: Boolean,
    val createdAt: LocalDateTime
)

data class ScaleDimension(
    val id: Long,
    val scaleId: Long,
    val dimensionCode: String,
    val dimensionName: String,
    val description: String?,
    val sortNo: Int
)

data class ScaleQuestionOption(
    val id: Long,
    val questionId: Long,
    val optionCode: String,
    val optionLabel: String,
    val scoreValue: BigDecimal,
    val exclusiveFlag: Boolean,
    val optionGroupCode: String?,
    val sortNo: Int
)

data class ScaleQuestion(
    val id: Long,
    val scaleId: Long,
    val dimensionId: Long?,
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val requiredFlag: Boolean,
    val reverseScoreFlag: Boolean,
    val weightValue: BigDecimal,
    val optionSelectionLimit: Int?,
    val sliderMin: BigDecimal?,
    val sliderMax: BigDecimal?,
    val sliderStep: BigDecimal?,
    val textInputEnabled: Boolean,
    val textInputPlaceholder: String?,
    val matrixGroupCode: String?,
    val rowCode: String?,
    val columnCode: String?,
    val sortNo: Int,
    val options: List<ScaleQuestionOption>
)

data class ScaleResultRule(
    val id: Long,
    val scaleId: Long,
    val dimensionId: Long?,
    val riskLevel: String,
    val scoreMin: BigDecimal,
    val scoreMax: BigDecimal,
    val scoreSource: String,
    val normCode: String?,
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?
)

data class ScaleNorm(
    val id: Long,
    val scaleId: Long,
    val normCode: String,
    val normName: String?,
    val dimensionId: Long?,
    val applicableTarget: String?,
    val ageMin: Int?,
    val ageMax: Int?,
    val gender: String?,
    val orgType: String?,
    val meanScore: BigDecimal?,
    val stdDeviation: BigDecimal?,
    val tScoreMean: BigDecimal?,
    val tScoreStdDeviation: BigDecimal?,
    val sortNo: Int
)

data class ScaleNormCoverageItem(
    val dimensionId: Long?,
    val dimensionCode: String,
    val dimensionName: String,
    val normCount: Int,
    val hasGlobalNorm: Boolean,
    val missingOverallNorm: Boolean
)

data class ScaleNormCoverage(
    val scaleId: Long,
    val normStrategy: String,
    val defaultNormGroup: String?,
    val totalNormCount: Int,
    val coveredDimensionCount: Int,
    val uncoveredDimensionCount: Int,
    val items: List<ScaleNormCoverageItem>
)

data class ScaleDetail(
    val id: Long,
    val scaleCode: String,
    val scaleName: String,
    val description: String?,
    val applicableTarget: String?,
    val versionNo: String?,
    val versionGroupId: Long?,
    val currentVersionFlag: Boolean,
    val status: String,
    val scoreMethod: String,
    val scoreCoefficient: BigDecimal,
    val normStrategy: String,
    val normDefaultGroup: String?,
    val highRiskWarningEnabled: Boolean,
    val anonymousSupported: Boolean,
    val reportTemplate: String?,
    val createdBy: Long?,
    val createdAt: LocalDateTime,
    val updatedBy: Long?,
    val updatedAt: LocalDateTime,
    val dimensions: List<ScaleDimension>,
    val questions: List<ScaleQuestion>,
    val resultRules: List<ScaleResultRule>,
    val norms: List<ScaleNorm>,
    val visualizationConfigs: List<ScaleVisualizationConfig> = emptyList(),
    val tenantId: Long? = null
)

data class ScaleVersionRef(
    val id: Long,
    val versionGroupId: Long?,
    val versionNo: String?,
    val scaleName: String,
    val status: String,
    val currentVersionFlag: Boolean
)

data class ScaleVersionDiffSummary(
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int
)

data class ScaleVersionDiffChange(
    val section: String,
    val key: String,
    val changeType: String,
    val before: Map<String, String?>? = null,
    val after: Map<String, String?>? = null
)

data class ScaleVersionDiff(
    val from: ScaleVersionRef,
    val to: ScaleVersionRef,
    val summary: ScaleVersionDiffSummary,
    val changes: List<ScaleVersionDiffChange>
)
