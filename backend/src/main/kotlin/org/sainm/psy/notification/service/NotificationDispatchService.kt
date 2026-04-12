package org.sainm.psy.notification.service

import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.stereotype.Service

@Service
class NotificationDispatchService(
    private val notificationRepository: NotificationRepository,
    private val notificationPolicyService: NotificationPolicyService,
    private val messages: LocalizedMessages
) {

    fun notifyTaskAssigned(
        taskId: Long,
        taskName: String,
        scaleId: Long,
        endTime: Any,
        status: String,
        receiverUserIds: Collection<Long?>
    ) {
        send(
            notificationType = "TASK_ASSIGNED",
            title = messages.get("task.assigned.title"),
            content = messages.get("task.assigned.content", taskName),
            bizType = "TASK",
            bizId = taskId,
            targetPath = "/my/tasks/$taskId",
            payloadJson = """{"taskId":$taskId,"taskName":"${taskName.replace("\"", "\\\"")}","scaleId":$scaleId,"endTime":"$endTime","status":"$status"}""",
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyTaskOverdue(taskId: Long, taskName: String, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "TASK_OVERDUE",
            title = messages.get("task.overdue.title"),
            content = messages.get("task.overdue.content", taskName),
            bizType = "TASK",
            bizId = taskId,
            targetPath = "/my/tasks",
            payloadJson = """{"taskId":$taskId,"status":"OVERDUE"}""",
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyReportGenerated(
        reportId: Long,
        resultId: Long,
        taskId: Long,
        riskLevel: String,
        autoSubmitted: Boolean,
        receiverUserIds: Collection<Long?>
    ) {
        val notificationSource = if (autoSubmitted) "REPORT_AUTO_SUBMITTED" else "REPORT_GENERATED"
        send(
            notificationType = notificationSource,
            title = if (autoSubmitted) messages.get("report.auto_submitted.title") else messages.get("report.generated.title"),
            content = if (autoSubmitted) messages.get("report.auto_submitted.content") else messages.get("report.generated.content"),
            bizType = "REPORT",
            bizId = reportId,
            targetPath = "/reports/$reportId?resultId=$resultId&taskId=$taskId&notificationSource=$notificationSource",
            payloadJson = """{"reportId":$reportId,"resultId":$resultId,"taskId":$taskId,"riskLevel":"$riskLevel","notificationSource":"$notificationSource"}""",
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyAppointmentCreated(appointmentId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "APPOINTMENT_CREATED",
            title = messages.get("appointment.created.title"),
            content = messages.get("appointment.created.content", appointmentId),
            bizType = "APPOINTMENT",
            bizId = appointmentId,
            targetPath = "/appointments",
            payloadJson = null,
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyWarningClaimed(warningId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "WARNING_CLAIMED",
            title = messages.get("warning.claimed.title"),
            content = messages.get("warning.claimed.content", warningId),
            bizType = "WARNING",
            bizId = warningId,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyWarningAssigned(warningId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "WARNING_ASSIGNED",
            title = messages.get("warning.assigned.title"),
            content = messages.get("warning.assigned.content", warningId),
            bizType = "WARNING",
            bizId = warningId,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyWarningEscalated(warningId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "WARNING_ESCALATED",
            title = messages.get("warning.escalated.title"),
            content = messages.get("warning.escalated.content", warningId),
            bizType = "WARNING",
            bizId = warningId,
            targetPath = "/warnings",
            payloadJson = """{"warningId":$warningId,"priority":"P0"}""",
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyWarningReminder(warningId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "WARNING_REMINDER",
            title = messages.get("warning.reminder.title"),
            content = messages.get("warning.reminder.content", warningId),
            bizType = "WARNING",
            bizId = warningId,
            targetPath = "/warnings",
            payloadJson = """{"warningId":$warningId,"reminder":true}""",
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyInterventionCreated(interventionId: Long, warningId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "INTERVENTION_CREATED",
            title = messages.get("intervention.created.title"),
            content = messages.get("intervention.created.content", warningId),
            bizType = "INTERVENTION",
            bizId = interventionId,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyInterventionClosed(interventionId: Long, warningId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "INTERVENTION_CLOSED",
            title = messages.get("intervention.closed.title"),
            content = messages.get("intervention.closed.content", interventionId, warningId),
            bizType = "INTERVENTION",
            bizId = interventionId,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyRetestTaskCreated(taskId: Long, taskName: String, warningId: Long, interventionId: Long, receiverUserIds: Collection<Long?>) {
        send(
            notificationType = "RETEST_TASK_CREATED",
            title = messages.get("intervention.retest.created.title"),
            content = messages.get("intervention.retest.created.content", taskName),
            bizType = "TASK",
            bizId = taskId,
            targetPath = "/my/tasks/$taskId",
            payloadJson = """{"taskId":$taskId,"taskMode":"RETEST","sourceWarningId":$warningId,"sourceInterventionId":$interventionId}""",
            receiverUserIds = receiverUserIds
        )
    }

    fun notifyUsers(
        notificationType: String,
        title: String,
        content: String,
        bizType: String,
        bizId: Long?,
        targetPath: String?,
        payloadJson: String?,
        receiverUserIds: Collection<Long?>
    ) = send(notificationType, title, content, bizType, bizId, targetPath, payloadJson, receiverUserIds)

    private fun send(
        notificationType: String,
        title: String,
        content: String,
        bizType: String,
        bizId: Long?,
        targetPath: String?,
        payloadJson: String?,
        receiverUserIds: Collection<Long?>
    ) {
        val normalizedReceiverIds = receiverUserIds.mapNotNull { it }.distinct()
        if (normalizedReceiverIds.isEmpty()) {
            return
        }
        val policy = notificationPolicyService.resolvePolicy(notificationType)
        val deliveryChannels = buildSet {
            if (policy.inAppEnabled) add("IN_APP")
            if (policy.pushEnabled) add("PUSH")
        }
        if (deliveryChannels.isEmpty()) {
            return
        }
        val finalReceiverIds = if (policy.cooldownMinutes > 0) {
            val cutoff = java.time.LocalDateTime.now().minusMinutes(policy.cooldownMinutes.toLong())
            val blocked = notificationRepository.findUsersWithRecentNotifications(
                notificationType = notificationType,
                receiverUserIds = normalizedReceiverIds,
                since = cutoff
            )
            normalizedReceiverIds.filterNot(blocked::contains)
        } else {
            normalizedReceiverIds
        }
        if (finalReceiverIds.isEmpty()) {
            return
        }
        notificationRepository.createNotification(
            notificationType = notificationType,
            title = title,
            content = content,
            bizType = bizType,
            bizId = bizId,
            targetPath = targetPath,
            payloadJson = payloadJson,
            receiverUserIds = finalReceiverIds,
            deliveryChannels = deliveryChannels
        )
    }
}
