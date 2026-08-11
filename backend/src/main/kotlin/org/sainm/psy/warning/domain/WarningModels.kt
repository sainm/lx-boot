package org.sainm.psy.warning.domain

import java.time.LocalDateTime

data class WarningSummary(
    val id: Long,
    val resultId: Long,
    val warningLevel: String,
    val warningPriority: String,
    val warningReason: String?,
    val status: String,
    val createdAt: LocalDateTime,
    val deadlineTime: LocalDateTime? = null,
    val firstResponseTime: LocalDateTime? = null,
    val safetyPolicyId: Long? = null,
    val safetyPolicyVersion: Int? = null,
    val policyResolutionStatus: String = "MISSING"
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

data class WarningQueueState(
    val openCount: Long,
    val overdueCount: Long,
    val oldestOpenAgeSeconds: Long
)
