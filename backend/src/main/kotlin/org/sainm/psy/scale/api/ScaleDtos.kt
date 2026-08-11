package org.sainm.psy.scale.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateScaleRequest(
    @field:NotBlank(message = "{validation.scale_code_required}")
    @field:Size(max = 64, message = "{validation.scale_code_size}")
    val scaleCode: String,

    @field:NotBlank(message = "{validation.scale_name_required}")
    @field:Size(max = 255, message = "{validation.scale_name_size}")
    val scaleName: String,

    val description: String? = null,
    val applicableTarget: String? = null,
    val versionNo: String? = "v1",
    val scoreMethod: String = "SIMPLE_SUM",

    @field:DecimalMin(value = "0.0001", message = "{validation.score_coefficient_positive}")
    val scoreCoefficient: BigDecimal = BigDecimal.ONE,

    val anonymousSupported: Boolean = false,
    val reportTemplate: String? = null
)

data class CreateScaleVersionRequest(
    @field:NotBlank(message = "{validation.scale_version_required}")
    val versionNo: String,
    val scaleName: String? = null,
    val description: String? = null
)

data class CreateScaleVersionResponse(
    val id: Long,
    val versionGroupId: Long,
    val versionNo: String,
    val status: String
)

data class PublishScaleVersionResponse(
    val id: Long,
    val versionGroupId: Long,
    val versionNo: String?,
    val status: String,
    val currentVersionFlag: Boolean,
    val contentHash: String? = null
)

data class ScaleListQuery(
    val scaleName: String? = null,
    val status: String? = null,
    val page: Int = 1,
    val size: Int = 20
)

data class CreateScaleResponse(
    val id: Long,
    val status: String
)

data class UpdateScaleBasicRequest(
    @field:NotBlank(message = "{validation.scale_name_required}")
    @field:Size(max = 255, message = "{validation.scale_name_size}")
    val scaleName: String,
    val description: String? = null,
    val applicableTarget: String? = null,
    val anonymousSupported: Boolean = false,
    val reportTemplate: String? = null
)

data class UpdateScaleDimensionRequest(
    @field:NotBlank(message = "{validation.dimension_name_required}")
    @field:Size(max = 255, message = "{validation.dimension_name_size}")
    val dimensionName: String,
    val description: String? = null,
    val sortNo: Int = 0
)

data class UpdateScaleQuestionRequest(
    val dimensionId: Long? = null,
    @field:NotBlank(message = "{validation.question_title_required}")
    val questionTitle: String,
    val requiredFlag: Boolean = true,
    val reverseScoreFlag: Boolean = false,
    @field:DecimalMin(value = "0.0001", message = "{validation.weight_value_positive}")
    val weightValue: BigDecimal = BigDecimal.ONE,
    val sortNo: Int = 0
)

data class UpdateScaleOptionRequest(
    @field:NotBlank(message = "{validation.option_label_required}")
    @field:Size(max = 255, message = "{validation.option_label_size}")
    val optionLabel: String,
    val scoreValue: BigDecimal,
    val exclusiveFlag: Boolean = false,
    val optionGroupCode: String? = null,
    val sortNo: Int = 0
)

data class UpdateScaleVisualizationsRequest(
    val visualizations: List<ScaleVisualizationConfigRequest> = emptyList()
)

data class ScaleVisualizationConfigRequest(
    @field:NotBlank(message = "{validation.chart_type_required}")
    val chartType: String,
    @field:NotBlank(message = "{validation.chart_title_required}")
    val chartTitle: String,
    @field:NotBlank(message = "{validation.view_scope_required}")
    val viewScope: String,
    @field:NotBlank(message = "{validation.data_source_required}")
    val dataSource: String,
    val configJson: String = "{}",
    val enabled: Boolean = true,
    val sortNo: Int = 0
)
