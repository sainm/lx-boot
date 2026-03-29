package org.sainm.psy.intervention.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateInterventionRequest(
    @field:NotNull(message = "预警不能为空")
    val warningId: Long,

    val counselorUserId: Long? = null,

    @field:NotBlank(message = "干预计划不能为空")
    val planText: String
)

data class CloseInterventionRequest(
    @field:NotBlank(message = "结案说明不能为空")
    val closeSummary: String
)

data class InterventionActionResult(
    val interventionId: Long,
    val warningId: Long,
    val status: String
)
