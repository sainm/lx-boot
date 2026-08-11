package org.sainm.psy.intervention.service

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.security.TenantAccessPolicy
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
    private val messages: LocalizedMessages,
    private val tenantAccessPolicy: TenantAccessPolicy,
    private val psyMetrics: PsyMetrics? = null
) {

    @Transactional
    fun create(request: CreateInterventionRequest): InterventionActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        val warningTenantId = requireAccessibleWarningTenant(request.warningId, "CREATE_INTERVENTION")
        ensureNoActiveIntervention(request.warningId)
        val counselorUserId: Long = request.counselorUserId ?: currentUser.userId
        if (!warningRepository.isActiveUserInTenant(counselorUserId, warningTenantId)) {
            throw BizException("WARNING_ASSIGNEE_FORBIDDEN", messages.get("error.warning_assignee_forbidden"))
        }
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
        if (!tenantAccessPolicy.canAccess(detail.tenantId, "INTERVENTION", interventionId, "CLOSE")) {
            throw BizException("INTERVENTION_NOT_FOUND", messages.get("error.intervention_not_found"))
        }
        val riskCategory = warningRepository.findRiskCategory(detail.warningId, detail.tenantId)
            ?: throw BizException("WARNING_NOT_FOUND", messages.get("error.warning_not_found"))
        val closureEvidence = validateClosureEvidence(request, riskCategory)
        val warningTenantId = closureEvidence?.let {
            warningRepository.findTenantId(detail.warningId)
                ?: throw BizException("WARNING_TENANT_REQUIRED", messages.get("error.warning_tenant_required"))
        }
        if (!interventionRepository.closeIntervention(interventionId, request.closeSummary, request.needRetest, currentUser.userId)) {
            throw BizException("INTERVENTION_NOT_FOUND", messages.get("error.intervention_not_found"))
        }
        if (closureEvidence != null && warningTenantId != null) {
            warningRepository.recordClosureEvidenceAndClose(
                warningId = detail.warningId,
                tenantId = warningTenantId,
                performedBy = currentUser.userId,
                contactChannel = closureEvidence.contactChannel,
                contactOutcome = closureEvidence.contactOutcome,
                safetyAssessmentSummary = closureEvidence.safetyAssessmentSummary,
                imminentDangerFlag = closureEvidence.imminentDangerFlag,
                responsibleHandoffSummary = closureEvidence.responsibleHandoffSummary,
                followUpDueTime = closureEvidence.followUpDueTime,
                closureReason = request.closeSummary
            )
        } else {
            warningRepository.closeWarning(detail.warningId)
        }
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
        psyMetrics?.recordWarningAction("CLOSED")
        return InterventionActionResult(
            interventionId = interventionId,
            warningId = detail.warningId,
            status = "CLOSED",
            retestTaskId = retestTaskId
        )
    }

    private fun validateClosureEvidence(request: CloseInterventionRequest, riskCategory: String): ClosureEvidence? {
        if (riskCategory !in setOf("P0", "P1")) return null
        fun required(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BizException("WARNING_CLOSE_CHECKLIST_REQUIRED", messages.get("error.warning_close_checklist_required"))
        val imminentDanger = request.imminentDangerFlag
            ?: throw BizException("WARNING_CLOSE_CHECKLIST_REQUIRED", messages.get("error.warning_close_checklist_required"))
        if (imminentDanger) {
            throw BizException("WARNING_IMMINENT_DANGER_OPEN", messages.get("error.warning_imminent_danger_open"))
        }
        val followUpDueTime = request.followUpDueTime
            ?.takeIf { it.isAfter(LocalDateTime.now()) }
            ?: throw BizException("WARNING_FOLLOW_UP_TIME_INVALID", messages.get("error.warning_follow_up_time_invalid"))
        return ClosureEvidence(
            contactChannel = required(request.contactChannel),
            contactOutcome = required(request.contactOutcome),
            safetyAssessmentSummary = required(request.safetyAssessmentSummary),
            imminentDangerFlag = false,
            responsibleHandoffSummary = required(request.responsibleHandoffSummary),
            followUpDueTime = followUpDueTime
        )
    }

    private data class ClosureEvidence(
        val contactChannel: String,
        val contactOutcome: String,
        val safetyAssessmentSummary: String,
        val imminentDangerFlag: Boolean,
        val responsibleHandoffSummary: String,
        val followUpDueTime: LocalDateTime
    )

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

    private fun ensureNoActiveIntervention(warningId: Long) {
        interventionRepository.findByWarningId(warningId)
            ?.takeUnless { it.currentStatus == "CLOSED" }
            ?.let {
                throw BizException("INTERVENTION_ALREADY_EXISTS", messages.get("error.intervention_already_exists"))
            }
    }
}
