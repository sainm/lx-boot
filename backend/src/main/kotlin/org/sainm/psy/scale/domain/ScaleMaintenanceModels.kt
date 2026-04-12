package org.sainm.psy.scale.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class ScaleDimensionDraft(
    val dimensionCode: String,
    val dimensionName: String,
    val description: String?,
    val sortNo: Int
)

data class ScaleQuestionOptionDraft(
    val optionCode: String,
    val optionLabel: String,
    val scoreValue: BigDecimal,
    val exclusiveFlag: Boolean = false,
    val optionGroupCode: String? = null,
    val sortNo: Int
)

data class ScaleQuestionDraft(
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val dimensionId: Long?,
    val requiredFlag: Boolean,
    val reverseScoreFlag: Boolean,
    val weightValue: BigDecimal,
    val optionSelectionLimit: Int? = null,
    val sliderMin: BigDecimal? = null,
    val sliderMax: BigDecimal? = null,
    val sliderStep: BigDecimal? = null,
    val textInputEnabled: Boolean = false,
    val textInputPlaceholder: String? = null,
    val matrixGroupCode: String? = null,
    val rowCode: String? = null,
    val columnCode: String? = null,
    val sortNo: Int,
    val options: List<ScaleQuestionOptionDraft>
)

data class ScaleResultRuleDraft(
    val dimensionId: Long?,
    val riskLevel: String,
    val scoreMin: BigDecimal,
    val scoreMax: BigDecimal,
    val scoreSource: String = "RAW_SCORE",
    val normCode: String? = null,
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?
)

data class ScaleNormDraft(
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

data class ScaleHighRiskRuleDraft(
    val ruleCode: String,
    val questionId: Long,
    val optionId: Long?,
    val scoreThreshold: BigDecimal?,
    val warningLevel: String,
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?,
    val sortNo: Int
)

data class CreatedBatchIds(
    val createdIds: List<Long>
)

data class CreatedScaleDimension(
    val id: Long,
    val dimensionCode: String,
    val dimensionName: String,
    val sortNo: Int,
    val createdAt: LocalDateTime
)
