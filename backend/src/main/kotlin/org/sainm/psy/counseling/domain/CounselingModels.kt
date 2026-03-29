package org.sainm.psy.counseling.domain

import java.time.LocalDateTime

data class CounselingRecordDetail(
    val id: Long,
    val appointmentId: Long,
    val counselorUserId: Long,
    val summaryText: String?,
    val suggestionText: String?,
    val needRetestFlag: Boolean,
    val needTransferFlag: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CounselingRecordActionResult(
    val recordId: Long,
    val appointmentId: Long,
    val appointmentStatus: String
)
