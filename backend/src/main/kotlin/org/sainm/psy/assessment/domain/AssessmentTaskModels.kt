package org.sainm.psy.assessment.domain

import java.time.LocalDateTime

data class AssessmentTaskSummary(
    val id: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val scaleVersionNo: String?,
    val scaleVersionGroupId: Long?,
    val taskMode: String,
    val anonymousFlag: Boolean,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val status: String
)

data class AssessmentTaskAssignment(
    val id: Long,
    val taskId: Long,
    val targetType: String,
    val targetId: Long,
    val assignedBy: Long?,
    val assignedAt: LocalDateTime
)

data class AssessmentTaskDetail(
    val id: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val scaleVersionNo: String?,
    val scaleVersionGroupId: Long?,
    val taskMode: String,
    val anonymousFlag: Boolean,
    val allowSaveFlag: Boolean,
    val allowTimeoutSubmitFlag: Boolean,
    val allowRetakeFlag: Boolean,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val status: String,
    val createdBy: Long?,
    val createdAt: LocalDateTime,
    val assignments: List<AssessmentTaskAssignment>,
    val closedAt: LocalDateTime? = null,
    val closedBy: Long? = null,
    val closeReason: String? = null
)

data class MyAssessmentTask(
    val taskId: Long,
    val taskName: String,
    val scaleId: Long,
    val scaleName: String,
    val endTime: LocalDateTime,
    val status: String
)

data class OverdueTaskNotification(
    val taskId: Long,
    val taskName: String,
    val receiverUserIds: List<Long>
)
