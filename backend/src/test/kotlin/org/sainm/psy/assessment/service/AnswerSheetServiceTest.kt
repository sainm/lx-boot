package org.sainm.psy.assessment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.assessment.api.AnswerItemRequest
import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class AnswerSheetServiceTest {

    @Mock private lateinit var answerSheetRepository: AnswerSheetRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService

    @InjectMocks
    private lateinit var answerSheetService: AnswerSheetService

    private val mockUser = CurrentUser(
        userId = 5L,
        username = "user01",
        displayName = "User",
        tenantId = 1L,
        groupId = 10L,
        roles = setOf("USER"),
        permissions = emptySet()
    )

    private val sampleAnswers = listOf(
        AnswerItemRequest(questionId = 1L, optionId = 11L, answerText = null),
        AnswerItemRequest(questionId = 2L, optionId = 12L, answerText = null)
    )

    // ── getTaskQuestions ──────────────────────────────────────────────────────

    @Test
    fun `getTaskQuestions throws TASK_FORBIDDEN when not assigned`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(99L, 5L, 10L)).thenReturn(false)

        val ex = assertThrows<BizException> { answerSheetService.getTaskQuestions(99L) }
        assertEquals("TASK_FORBIDDEN", ex.code)
        verify(answerSheetRepository, never()).findTaskQuestionPayload(anyLong())
    }

    @Test
    fun `getTaskQuestions throws TASK_NOT_FOUND when payload is null`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L)).thenReturn(null)

        val ex = assertThrows<BizException> { answerSheetService.getTaskQuestions(1L) }
        assertEquals("TASK_NOT_FOUND", ex.code)
    }

    @Test
    fun `getTaskQuestions returns payload on success`() {
        val payload = TaskQuestionPayload(taskId = 1L, scaleId = 2L, scaleName = "PHQ-9", questions = emptyList())
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L)).thenReturn(payload)

        val result = answerSheetService.getTaskQuestions(1L)

        assertEquals(1L, result.taskId)
        assertEquals("PHQ-9", result.scaleName)
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    fun `save throws TASK_FORBIDDEN when not assigned`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))
        }
        assertEquals("TASK_FORBIDDEN", ex.code)
        verify(answerSheetRepository, never()).createAnswerSheet(anyLong(), anyLong(), anyLong(), anyString())
    }

    @Test
    fun `save creates new answer sheet when no draft exists`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheet(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createAnswerSheet(1L, 2L, 5L, "DRAFT")).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(BigDecimal("10"))

        val result = answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals(100L, result["answerSheetId"])
        assertEquals("DRAFT", result["status"])
        verify(answerSheetRepository).createAnswerSheet(1L, 2L, 5L, "DRAFT")
        verify(answerSheetRepository).replaceAnswerItems(100L, sampleAnswers)
    }

    @Test
    fun `save reuses existing draft answer sheet`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheet(1L, 5L)).thenReturn(77L)
        `when`(answerSheetRepository.replaceAnswerItems(77L, sampleAnswers)).thenReturn(BigDecimal.ZERO)

        val result = answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals(77L, result["answerSheetId"])
        verify(answerSheetRepository, never()).createAnswerSheet(anyLong(), anyLong(), anyLong(), anyString())
        verify(answerSheetRepository).replaceAnswerItems(77L, sampleAnswers)
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    fun `submit throws TASK_FORBIDDEN when not assigned`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            answerSheetService.submit(SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))
        }
        assertEquals("TASK_FORBIDDEN", ex.code)
    }

    @Test
    fun `submit creates report and sends REPORT_GENERATED notification for non-NORMAL risk`() {
        val totalScore = BigDecimal("15")
        // expected strings derived from service buildString logic
        val expectedResultSummary = "总分：15；风险等级：MODERATE；结果标题：中度风险"
        val expectedReportContent = "系统自动报告\n总分：15\n风险等级：MODERATE\n建议咨询"
        val expectedWarningReason = "系统根据量表计分规则自动识别出风险等级：MODERATE"

        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheet(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createAnswerSheet(1L, 2L, 5L, "DRAFT")).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(totalScore)
        `when`(answerSheetRepository.resolveRisk(2L, totalScore))
            .thenReturn(Triple("MODERATE", "中度风险", "建议咨询"))
        `when`(answerSheetRepository.createResult(100L, totalScore, "MODERATE", true, expectedResultSummary))
            .thenReturn(200L)
        `when`(answerSheetRepository.createReport(200L, 5L, "中度风险", expectedReportContent))
            .thenReturn(300L)

        val result = answerSheetService.submit(SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals(100L, result.answerSheetId)
        assertEquals(200L, result.resultId)
        assertEquals(300L, result.reportId)
        assertEquals("MODERATE", result.riskLevel)

        verify(answerSheetRepository).updateAnswerSheetStatus(100L, "SUBMITTED")
        verify(notificationDispatchService).notifyUsers(
            notificationType = "REPORT_GENERATED",
            title = "系统报告已生成",
            content = "你的测评已提交，系统报告现在可以查看。",
            bizType = "REPORT",
            bizId = 300L,
            targetPath = "/reports/300?resultId=200&taskId=1&notificationSource=REPORT_GENERATED",
            payloadJson = """{"reportId":300,"resultId":200,"taskId":1,"riskLevel":"MODERATE"}""",
            receiverUserIds = listOf(5L)
        )
        verify(answerSheetRepository).createWarningIfNeeded(200L, "MODERATE", expectedWarningReason)
    }

    @Test
    fun `submit uses warningFlag false and fallback title when riskLevel is NORMAL`() {
        val totalScore = BigDecimal("5")
        val expectedResultSummary = "总分：5；风险等级：NORMAL；结果标题：正常"
        val expectedReportContent = "系统自动报告\n总分：5\n风险等级：NORMAL\n无风险"
        val expectedWarningReason = "系统根据量表计分规则自动识别出风险等级：NORMAL"

        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheet(1L, 5L)).thenReturn(50L)
        `when`(answerSheetRepository.replaceAnswerItems(50L, sampleAnswers)).thenReturn(totalScore)
        `when`(answerSheetRepository.resolveRisk(2L, totalScore))
            .thenReturn(Triple("NORMAL", "正常", "无风险"))
        `when`(answerSheetRepository.createResult(50L, totalScore, "NORMAL", false, expectedResultSummary))
            .thenReturn(201L)
        `when`(answerSheetRepository.createReport(201L, 5L, "正常", expectedReportContent))
            .thenReturn(301L)

        val result = answerSheetService.submit(SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals("NORMAL", result.riskLevel)
        verify(answerSheetRepository).createResult(50L, totalScore, "NORMAL", false, expectedResultSummary)
        verify(answerSheetRepository).createWarningIfNeeded(201L, "NORMAL", expectedWarningReason)
    }

    @Test
    fun `submit uses system report fallback title when resolved title is null`() {
        val totalScore = BigDecimal("0")
        val expectedResultSummary = "总分：0；风险等级：NORMAL"
        val expectedReportContent = "系统自动报告\n总分：0\n风险等级：NORMAL\n"

        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheet(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createAnswerSheet(1L, 2L, 5L, "DRAFT")).thenReturn(55L)
        `when`(answerSheetRepository.replaceAnswerItems(55L, sampleAnswers)).thenReturn(totalScore)
        `when`(answerSheetRepository.resolveRisk(2L, totalScore))
            .thenReturn(Triple("NORMAL", null, null))
        `when`(answerSheetRepository.createResult(55L, totalScore, "NORMAL", false, expectedResultSummary))
            .thenReturn(202L)
        // title falls back to "系统报告" when resolved.second is null
        `when`(answerSheetRepository.createReport(202L, 5L, "系统报告", expectedReportContent))
            .thenReturn(302L)

        val result = answerSheetService.submit(SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals(302L, result.reportId)
        verify(answerSheetRepository).createReport(202L, 5L, "系统报告", expectedReportContent)
    }
}
