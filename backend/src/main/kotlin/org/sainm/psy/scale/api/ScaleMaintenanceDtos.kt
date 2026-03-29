package org.sainm.psy.scale.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CreateScaleDimensionRequest(
    @field:NotBlank(message = "维度编码不能为空")
    val dimensionCode: String,

    @field:NotBlank(message = "维度名称不能为空")
    val dimensionName: String,

    val description: String? = null,

    @field:PositiveOrZero(message = "排序号不能为负数")
    val sortNo: Int = 0
)

data class BatchCreateScaleDimensionsRequest(
    @field:NotEmpty(message = "维度列表不能为空")
    @field:Valid
    val dimensions: List<CreateScaleDimensionRequest>
)

data class CreateScaleQuestionOptionRequest(
    @field:NotBlank(message = "选项编码不能为空")
    val optionCode: String,

    @field:NotBlank(message = "选项内容不能为空")
    val optionLabel: String,

    @field:NotNull(message = "选项分值不能为空")
    val scoreValue: BigDecimal,

    @field:PositiveOrZero(message = "排序号不能为负数")
    val sortNo: Int = 0
)

data class CreateScaleQuestionRequest(
    @field:NotNull(message = "题号不能为空")
    val questionNo: Int,

    @field:NotBlank(message = "题干不能为空")
    val questionTitle: String,

    @field:NotBlank(message = "题目类型不能为空")
    val questionType: String,

    val dimensionId: Long? = null,
    val requiredFlag: Boolean = true,
    val reverseScoreFlag: Boolean = false,
    @field:NotNull(message = "权重不能为空")
    val weightValue: BigDecimal = BigDecimal.ONE,
    @field:PositiveOrZero(message = "排序号不能为负数")
    val sortNo: Int = 0,

    @field:NotEmpty(message = "选项不能为空")
    @field:Valid
    val options: List<CreateScaleQuestionOptionRequest>
)

data class BatchCreateScaleQuestionsRequest(
    @field:NotEmpty(message = "题目列表不能为空")
    @field:Valid
    val questions: List<CreateScaleQuestionRequest>
)

data class CreateScaleResultRuleRequest(
    val dimensionId: Long? = null,

    @field:NotBlank(message = "风险等级不能为空")
    val riskLevel: String,

    @field:NotNull(message = "最低分不能为空")
    val scoreMin: BigDecimal,

    @field:NotNull(message = "最高分不能为空")
    val scoreMax: BigDecimal,

    val resultTitle: String? = null,
    val resultDescription: String? = null,
    val suggestionText: String? = null
)

data class BatchCreateScaleResultRulesRequest(
    @field:NotEmpty(message = "结果规则列表不能为空")
    @field:Valid
    val resultRules: List<CreateScaleResultRuleRequest>
)

data class BatchCreateResponse(
    val createdIds: List<Long>
)
