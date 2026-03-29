package org.sainm.psy.scale.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class ScaleSummary(
    val id: Long,
    val scaleCode: String,
    val scaleName: String,
    val applicableTarget: String?,
    val versionNo: String?,
    val status: String,
    val scoreMethod: String,
    val scoreCoefficient: BigDecimal,
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
    val resultTitle: String?,
    val resultDescription: String?,
    val suggestionText: String?
)

data class ScaleDetail(
    val id: Long,
    val scaleCode: String,
    val scaleName: String,
    val description: String?,
    val applicableTarget: String?,
    val versionNo: String?,
    val status: String,
    val scoreMethod: String,
    val scoreCoefficient: BigDecimal,
    val anonymousSupported: Boolean,
    val reportTemplate: String?,
    val createdBy: Long?,
    val createdAt: LocalDateTime,
    val updatedBy: Long?,
    val updatedAt: LocalDateTime,
    val dimensions: List<ScaleDimension>,
    val questions: List<ScaleQuestion>,
    val resultRules: List<ScaleResultRule>
)
