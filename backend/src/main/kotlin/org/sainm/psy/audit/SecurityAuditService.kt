package org.sainm.psy.audit

import org.sainm.auth.core.spi.AuditEvent
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.psy.auth.CurrentUserFacade
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SecurityAuditService(
    private val auditEventPublisher: AuditEventPublisher,
    private val currentUserFacade: CurrentUserFacade
) {

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
