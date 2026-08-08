package org.sainm.psy.warning.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.api.AssignWarningRequest
import org.sainm.psy.warning.api.WarningListQuery
import org.sainm.psy.warning.domain.WarningActionResult
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
    @Value("\${psy.warning.unclaimed-escalation-hours:24}")
    private val unclaimedEscalationHours: Long = 24,
    @Value("\${psy.warning.processing-reminder-hours:24}")
    private val processingReminderHours: Long = 24
) {

    fun findPage(query: WarningListQuery): PageResponse<WarningSummary> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        val currentUser = currentUserFacade.requireCurrentUser()
        val (list, total) = warningRepository.findPage(query, currentUser.scopedTenantId())
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun claim(warningId: Long): WarningActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        ensureWarningAccess(warningId, currentUser)
        val result = warningRepository.claimWarning(warningId, currentUser.userId, currentUser.userId)
        securityAuditService.recordWarningClaimed(warningId)
        notificationDispatchService.notifyWarningClaimed(warningId, listOf(currentUser.userId))
        return result
    }

    @Transactional
    fun assign(warningId: Long, request: AssignWarningRequest): WarningActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        ensureWarningAccess(warningId, currentUser)
        if (!warningRepository.isActiveUserInTenant(request.assigneeUserId, currentUser.scopedTenantId())) {
            throw BizException("WARNING_ASSIGNEE_OUT_OF_SCOPE", messages.get("error.warning_assignee_out_of_scope"))
        }
        val result = warningRepository.assignWarning(warningId, request.assigneeUserId, currentUser.userId)
        securityAuditService.recordWarningAssigned(warningId, request.assigneeUserId)
        notificationDispatchService.notifyWarningAssigned(warningId, listOf(request.assigneeUserId))
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
        return WarningAutomationResult(
            escalatedCount = processEscalations(now),
            remindedCount = processReminders(now)
        )
    }

    fun processEscalations(now: LocalDateTime): Int =
        transactionTemplate.execute<Int> {
            val escalationCandidates = warningRepository.findHighRiskWarningsNeedingEscalation(
                now.minusHours(unclaimedEscalationHours)
            ).filter { it.receiverUserIds.isNotEmpty() }
            val escalatedCount = warningRepository.markWarningsEscalated(
                escalationCandidates.map { it.warningId },
                now
            )
            escalationCandidates.forEach { candidate ->
                notificationDispatchService.notifyWarningEscalated(candidate.warningId, candidate.receiverUserIds)
            }
            escalatedCount
        } ?: 0

    fun processReminders(now: LocalDateTime): Int =
        transactionTemplate.execute<Int> {
            val reminderCandidates = warningRepository.findWarningsNeedingReminder(
                now.minusHours(processingReminderHours)
            )
            val remindedCount = warningRepository.markWarningsReminded(
                reminderCandidates.map { it.warningId },
                now
            )
            reminderCandidates.forEach { candidate ->
                notificationDispatchService.notifyWarningReminder(candidate.warningId, candidate.receiverUserIds)
            }
            remindedCount
        } ?: 0

    private fun ensureWarningExists(warningId: Long) {
        if (!warningRepository.existsById(warningId)) {
            throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
        }
    }

    private fun ensureWarningAccess(warningId: Long, currentUser: UserPrincipal) {
        ensureWarningExists(warningId)
        if (currentUser.isGlobalAdmin()) return
        if (currentUser.tenantId != null && currentUser.tenantId == warningRepository.findTenantId(warningId)) return
        throw BizException("WARNING_FORBIDDEN", messages.get("error.warning_forbidden"))
    }

    private fun UserPrincipal.isGlobalAdmin(): Boolean =
        tenantId == null && roles.any { it in GLOBAL_ADMIN_ROLES }

    private fun UserPrincipal.scopedTenantId(): Long? = if (isGlobalAdmin()) null else tenantId

    companion object {
        private val GLOBAL_ADMIN_ROLES = setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN")
    }
}
