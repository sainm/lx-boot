package org.sainm.psy.intervention.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateInterventionRequest(
    @field:NotNull(message = "{validation.warning_id_required}")
    val warningId: Long,

    val counselorUserId: Long? = null,

    @field:NotBlank(message = "{validation.plan_text_required}")
    @field:Size(max = 2000, message = "{validation.intervention_text_size}")
    val planText: String
)

data class CloseInterventionRequest(
    @field:NotBlank(message = "{validation.close_summary_required}")
    @field:Size(max = 2000, message = "{validation.intervention_text_size}")
    val closeSummary: String,

    val needRetest: Boolean = false,

    @field:Pattern(
        regexp = "PHONE|IN_PERSON|VIDEO|OTHER",
        message = "{validation.contact_channel_invalid}"
    )
    val contactChannel: String? = null,

    @field:Size(max = 2000, message = "{validation.intervention_text_size}")
    val contactOutcome: String? = null,

    @field:Size(max = 2000, message = "{validation.intervention_text_size}")
    val safetyAssessmentSummary: String? = null,
    val imminentDangerFlag: Boolean? = null,

    @field:Size(max = 2000, message = "{validation.intervention_text_size}")
    val responsibleHandoffSummary: String? = null,
    val followUpDueTime: LocalDateTime? = null
)

data class InterventionActionResult(
    val interventionId: Long,
    val warningId: Long,
    val status: String,
    val retestTaskId: Long? = null
)
