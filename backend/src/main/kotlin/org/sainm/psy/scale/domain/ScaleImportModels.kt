package org.sainm.psy.scale.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class ScaleImportPreview(
    val scale: ScaleImportScalePreview,
    val dimensions: List<ScaleImportDimensionPreview>,
    val questions: List<ScaleImportQuestionPreview>,
    val resultRules: List<ScaleImportResultRulePreview>,
    val norms: List<ScaleImportNormPreview> = emptyList(),
    val highRiskRules: List<ScaleImportHighRiskRulePreview> = emptyList()
)

data class ScaleImportScalePreview(
    val scaleCode: String,
    val scaleName: String,
    val description: String? = null,
    val applicableTarget: String? = null,
    val versionNo: String? = null,
    val scoreMethod: String = "SIMPLE_SUM",
    val scoreCoefficient: BigDecimal = BigDecimal.ONE,
    val normStrategy: String = "RAW_SCORE",
    val normDefaultGroup: String? = null,
    val highRiskWarningEnabled: Boolean = false,
    val anonymousSupported: Boolean = false,
    val reportTemplate: String? = null
)

data class ScaleImportDimensionPreview(
    val dimensionCode: String,
    val dimensionName: String,
    val description: String? = null,
    val sortNo: Int = 0
)

data class ScaleImportQuestionPreview(
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val dimensionCode: String? = null,
    val requiredFlag: Boolean = true,
    val reverseScoreFlag: Boolean = false,
    val weightValue: BigDecimal = BigDecimal.ONE,
    val optionSelectionLimit: Int? = null,
    val sliderMin: BigDecimal? = null,
    val sliderMax: BigDecimal? = null,
    val sliderStep: BigDecimal? = null,
    val textInputEnabled: Boolean = false,
    val textInputPlaceholder: String? = null,
    val matrixGroupCode: String? = null,
    val rowCode: String? = null,
    val columnCode: String? = null,
    val sortNo: Int = 0,
    val options: List<ScaleImportOptionPreview>
)

data class ScaleImportOptionPreview(
    val optionCode: String,
    val optionLabel: String,
    val scoreValue: BigDecimal,
    val exclusiveFlag: Boolean = false,
    val optionGroupCode: String? = null,
    val sortNo: Int = 0
)

data class ScaleImportResultRulePreview(
    val dimensionCode: String? = null,
    val riskLevel: String,
    val scoreMin: BigDecimal,
    val scoreMax: BigDecimal,
    val scoreSource: String = "RAW_SCORE",
    val normCode: String? = null,
    val resultTitle: String? = null,
    val resultDescription: String? = null,
    val suggestionText: String? = null,
    val sortNo: Int = 0
)

data class ScaleImportNormPreview(
    val normCode: String,
    val normName: String? = null,
    val dimensionCode: String? = null,
    val applicableTarget: String? = null,
    val ageMin: Int? = null,
    val ageMax: Int? = null,
    val gender: String? = null,
    val orgType: String? = null,
    val meanScore: BigDecimal? = null,
    val stdDeviation: BigDecimal? = null,
    val tScoreMean: BigDecimal? = null,
    val tScoreStdDeviation: BigDecimal? = null,
    val sortNo: Int = 0
)

data class ScaleImportHighRiskRulePreview(
    val ruleCode: String,
    val questionNo: Int,
    val optionCode: String? = null,
    val scoreThreshold: BigDecimal? = null,
    val warningLevel: String,
    val resultTitle: String? = null,
    val resultDescription: String? = null,
    val suggestionText: String? = null,
    val sortNo: Int = 0
)

data class ScaleImportIssue(
    val severity: String,
    val sheetName: String,
    val rowNo: Int?,
    val columnName: String?,
    val errorCode: String,
    val message: String
)

data class ScaleImportSummary(
    val scaleCode: String? = null,
    val scaleName: String? = null,
    val dimensionCount: Int = 0,
    val questionCount: Int = 0,
    val optionCount: Int = 0,
    val resultRuleCount: Int = 0
)

data class ScaleImportJobRecord(
    val id: Long,
    val tenantId: Long?,
    val fileName: String,
    val importMode: String,
    val draftFlag: Boolean,
    val status: String,
    val summaryJson: String?,
    val previewJson: String?,
    val errorCount: Int,
    val warningCount: Int,
    val createdScaleId: Long?,
    val operatorUserId: Long,
    val parsedAt: LocalDateTime?,
    val confirmedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
