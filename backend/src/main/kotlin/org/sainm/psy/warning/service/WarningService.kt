package org.sainm.psy.warning.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUserFacade
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
import java.time.Duration
import java.time.LocalDateTime

@Service
class WarningService(
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.warning.unclaimed-escalation-hours:24}")
    private val unclaimedEscalationHours: Long = 24,
    @Value("\${psy.warning.processing-reminder-hours:24}")
    private val processingReminderHours: Long = 24
) {

    fun findPage(query: WarningListQuery): PageResponse<WarningSummary> {
        require(query.page > 0) { "page must be greater than 0" }
        require(query.size in 1..200) { "size must be between 1 and 200" }
        val (list, total) = warningRepository.findPage(query)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun claim(warningId: Long): WarningActionResult {
        ensureWarningExists(warningId)
        val currentUser = currentUserFacade.requireCurrentUser()
        val result = warningRepository.claimWarning(warningId, currentUser.userId, currentUser.userId)
        securityAuditService.recordWarningClaimed(warningId)
        notificationDispatchService.notifyWarningClaimed(warningId, listOf(currentUser.userId))
        return result
    }

    @Transactional
    fun assign(warningId: Long, request: AssignWarningRequest): WarningActionResult {
        ensureWarningExists(warningId)
        val currentUser = currentUserFacade.requireCurrentUser()
        val result = warningRepository.assignWarning(warningId, request.assigneeUserId, currentUser.userId)
        securityAuditService.recordWarningAssigned(warningId, request.assigneeUserId)
        notificationDispatchService.notifyWarningAssigned(warningId, listOf(request.assigneeUserId))
        return result
    }

    @Scheduled(fixedDelayString = "\${psy.warning.escalation-scan-delay-ms:60000}")
    @Transactional
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

    @Transactional
    fun processWarningEscalations(now: LocalDateTime): WarningAutomationResult {
        val escalationCandidates = warningRepository.findHighRiskWarningsNeedingEscalation(
            now.minusHours(unclaimedEscalationHours)
        )
        val escalatedCount = warningRepository.markWarningsEscalated(
            escalationCandidates.map { it.warningId },
            now
        )
        escalationCandidates.forEach { candidate ->
            notificationDispatchService.notifyWarningEscalated(candidate.warningId, candidate.receiverUserIds)
        }

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

        return WarningAutomationResult(
            escalatedCount = escalatedCount,
            remindedCount = remindedCount
        )
    }

    private fun ensureWarningExists(warningId: Long) {
        if (!warningRepository.existsById(warningId)) {
            throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
        }
    }
}
