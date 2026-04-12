package org.sainm.psy.warning.domain

import java.time.LocalDateTime

data class WarningSummary(
    val id: Long,
    val resultId: Long,
    val warningLevel: String,
    val warningPriority: String,
    val warningReason: String?,
    val status: String,
    val createdAt: LocalDateTime
)

data class WarningActionResult(
    val warningId: Long,
    val status: String,
    val assigneeUserId: Long? = null
)

data class WarningAutomationCandidate(
    val warningId: Long,
    val receiverUserIds: List<Long>
)

data class WarningAutomationResult(
    val escalatedCount: Int,
    val remindedCount: Int
)
