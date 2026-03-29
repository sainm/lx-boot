package org.sainm.psy.counseling.api

import jakarta.validation.constraints.NotNull

data class CreateCounselingRecordRequest(
    @field:NotNull(message = "Appointment id is required")
    val appointmentId: Long,

    val summaryText: String? = null,

    val suggestionText: String? = null,

    val needRetestFlag: Boolean = false,

    val needTransferFlag: Boolean = false
)
