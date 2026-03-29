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
    val sortNo: Int,
    val options: List<ScaleQuestionOptionDraft>
)

data class ScaleResultRuleDraft(
    val dimensionId: Long?,
    val riskLevel: String,
    val scoreMin: BigDecimal,
    val scoreMax: BigDecimal,
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?
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

