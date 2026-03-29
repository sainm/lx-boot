package org.sainm.psy.scale.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateScaleRequest(
    @field:NotBlank(message = "量表编码不能为空")
    @field:Size(max = 64, message = "量表编码长度不能超过 64")
    val scaleCode: String,

    @field:NotBlank(message = "量表名称不能为空")
    @field:Size(max = 255, message = "量表名称长度不能超过 255")
    val scaleName: String,

    val description: String? = null,
    val applicableTarget: String? = null,
    val versionNo: String? = "v1",
    val scoreMethod: String = "SIMPLE_SUM",

    @field:DecimalMin(value = "0.0001", message = "换算系数必须大于 0")
    val scoreCoefficient: BigDecimal = BigDecimal.ONE,

    val anonymousSupported: Boolean = false,
    val reportTemplate: String? = null
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
