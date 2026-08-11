package org.sainm.psy.scale.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class GoldenCaseAnswerInput(
    @field:Positive val questionNo: Int,
    val optionCodes: List<String> = emptyList(),
    val answerValue: BigDecimal? = null,
    val answerText: String? = null
)

data class GoldenCaseNormInput(
    val age: Int? = null,
    val gender: String? = null,
    val orgType: String? = null,
    val applicableTarget: String? = null,
    val preferredNormCode: String? = null
)

data class GoldenCaseInput(
    @field:Valid val answers: List<GoldenCaseAnswerInput> = emptyList(),
    val durationSeconds: Int? = null,
    val norm: GoldenCaseNormInput? = null
)

data class GoldenCaseExpectedDimension(
    val score: BigDecimal,
    val riskLevel: String? = null,
    val normCode: String? = null
)

data class GoldenCaseExpected(
    val valid: Boolean = true,
    val errorCode: String? = null,
    val totalScore: BigDecimal? = null,
    val riskLevel: String? = null,
    val highRiskTriggered: Boolean? = null,
    val highRiskRuleCode: String? = null,
    val normCode: String? = null,
    val dimensions: Map<String, GoldenCaseExpectedDimension> = emptyMap()
)

data class CreateScaleGoldenCaseRequest(
    @field:NotBlank val caseCode: String,
    @field:NotBlank val caseType: String,
    @field:NotBlank val sourceReference: String,
    @field:Valid val input: GoldenCaseInput,
    @field:Valid val expected: GoldenCaseExpected
)

data class ScalePublicationReviewRequest(
    @field:NotBlank val decision: String,
    @field:NotBlank val reviewToken: String,
    val comment: String? = null
)

data class GoldenCaseRunResponse(
    val runId: Long,
    val goldenCaseId: Long,
    val passed: Boolean,
    val actual: Map<String, Any?>,
    val differences: List<String>
)
