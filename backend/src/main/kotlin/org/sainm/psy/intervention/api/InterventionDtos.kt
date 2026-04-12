package org.sainm.psy.intervention.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateInterventionRequest(
    @field:NotNull(message = "{validation.warning_id_required}")
    val warningId: Long,

    val counselorUserId: Long? = null,

    @field:NotBlank(message = "{validation.plan_text_required}")
    val planText: String
)

data class CloseInterventionRequest(
    @field:NotBlank(message = "{validation.close_summary_required}")
    val closeSummary: String,

    val needRetest: Boolean = false
)

data class InterventionActionResult(
    val interventionId: Long,
    val warningId: Long,
    val status: String,
    val retestTaskId: Long? = null
)
