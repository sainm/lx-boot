package org.sainm.psy.assessment.service

import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnswerSheetService(
    private val answerSheetRepository: AnswerSheetRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService
) {

    fun getTaskQuestions(taskId: Long): TaskQuestionPayload {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", "当前任务未分配给该用户")
        }
        return answerSheetRepository.findTaskQuestionPayload(taskId)
            ?: throw BizException("TASK_NOT_FOUND", "测评任务不存在")
    }

    @Transactional
    fun save(request: SaveAnswerSheetRequest): Map<String, Any> {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(request.taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", "当前任务未分配给该用户")
        }
        val answerSheetId = answerSheetRepository.findDraftAnswerSheet(request.taskId, currentUser.userId)
            ?: answerSheetRepository.createAnswerSheet(request.taskId, request.scaleId, currentUser.userId, "DRAFT")
        answerSheetRepository.replaceAnswerItems(answerSheetId, request.answers)
        return mapOf("answerSheetId" to answerSheetId, "status" to "DRAFT")
    }

    @Transactional
    fun submit(request: SubmitAnswerSheetRequest): AnswerSubmitResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(request.taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", "当前任务未分配给该用户")
        }
        val answerSheetId = answerSheetRepository.findDraftAnswerSheet(request.taskId, currentUser.userId)
            ?: answerSheetRepository.createAnswerSheet(request.taskId, request.scaleId, currentUser.userId, "DRAFT")
        val totalScore = answerSheetRepository.replaceAnswerItems(answerSheetId, request.answers)
        answerSheetRepository.updateAnswerSheetStatus(answerSheetId, "SUBMITTED")

        val resolved = answerSheetRepository.resolveRisk(request.scaleId, totalScore)
        val riskLevel = resolved.first
        val resultSummary = buildString {
            append("总分：").append(totalScore.stripTrailingZeros().toPlainString())
            append("；风险等级：").append(riskLevel)
            resolved.second?.takeIf { it.isNotBlank() }?.let { append("；结果标题：").append(it) }
        }
        val resultId = answerSheetRepository.createResult(
            answerSheetId = answerSheetId,
            totalScore = totalScore,
            riskLevel = riskLevel,
            warningFlag = riskLevel != "NORMAL",
            resultSummary = resultSummary
        )
        val reportContent = buildString {
            append("系统自动报告").append("\n")
            append("总分：").append(totalScore.stripTrailingZeros().toPlainString()).append("\n")
            append("风险等级：").append(riskLevel).append("\n")
            resolved.third?.takeIf { it.isNotBlank() }?.let { append(it) }
        }
        val reportId = answerSheetRepository.createReport(
            resultId = resultId,
            authorUserId = currentUser.userId,
            title = resolved.second ?: "系统报告",
            content = reportContent
        )
        notificationDispatchService.notifyUsers(
            notificationType = "REPORT_GENERATED",
            title = "系统报告已生成",
            content = "你的测评已提交，系统报告现在可以查看。",
            bizType = "REPORT",
            bizId = reportId,
            targetPath = "/reports/$reportId?resultId=$resultId&taskId=${request.taskId}&notificationSource=REPORT_GENERATED",
            payloadJson = """{"reportId":$reportId,"resultId":$resultId,"taskId":${request.taskId},"riskLevel":"$riskLevel"}""",
            receiverUserIds = listOf(currentUser.userId)
        )
        answerSheetRepository.createWarningIfNeeded(
            resultId = resultId,
            riskLevel = riskLevel,
            reason = "系统根据量表计分规则自动识别出风险等级：$riskLevel"
        )
        return AnswerSubmitResult(
            answerSheetId = answerSheetId,
            resultId = resultId,
            reportId = reportId,
            riskLevel = riskLevel
        )
    }
}
