package org.sainm.psy.warning.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.api.AssignWarningRequest
import org.sainm.psy.warning.api.WarningListQuery
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.domain.WarningAutomationCandidate
import org.sainm.psy.warning.domain.WarningAutomationResult
import org.sainm.psy.warning.domain.WarningSummary
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

@Service
class WarningService(
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages,
    private val transactionTemplate: TransactionTemplate,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    private val tenantAccessPolicy: TenantAccessPolicy,
    @Value("\${psy.warning.unclaimed-escalation-hours:24}")
    private val unclaimedEscalationHours: Long = 24,
    @Value("\${psy.warning.processing-reminder-hours:24}")
    private val processingReminderHours: Long = 24
) {

    fun findPage(query: WarningListQuery): PageResponse<WarningSummary> {
        require(query.page > 0) { "page must be greater than 0" }
        require(query.size in 1..200) { "size must be between 1 and 200" }
        val tenantId = tenantAccessPolicy.currentTenantFilter("WARNING", "LIST")
        val (list, total) = warningRepository.findPage(query, tenantId)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun claim(warningId: Long): WarningActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val warningTenantId = requireAccessibleWarningTenant(warningId, "CLAIM")
        if (!warningRepository.isActiveUserInTenant(currentUser.userId, warningTenantId)) {
            throw BizException("WARNING_ASSIGNEE_FORBIDDEN", messages.get("error.warning_assignee_forbidden"))
        }
        val result = warningRepository.claimWarning(warningId, currentUser.userId, currentUser.userId)
        securityAuditService.recordWarningClaimed(warningId)
        notificationDispatchService.notifyWarningClaimed(warningId, listOf(currentUser.userId))
        psyMetrics?.recordWarningAction("CLAIMED")
        return result
    }

    @Transactional
    fun assign(warningId: Long, request: AssignWarningRequest): WarningActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val warningTenantId = requireAccessibleWarningTenant(warningId, "ASSIGN")
        if (!warningRepository.isActiveUserInTenant(request.assigneeUserId, warningTenantId)) {
            throw BizException("WARNING_ASSIGNEE_FORBIDDEN", messages.get("error.warning_assignee_forbidden"))
        }
        val result = warningRepository.assignWarning(warningId, request.assigneeUserId, currentUser.userId)
        securityAuditService.recordWarningAssigned(warningId, request.assigneeUserId)
        notificationDispatchService.notifyWarningAssigned(warningId, listOf(request.assigneeUserId))
        psyMetrics?.recordWarningAction("ASSIGNED")
        return result
    }

    @Scheduled(fixedDelayString = "\${psy.warning.escalation-scan-delay-ms:60000}")
    fun processWarningEscalations(): WarningAutomationResult {
        val now = LocalDateTime.now()
        val lock = schedulerLockService ?: return processWarningEscalations(now)
        val jobName = "warning.escalation"
        val result = lock.withLock("warning:escalation", Duration.ofMinutes(2)) {
            psyMetrics?.recordSchedulerRun(jobName) { processWarningEscalations(now) }
                ?: processWarningEscalations(now)
        }
        if (result == null) {
            psyMetrics?.recordSchedulerSkipped(jobName)
        }
        return result ?: WarningAutomationResult(escalatedCount = 0, remindedCount = 0)
    }

    fun processWarningEscalations(now: LocalDateTime): WarningAutomationResult {
        val result = WarningAutomationResult(
            escalatedCount = processEscalations(now),
            remindedCount = processReminders(now)
        )
        warningRepository.findWarningQueueState(now)?.let { queue ->
            psyMetrics?.recordWarningQueueState(
                open = queue.openCount,
                overdue = queue.overdueCount,
                oldestOpenSeconds = queue.oldestOpenAgeSeconds
            )
        }
        return result
    }

    fun processEscalations(now: LocalDateTime): Int =
        (transactionTemplate.execute<List<WarningAutomationCandidate>> {
            val escalationCandidates = warningRepository.findHighRiskWarningsNeedingEscalation(
                fallbackCreatedBefore = now.minusHours(unclaimedEscalationHours),
                deadlineBefore = now
            )
            warningRepository.markWarningsEscalated(
                escalationCandidates.map { it.warningId },
                now
            )
            escalationCandidates
        } ?: emptyList()).let { candidates ->
            candidates.forEach { candidate ->
                notificationDispatchService.notifyWarningEscalated(candidate.warningId, candidate.receiverUserIds)
            }
            psyMetrics?.recordWarningAction("ESCALATED", candidates.size)
            candidates.size
        }

    fun processReminders(now: LocalDateTime): Int =
        (transactionTemplate.execute<List<WarningAutomationCandidate>> {
            val reminderCandidates = warningRepository.findWarningsNeedingReminder(
                now.minusHours(processingReminderHours)
            )
            warningRepository.markWarningsReminded(
                reminderCandidates.map { it.warningId },
                now
            )
            reminderCandidates
        } ?: emptyList()).let { candidates ->
            candidates.forEach { candidate ->
                notificationDispatchService.notifyWarningReminder(candidate.warningId, candidate.receiverUserIds)
            }
            psyMetrics?.recordWarningAction("REMINDED", candidates.size)
            candidates.size
        }

    private fun requireAccessibleWarningTenant(warningId: Long, action: String): Long {
        val tenantFilter = tenantAccessPolicy.currentTenantFilter("WARNING", action)
        if (!warningRepository.existsById(warningId, tenantFilter)) {
            throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
        }
        if (tenantFilter != null) return tenantFilter
        val targetTenantId = warningRepository.findTenantId(warningId)
            ?: throw BizException("WARNING_TENANT_REQUIRED", messages.get("error.warning_tenant_required"))
        tenantAccessPolicy.canAccess(targetTenantId, "WARNING", warningId, action)
        return targetTenantId
    }
}
