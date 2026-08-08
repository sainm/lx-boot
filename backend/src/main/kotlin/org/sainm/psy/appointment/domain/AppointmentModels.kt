package org.sainm.psy.appointment.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class CounselorScheduleSummary(
    val id: Long,
    val counselorUserId: Long,
    val scheduleDate: LocalDate,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val quotaCount: Int,
    val bookedCount: Int,
    val availableCount: Int,
    val status: String,
    val tenantId: Long? = null
)

data class CounselorOption(
    val userId: Long,
    val username: String,
    val displayName: String
)

data class AppointmentSummary(
    val id: Long,
    val userId: Long,
    val counselorUserId: Long,
    val counselorDisplayName: String?,
    val warningId: Long?,
    val scheduleId: Long?,
    val appointmentStatus: String,
    val sourceType: String,
    val remark: String?,
    val scheduleDate: LocalDate?,
    val startTime: LocalDateTime?,
    val endTime: LocalDateTime?,
    val createdAt: LocalDateTime
)

data class AppointmentDetail(
    val id: Long,
    val userId: Long,
    val counselorUserId: Long,
    val warningId: Long?,
    val scheduleId: Long?,
    val appointmentStatus: String,
    val sourceType: String,
    val remark: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val tenantId: Long? = null
)

data class AppointmentActionResult(
    val appointmentId: Long,
    val status: String
)
