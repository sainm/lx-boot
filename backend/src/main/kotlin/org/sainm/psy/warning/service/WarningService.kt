package org.sainm.psy.warning.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import org.sainm.psy.warning.api.AssignWarningRequest
import org.sainm.psy.warning.api.WarningListQuery
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.domain.WarningSummary
import org.sainm.psy.warning.repository.WarningRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WarningService(
    private val warningRepository: WarningRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService
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
        notificationDispatchService.notifyUsers(
            notificationType = "WARNING_CLAIMED",
            title = "预警已接单",
            content = "预警 #$warningId 已由当前处理人接单，请及时跟进。",
            bizType = "WARNING",
            bizId = warningId,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(currentUser.userId)
        )
        return result
    }

    @Transactional
    fun assign(warningId: Long, request: AssignWarningRequest): WarningActionResult {
        ensureWarningExists(warningId)
        val currentUser = currentUserFacade.requireCurrentUser()
        val result = warningRepository.assignWarning(warningId, request.assigneeUserId, currentUser.userId)
        securityAuditService.recordWarningAssigned(warningId, request.assigneeUserId)
        notificationDispatchService.notifyUsers(
            notificationType = "WARNING_ASSIGNED",
            title = "收到新的预警指派",
            content = "预警 #$warningId 已指派给你，请尽快查看报告并开始跟进。",
            bizType = "WARNING",
            bizId = warningId,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(request.assigneeUserId)
        )
        return result
    }

    private fun ensureWarningExists(warningId: Long) {
        if (!warningRepository.existsById(warningId)) {
            throw BizException("WARNING_NOT_FOUND", "预警不存在")
        }
    }
}
