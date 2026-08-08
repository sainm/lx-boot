package org.sainm.psy.audit

import org.sainm.auth.core.spi.AuditEvent
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.notification.domain.UserDeviceSummary as NotificationUserDeviceSummary
import org.sainm.auth.core.spi.UserDeviceSummary as AuthUserDeviceSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SecurityAuditService(
    private val auditEventPublisher: AuditEventPublisher,
    private val currentUserFacade: CurrentUserFacade
) {

    fun runCatchingAudit(type: String, detail: Map<String, Any?>) {
        publish(type, detail)
    }

    fun recordReportViewed(
        reportId: Long,
        resultId: Long,
        reportType: String,
        riskLevel: String,
        accessPath: String
    ) {
        publish(
            type = "PSY_REPORT_VIEWED",
            detail = mapOf(
                "reportId" to reportId,
                "resultId" to resultId,
                "reportType" to reportType,
                "riskLevel" to riskLevel,
                "accessPath" to accessPath
            )
        )
    }

    fun recordReportExported(
        reportId: Long,
        resultId: Long,
        reportType: String,
        riskLevel: String,
        exportFormat: String,
        exportChannel: String
    ) {
        publish(
            type = "PSY_REPORT_EXPORTED",
            detail = mapOf(
                "reportId" to reportId,
                "resultId" to resultId,
                "reportType" to reportType,
                "riskLevel" to riskLevel,
                "exportFormat" to exportFormat,
                "exportChannel" to exportChannel
            )
        )
    }

    fun recordGroupReportExportRequested(
        jobId: String,
        taskId: Long,
        groupId: Long,
        exportFormat: String
    ) {
        publish(
            type = "PSY_GROUP_REPORT_EXPORT_REQUESTED",
            detail = mapOf(
                "jobId" to jobId,
                "taskId" to taskId,
                "groupId" to groupId,
                "exportFormat" to exportFormat
            )
        )
    }

    fun recordReportRegenerated(
        oldReportId: Long,
        newReportId: Long,
        resultId: Long,
        riskLevel: String
    ) {
        publish(
            type = "PSY_REPORT_REGENERATED",
            detail = mapOf(
                "oldReportId" to oldReportId,
                "newReportId" to newReportId,
                "resultId" to resultId,
                "riskLevel" to riskLevel
            )
        )
    }

    fun recordAssessmentResultRescored(
        answerSheetId: Long,
        resultId: Long,
        reportId: Long,
        previousRiskLevel: String,
        riskLevel: String
    ) {
        publish(
            type = "PSY_ASSESSMENT_RESULT_RESCORED",
            detail = mapOf(
                "answerSheetId" to answerSheetId,
                "resultId" to resultId,
                "reportId" to reportId,
                "previousRiskLevel" to previousRiskLevel,
                "riskLevel" to riskLevel
            )
        )
    }

    fun recordAppointmentTransition(
        appointmentId: Long,
        fromStatus: String?,
        toStatus: String,
        actionType: String,
        scheduleId: Long?
    ) {
        publish(
            type = "PSY_APPOINTMENT_TRANSITION",
            detail = mapOf(
                "appointmentId" to appointmentId,
                "fromStatus" to fromStatus,
                "toStatus" to toStatus,
                "actionType" to actionType,
                "scheduleId" to scheduleId
            )
        )
    }

    fun recordWarningClaimed(warningId: Long) {
        publish(
            type = "PSY_WARNING_CLAIMED",
            detail = mapOf("warningId" to warningId)
        )
    }

    fun recordWarningAssigned(warningId: Long, assigneeUserId: Long) {
        publish(
            type = "PSY_WARNING_ASSIGNED",
            detail = mapOf(
                "warningId" to warningId,
                "assigneeUserId" to assigneeUserId
            )
        )
    }

    fun recordInterventionCreated(interventionId: Long, warningId: Long, counselorUserId: Long) {
        publish(
            type = "PSY_INTERVENTION_CREATED",
            detail = mapOf(
                "interventionId" to interventionId,
                "warningId" to warningId,
                "counselorUserId" to counselorUserId
            )
        )
    }

    fun recordInterventionClosed(interventionId: Long, warningId: Long, counselorUserId: Long) {
        publish(
            type = "PSY_INTERVENTION_CLOSED",
            detail = mapOf(
                "interventionId" to interventionId,
                "warningId" to warningId,
                "counselorUserId" to counselorUserId
            )
        )
    }

    fun recordRetestTaskCreated(interventionId: Long, warningId: Long, retestTaskId: Long, receiverUserId: Long) {
        publish(
            type = "PSY_RETEST_TASK_CREATED",
            detail = mapOf(
                "interventionId" to interventionId,
                "warningId" to warningId,
                "retestTaskId" to retestTaskId,
                "receiverUserId" to receiverUserId
            )
        )
    }

    fun recordUserDeviceDeactivated(
        targetUserId: Long,
        device: NotificationUserDeviceSummary,
        revokedSessionCount: Int
    ) {
        publish(
            type = "PSY_USER_DEVICE_DEACTIVATED",
            detail = buildDeviceGovernanceDetail(
                targetUserId = targetUserId,
                deviceId = device.deviceId,
                deviceType = device.deviceType,
                activeFlag = device.activeFlag,
                deviceTrustLevel = device.deviceTrustLevel,
                riskSignals = device.riskSignals,
                riskLevel = device.riskLevel,
                autoDisposition = device.autoDisposition,
                autoDispositionReason = device.autoDispositionReason,
                authSessionId = device.authSessionId,
                authSessionStatus = device.authSessionStatus,
                authSessionLastSeenAt = device.authSessionLastSeenAt?.toString(),
                lastActiveAt = device.lastActiveAt?.toString(),
                revokedSessionCount = revokedSessionCount,
                triggerSource = "MANUAL_DEVICE_DEACTIVATION"
            )
        )
    }

    fun recordUserDeviceAutoDisposed(
        targetUserId: Long,
        device: AuthUserDeviceSummary,
        revokedSessionCount: Int? = null
    ) {
        publish(
            type = "PSY_USER_DEVICE_AUTO_DISPOSED",
            detail = buildDeviceGovernanceDetail(
                targetUserId = targetUserId,
                deviceId = device.deviceId,
                deviceType = device.deviceType,
                activeFlag = device.activeFlag,
                deviceTrustLevel = device.deviceTrustLevel,
                riskSignals = device.riskSignals,
                riskLevel = device.riskLevel,
                autoDisposition = device.autoDisposition,
                autoDispositionReason = device.autoDispositionReason,
                authSessionId = device.authSessionId,
                authSessionStatus = device.authSessionStatus,
                authSessionLastSeenAt = device.authSessionLastSeenAt,
                lastActiveAt = device.lastActiveAt,
                revokedSessionCount = revokedSessionCount,
                triggerSource = "AUTO_DEVICE_RISK_CONTROL"
            )
        )
    }

    private fun buildDeviceGovernanceDetail(
        targetUserId: Long,
        deviceId: String,
        deviceType: String,
        activeFlag: Boolean,
        deviceTrustLevel: String,
        riskSignals: List<String>,
        riskLevel: String,
        autoDisposition: String,
        autoDispositionReason: String?,
        authSessionId: String?,
        authSessionStatus: String?,
        authSessionLastSeenAt: String?,
        lastActiveAt: String?,
        revokedSessionCount: Int?,
        triggerSource: String
    ): Map<String, Any?> = mapOf(
        "targetUserId" to targetUserId,
        "deviceId" to deviceId,
        "deviceType" to deviceType,
        "activeFlag" to activeFlag,
        "deviceTrustLevel" to deviceTrustLevel,
        "riskSignals" to riskSignals,
        "riskLevel" to riskLevel,
        "autoDisposition" to autoDisposition,
        "autoDispositionReason" to autoDispositionReason,
        "authSessionId" to authSessionId,
        "authSessionStatus" to authSessionStatus,
        "authSessionLastSeenAt" to authSessionLastSeenAt,
        "lastActiveAt" to lastActiveAt,
        "revokedSessionCount" to revokedSessionCount,
        "triggerSource" to triggerSource
    )

    private fun publish(type: String, detail: Map<String, Any?>) {
        runCatching {
            val currentUser = currentUserFacade.requireCurrentUser()
            auditEventPublisher.publish(
                AuditEvent(
                    type = type,
                    userId = currentUser.userId,
                    principal = currentUser.username,
                    detail = detail + mapOf(
                        "tenantId" to currentUser.tenantId,
                        "groupId" to currentUser.groupId,
                        "roles" to currentUser.roles.toList()
                    )
                )
            )
        }.onFailure { error ->
            logger.warn("Failed to publish security audit event: {}", type, error)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityAuditService::class.java)
    }
}
