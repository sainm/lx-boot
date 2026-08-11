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

    fun recordExportJobSubmitted(
        jobId: String,
        reportId: Long?,
        resultId: Long?,
        exportFormat: String?,
        desensitized: Boolean
    ) {
        publishRequired(
            type = "PSY_EXPORT_JOB_SUBMITTED",
            detail = mapOf(
                "jobId" to jobId,
                "reportId" to reportId,
                "resultId" to resultId,
                "exportFormat" to exportFormat,
                "desensitized" to desensitized
            )
        )
    }

    fun recordExportJobReplayed(
        jobId: String,
        reportId: Long?,
        resultId: Long?,
        previousStatus: String,
        previousRetryCount: Int
    ) {
        publishRequired(
            type = "PSY_EXPORT_JOB_REPLAYED",
            detail = mapOf(
                "jobId" to jobId,
                "reportId" to reportId,
                "resultId" to resultId,
                "previousStatus" to previousStatus,
                "previousRetryCount" to previousRetryCount
            )
        )
    }

    fun recordExportJobDownloaded(
        jobId: String,
        reportId: Long?,
        resultId: Long?,
        exportFormat: String?,
        fileSize: Long?
    ) {
        publishRequired(
            type = "PSY_EXPORT_JOB_DOWNLOADED",
            detail = mapOf(
                "jobId" to jobId,
                "reportId" to reportId,
                "resultId" to resultId,
                "exportFormat" to exportFormat,
                "fileSize" to fileSize
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
        previousResultId: Long,
        resultId: Long,
        reportId: Long,
        previousRiskLevel: String,
        riskLevel: String
    ) {
        publish(
            type = "PSY_ASSESSMENT_RESULT_RESCORED",
            detail = mapOf(
                "answerSheetId" to answerSheetId,
                "previousResultId" to previousResultId,
                "resultId" to resultId,
                "reportId" to reportId,
                "previousRiskLevel" to previousRiskLevel,
                "riskLevel" to riskLevel
            )
        )
    }

    fun recordTenantScopeOverride(
        resourceType: String,
        resourceId: Any?,
        action: String,
        targetTenantId: Long?
    ) {
        publish(
            type = "PSY_TENANT_SCOPE_OVERRIDE",
            detail = mapOf(
                "resourceType" to resourceType,
                "resourceId" to resourceId,
                "action" to action,
                "targetTenantId" to targetTenantId
            )
        )
    }

    fun recordScalePackageUpdated(scaleId: Long, localeCodes: List<String>, validityRuleCount: Int) {
        publish(
            type = "PSY_SCALE_PACKAGE_UPDATED",
            detail = mapOf(
                "scaleId" to scaleId,
                "localeCodes" to localeCodes.sorted(),
                "validityRuleCount" to validityRuleCount
            )
        )
    }

    fun recordScalePackageExported(
        scaleId: Long,
        exportId: String,
        scaleContentHash: String,
        releaseFingerprint: String,
        schemaVersion: Int,
        caseRevisionCount: Int,
        runCount: Int,
        reviewCount: Int
    ) {
        publishRequired(
            type = "PSY_SCALE_PACKAGE_EXPORTED",
            detail = mapOf(
                "scaleId" to scaleId,
                "exportId" to exportId,
                "scaleContentHash" to scaleContentHash,
                "releaseFingerprint" to releaseFingerprint,
                "schemaVersion" to schemaVersion,
                "caseRevisionCount" to caseRevisionCount,
                "runCount" to runCount,
                "reviewCount" to reviewCount
            )
        )
    }

    fun recordScalePackageImported(
        importId: Long,
        scaleId: Long,
        payloadHash: String,
        goldenCaseRevisionCount: Int,
        discardedRunCount: Int,
        discardedReviewCount: Int
    ) {
        publishRequired(
            "PSY_SCALE_PACKAGE_IMPORTED",
            mapOf(
                "importId" to importId,
                "scaleId" to scaleId,
                "payloadHash" to payloadHash,
                "goldenCaseRevisionCount" to goldenCaseRevisionCount,
                "discardedRunCount" to discardedRunCount,
                "discardedReviewCount" to discardedReviewCount
            )
        )
    }

    fun recordScaleGoldenCaseSaved(scaleId: Long, caseId: Long, caseCode: String, revisionNo: Int) {
        publish("PSY_SCALE_GOLDEN_CASE_SAVED", mapOf("scaleId" to scaleId, "caseId" to caseId, "caseCode" to caseCode, "revisionNo" to revisionNo))
    }

    fun recordScaleGoldenCaseRun(scaleId: Long, caseId: Long, runId: Long, passed: Boolean) {
        publish("PSY_SCALE_GOLDEN_CASE_RUN", mapOf("scaleId" to scaleId, "caseId" to caseId, "runId" to runId, "passed" to passed))
    }

    fun recordScaleGoldenCaseApproved(scaleId: Long, caseId: Long) {
        publish("PSY_SCALE_GOLDEN_CASE_APPROVED", mapOf("scaleId" to scaleId, "caseId" to caseId))
    }

    fun recordScalePublicationReviewed(scaleId: Long, reviewType: String, decision: String, releaseFingerprint: String) {
        publish(
            "PSY_SCALE_PUBLICATION_REVIEWED",
            mapOf("scaleId" to scaleId, "reviewType" to reviewType, "decision" to decision, "releaseFingerprint" to releaseFingerprint)
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

    fun recordNotificationDeliveriesRetried(
        notificationIds: List<Long>,
        deliveryChannel: String?,
        retriedCount: Int
    ) {
        publishRequired(
            type = "PSY_NOTIFICATION_DELIVERIES_RETRIED",
            detail = mapOf(
                "notificationIds" to notificationIds.distinct().sorted(),
                "deliveryChannel" to deliveryChannel,
                "retriedCount" to retriedCount
            )
        )
    }

    fun recordNotificationDeliveryCallbackApplied(
        deliveryId: Long,
        notificationId: Long,
        deliveryStatus: String,
        providerName: String?
    ) {
        publishRequired(
            type = "PSY_NOTIFICATION_DELIVERY_CALLBACK_APPLIED",
            detail = mapOf(
                "deliveryId" to deliveryId,
                "notificationId" to notificationId,
                "deliveryStatus" to deliveryStatus,
                "providerName" to providerName
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
        runCatching { publishRequired(type, detail) }.onFailure { error ->
            logger.warn("Failed to publish security audit event: {}", type, error)
        }
    }

    private fun publishRequired(type: String, detail: Map<String, Any?>) {
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
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityAuditService::class.java)
    }
}
