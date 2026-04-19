package org.sainm.psy.scale.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateScaleDimensionRequest(
    @field:NotBlank(message = "{validation.dimension_code_required}")
    val dimensionCode: String,

    @field:NotBlank(message = "{validation.dimension_name_required}")
    val dimensionName: String,

    val description: String? = null,

    @field:PositiveOrZero(message = "{validation.sort_no_non_negative}")
    val sortNo: Int = 0
)

data class BatchCreateScaleDimensionsRequest(
    @field:NotEmpty(message = "{validation.dimensions_required}")
    @field:Valid
    val dimensions: List<CreateScaleDimensionRequest>
)

data class CreateScaleQuestionOptionRequest(
    @field:NotBlank(message = "{validation.option_code_required}")
    val optionCode: String,

    @field:NotBlank(message = "{validation.option_label_required}")
    val optionLabel: String,

    @field:NotNull(message = "{validation.option_score_required}")
    val scoreValue: BigDecimal,

    val exclusiveFlag: Boolean = false,
    val optionGroupCode: String? = null,

    @field:PositiveOrZero(message = "{validation.sort_no_non_negative}")
    val sortNo: Int = 0
)

data class CreateScaleQuestionRequest(
    @field:NotNull(message = "{validation.question_no_required}")
    val questionNo: Int,

    @field:NotBlank(message = "{validation.question_title_required}")
    val questionTitle: String,

    @field:NotBlank(message = "{validation.question_type_required}")
    val questionType: String,

    val dimensionId: Long? = null,
    val requiredFlag: Boolean = true,
    val reverseScoreFlag: Boolean = false,
    @field:NotNull(message = "{validation.weight_required}")
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
    @field:PositiveOrZero(message = "{validation.sort_no_non_negative}")
    val sortNo: Int = 0,

    @field:Valid
    val options: List<CreateScaleQuestionOptionRequest> = emptyList()
)

data class BatchCreateScaleQuestionsRequest(
    @field:NotEmpty(message = "{validation.questions_required}")
    @field:Valid
    val questions: List<CreateScaleQuestionRequest>
)

data class CreateScaleResultRuleRequest(
    val dimensionId: Long? = null,

    @field:NotBlank(message = "{validation.risk_level_required}")
    val riskLevel: String,

    @field:NotNull(message = "{validation.score_min_required}")
    val scoreMin: BigDecimal,

    @field:NotNull(message = "{validation.score_max_required}")
    val scoreMax: BigDecimal,

    val scoreSource: String = "RAW_SCORE",
    val normCode: String? = null,
    val resultTitle: String? = null,
    val resultDescription: String? = null,
    val suggestionText: String? = null
)

data class BatchCreateScaleResultRulesRequest(
    @field:NotEmpty(message = "{validation.result_rules_required}")
    @field:Valid
    val resultRules: List<CreateScaleResultRuleRequest>
)

data class CreateScaleNormRequest(
    @field:NotBlank(message = "{validation.norm_code_required}")
    @field:Size(max = 64, message = "{validation.norm_code_size}")
    val normCode: String,

    @field:Size(max = 255, message = "{validation.norm_name_size}")
    val normName: String? = null,

    val dimensionId: Long? = null,

    @field:Size(max = 128, message = "{validation.applicable_target_size}")
    val applicableTarget: String? = null,
    val ageMin: Int? = null,
    val ageMax: Int? = null,

    @field:Size(max = 32, message = "{validation.gender_size}")
    val gender: String? = null,

    @field:Size(max = 64, message = "{validation.org_type_size}")
    val orgType: String? = null,

    val meanScore: BigDecimal? = null,
    val stdDeviation: BigDecimal? = null,
    val tScoreMean: BigDecimal? = null,
    val tScoreStdDeviation: BigDecimal? = null,

    @field:PositiveOrZero(message = "{validation.sort_no_non_negative}")
    val sortNo: Int = 0
)

data class BatchCreateScaleNormsRequest(
    @field:NotEmpty(message = "{validation.norms_required}")
    @field:Valid
    val norms: List<CreateScaleNormRequest>
)

data class BatchCreateResponse(
    val createdIds: List<Long>
)
