package org.sainm.psy.intervention.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.intervention.api.CloseInterventionRequest
import org.sainm.psy.intervention.api.CreateInterventionRequest
import org.sainm.psy.intervention.api.InterventionActionResult
import org.sainm.psy.intervention.repository.InterventionRepository
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterventionService(
    private val interventionRepository: InterventionRepository,
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService
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
        notificationDispatchService.notifyUsers(
            notificationType = "INTERVENTION_CREATED",
            title = "新的干预记录已创建",
            content = "预警 #${request.warningId} 已进入干预流程，请及时处理。",
            bizType = "INTERVENTION",
            bizId = interventionId,
            targetPath = "/warnings",
            receiverUserIds = listOf(counselorUserId)
        )
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
            ?: throw BizException("INTERVENTION_NOT_FOUND", "干预记录不存在")
        if (!interventionRepository.closeIntervention(interventionId, request.closeSummary, currentUser.userId)) {
            throw BizException("INTERVENTION_NOT_FOUND", "干预记录不存在")
        }
        warningRepository.closeWarning(detail.warningId)
        securityAuditService.recordInterventionClosed(
            interventionId = interventionId,
            warningId = detail.warningId,
            counselorUserId = detail.counselorUserId ?: currentUser.userId
        )
        notificationDispatchService.notifyUsers(
            notificationType = "INTERVENTION_CLOSED",
            title = "干预记录已结案",
            content = "干预 #$interventionId 已结案，预警 #${detail.warningId} 已关闭。",
            bizType = "INTERVENTION",
            bizId = interventionId,
            targetPath = "/warnings",
            receiverUserIds = listOfNotNull(detail.counselorUserId)
        )
        return InterventionActionResult(
            interventionId = interventionId,
            warningId = detail.warningId,
            status = "CLOSED"
        )
    }

    private fun ensureWarningExists(warningId: Long) {
        if (!warningRepository.existsById(warningId)) {
            throw BizException("WARNING_NOT_FOUND", "预警不存在")
        }
    }
}
