package org.sainm.psy.intervention.service

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.intervention.api.CloseInterventionRequest
import org.sainm.psy.intervention.api.CreateInterventionRequest
import org.sainm.psy.intervention.api.InterventionActionResult
import org.sainm.psy.intervention.repository.InterventionRepository
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InterventionService(
    private val interventionRepository: InterventionRepository,
    private val assessmentTaskRepository: AssessmentTaskRepository,
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages
) {

    @Transactional
    fun create(request: CreateInterventionRequest): InterventionActionResult {
        ensureWarningExists(request.warningId)
        val currentUser = currentUserFacade.requireCurrentUser()
        val counselorUserId: Long = request.counselorUserId ?: currentUser.userId
        warningRepository.markProcessing(request.warningId)
        val interventionId = interventionRepository.createIntervention(
            warningId = request.warningId,
            counselorUserId = counselorUserId,
            planText = request.planText,
            createdBy = currentUser.userId
        )
        securityAuditService.recordInterventionCreated(interventionId, request.warningId, counselorUserId)
        notificationDispatchService.notifyInterventionCreated(interventionId, request.warningId, listOf(counselorUserId))
        return InterventionActionResult(
            interventionId = interventionId,
            warningId = request.warningId,
            status = "PROCESSING"
        )
    }

    @Transactional
    fun close(interventionId: Long, request: CloseInterventionRequest): InterventionActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val detail = interventionRepository.findDetailById(interventionId)
            ?: throw BizException("INTERVENTION_NOT_FOUND", messages.get("error.intervention_not_found"))
        if (!interventionRepository.closeIntervention(interventionId, request.closeSummary, request.needRetest, currentUser.userId)) {
            throw BizException("INTERVENTION_NOT_FOUND", messages.get("error.intervention_not_found"))
        }
        warningRepository.closeWarning(detail.warningId)
        val retestTaskId = if (request.needRetest && detail.retestTaskId == null) {
            createRetestTask(interventionId = interventionId, warningId = detail.warningId, operatorUserId = currentUser.userId)
        } else {
            detail.retestTaskId
        }
        securityAuditService.recordInterventionClosed(
            interventionId = interventionId,
            warningId = detail.warningId,
            counselorUserId = detail.counselorUserId ?: currentUser.userId
        )
        notificationDispatchService.notifyInterventionClosed(interventionId, detail.warningId, listOfNotNull(detail.counselorUserId))
        return InterventionActionResult(
            interventionId = interventionId,
            warningId = detail.warningId,
            status = "CLOSED",
            retestTaskId = retestTaskId
        )
    }

    private fun createRetestTask(interventionId: Long, warningId: Long, operatorUserId: Long): Long {
        val seed = interventionRepository.findRetestTaskSeed(warningId)
            ?: throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
        val now = LocalDateTime.now()
        val taskName = messages.get("intervention.retest.task_name", seed.sourceTaskName)
        val taskId = assessmentTaskRepository.create(
            request = CreateAssessmentTaskRequest(
                taskName = taskName,
                scaleId = seed.scaleId,
                taskMode = "RETEST",
                anonymousFlag = false,
                allowSaveFlag = true,
                allowTimeoutSubmitFlag = false,
                allowRetakeFlag = false,
                startTime = now,
                endTime = now.plusDays(7)
            ),
            createdBy = operatorUserId
        )
        assessmentTaskRepository.assignTargets(taskId, "USER", listOf(seed.userId), operatorUserId)
        interventionRepository.markRetestTaskCreated(interventionId, taskId)
        notificationDispatchService.notifyRetestTaskCreated(taskId, taskName, warningId, interventionId, listOf(seed.userId))
        securityAuditService.recordRetestTaskCreated(interventionId, warningId, taskId, seed.userId)
        return taskId
    }

    private fun ensureWarningExists(warningId: Long) {
        if (!warningRepository.existsById(warningId)) {
            throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
        }
    }
}
