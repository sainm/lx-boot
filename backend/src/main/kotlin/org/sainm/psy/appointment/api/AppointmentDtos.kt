package org.sainm.psy.appointment.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalDateTime

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

data class CreateScheduleRequest(
    @field:NotNull(message = "Schedule date is required")
    val scheduleDate: LocalDate,

    @field:NotNull(message = "Start time is required")
    val startTime: LocalDateTime,

    @field:NotNull(message = "End time is required")
    val endTime: LocalDateTime,

    @field:Min(value = 1, message = "Quota count must be at least 1")
    val quotaCount: Int = 1
)

data class CreateScheduleResponse(
    val id: Long
)

data class CounselorOptionResponse(
    val userId: Long,
    val username: String,
    val displayName: String
)
