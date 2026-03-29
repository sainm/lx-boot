package org.sainm.psy.intervention.domain

import java.time.LocalDateTime

data class InterventionDetail(
    val id: Long,
    val warningId: Long,
    val counselorUserId: Long?,
    val currentStatus: String,
    val planText: String?,
    val closeSummary: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
