package org.sainm.psy.appointment.api

import jakarta.validation.constraints.NotNull

data class CreateAppointmentRequest(
    @field:NotNull(message = "Counselor user id is required")
    val counselorUserId: Long,

    @field:NotNull(message = "Schedule id is required")
    val scheduleId: Long,

    val warningId: Long? = null,

    val remark: String? = null
)

data class AppointmentCreateResponse(
    val appointmentId: Long,
    val status: String
)

data class AppointmentListQuery(
    val page: Int = 1,
    val size: Int = 20
)
