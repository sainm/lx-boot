package org.sainm.psy.assessment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.assessment.api.AnswerItemRequest
import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.assessment.domain.AnswerSheetRescoreContext
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskDraftAnswerItem
import org.sainm.psy.assessment.domain.TaskQuestionItem
import org.sainm.psy.assessment.domain.TaskQuestionOption
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.domain.TaskSkipRule
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AnswerSheetServiceTest {

    @Mock private lateinit var answerSheetRepository: AnswerSheetRepository
    @Mock private lateinit var scoreCalculator: ScoreCalculator
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private lateinit var answerSheetService: AnswerSheetService
    private lateinit var messages: LocalizedMessages

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        messages = LocalizedMessages(messageSource)
        answerSheetService = AnswerSheetService(
            answerSheetRepository = answerSheetRepository,
            scoreCalculator = scoreCalculator,
            currentUserFacade = currentUserFacade,
            notificationDispatchService = notificationDispatchService,
            securityAuditService = securityAuditService,
            messages = messages,
            tenantAccessPolicy = tenantAccessPolicy
        )
    }

    private val mockUser = UserPrincipal(
        userId = 5L,
        username = "user01",
        displayName = "User",
        status = UserStatus.ENABLED,
        tenantId = 1L,
        groupId = 10L,
        roles = setOf("USER"),
        permissions = emptySet()
    )

    private val sampleAnswers = listOf(
        AnswerItemRequest(questionId = 1L, optionId = 11L, answerText = null),
        AnswerItemRequest(questionId = 2L, optionId = 12L, answerText = null)
    )

    private val sampleOptionScoreMap = mapOf(
        11L to BigDecimal("4"),
        12L to BigDecimal("6")
    )

    private fun sampleTaskPayload(
        questionTypeById: Map<Long, String> = mapOf(1L to "SINGLE_CHOICE", 2L to "SINGLE_CHOICE"),
        allowRetakeFlag: Boolean = false,
        draftAnswers: List<TaskDraftAnswerItem> = emptyList(),
        skipRules: List<TaskSkipRule> = emptyList()
    ) =
        TaskQuestionPayload(
            taskId = 1L,
            scaleId = 2L,
            scaleName = "PHQ-9",
            allowSaveFlag = true,
            allowRetakeFlag = allowRetakeFlag,
            draftAnswerSheetId = null,
            draftVersionNo = null,
            draftAnswers = draftAnswers,
            skipRules = skipRules,
            questions = listOf(
                TaskQuestionItem(
                    questionId = 1L,
                    questionNo = 1,
                    questionTitle = "Question 1",
                    questionType = questionTypeById[1L] ?: "SINGLE_CHOICE",
                    requiredFlag = true,
                    optionSelectionLimit = 2,
                    sliderMin = BigDecimal.ZERO,
                    sliderMax = BigDecimal.TEN,
                    sliderStep = BigDecimal.ONE,
                    options = listOf(
                        TaskQuestionOption(11L, "A", "Option A", BigDecimal("4"), false),
                        TaskQuestionOption(21L, "X", "Option X", BigDecimal("0"), false)
                    )
                ),
                TaskQuestionItem(
                    questionId = 2L,
                    questionNo = 2,
                    questionTitle = "Question 2",
                    questionType = questionTypeById[2L] ?: "SINGLE_CHOICE",
                    requiredFlag = true,
                    optionSelectionLimit = 2,
                    sliderMin = BigDecimal.ZERO,
                    sliderMax = BigDecimal.TEN,
                    sliderStep = BigDecimal.ONE,
                    options = listOf(
                        TaskQuestionOption(12L, "B", "Option B", BigDecimal("6"), false),
                        TaskQuestionOption(22L, "C", "Option C", BigDecimal("2"), false)
                    )
                )
            )
        )

    @Test
    fun `getTaskQuestions throws TASK_FORBIDDEN when not assigned`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(99L, 5L, 10L)).thenReturn(false)

        val ex = assertThrows<BizException> { answerSheetService.getTaskQuestions(99L) }

        assertEquals("TASK_FORBIDDEN", ex.code)
        verify(answerSheetRepository, never()).findTaskQuestionPayload(anyLong(), anyLong())
    }

    @Test
    fun `getTaskQuestions throws TASK_NOT_FOUND when payload is null`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(null)

        val ex = assertThrows<BizException> { answerSheetService.getTaskQuestions(1L) }

        assertEquals("TASK_NOT_FOUND", ex.code)
    }

    @Test
    fun `getTaskQuestions returns payload on success`() {
        val payload = TaskQuestionPayload(
            taskId = 1L,
            scaleId = 2L,
            scaleName = "PHQ-9",
            allowSaveFlag = true,
            draftAnswerSheetId = 88L,
            draftVersionNo = 3,
            draftAnswers = listOf(
                TaskDraftAnswerItem(questionId = 1L, optionId = 11L),
                TaskDraftAnswerItem(questionId = 2L, answerText = "draft note")
            ),
            questions = emptyList()
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(payload)

        val result = answerSheetService.getTaskQuestions(1L)

        assertEquals(88L, result.draftAnswerSheetId)
        assertEquals(3, result.draftVersionNo)
        assertEquals(2, result.draftAnswers.size)
        assertEquals(11L, result.draftAnswers.first().optionId)
    }

    @Test
    fun `getTaskQuestions keeps questions answerable when retake is allowed`() {
        val payload = sampleTaskPayload(
            allowRetakeFlag = true,
            draftAnswers = listOf(TaskDraftAnswerItem(questionId = 1L, optionId = 11L))
        )
        val submittedReport = AnswerSheetRepository.SubmittedTaskReportInfo(
            reportId = 301L,
            resultId = 201L,
            riskLevel = "MODERATE"
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(payload)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(true)
        `when`(answerSheetRepository.findLatestSubmittedTaskReport(1L, 5L)).thenReturn(submittedReport)

        val result = answerSheetService.getTaskQuestions(1L)

        assertEquals(false, result.completedFlag)
        assertEquals(201L, result.completedResultId)
        assertEquals(301L, result.completedReportId)
        assertEquals(1, result.draftAnswers.size)
        assertEquals(2, result.questions.size)
    }

    @Test
    fun `getTaskQuestions clears draft answers when submitted task cannot retake`() {
        val payload = sampleTaskPayload(
            draftAnswers = listOf(TaskDraftAnswerItem(questionId = 1L, optionId = 11L))
        )
        val submittedReport = AnswerSheetRepository.SubmittedTaskReportInfo(
            reportId = 301L,
            resultId = 201L,
            riskLevel = "MODERATE"
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(payload)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(true)
        `when`(answerSheetRepository.findLatestSubmittedTaskReport(1L, 5L)).thenReturn(submittedReport)

        val result = answerSheetService.getTaskQuestions(1L)

        assertEquals(true, result.completedFlag)
        assertEquals(null, result.draftAnswerSheetId)
        assertEquals(null, result.draftVersionNo)
        assertEquals(emptyList<TaskDraftAnswerItem>(), result.draftAnswers)
        assertEquals(emptyList<TaskQuestionItem>(), result.questions)
    }

    @Test
    fun `save creates new answer sheet when no draft exists`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(100L, 1)).thenReturn(2)

        val result = answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals(100L, result.answerSheetId)
        assertEquals("DRAFT", result.status)
        assertEquals(2, result.versionNo)
    }

    @Test
    fun `save reuses existing draft and increments version`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 77L, versionNo = 4)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)
        `when`(answerSheetRepository.replaceAnswerItems(77L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(77L, 4)).thenReturn(5)

        val result = answerSheetService.save(
            SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answerSheetId = 77L, versionNo = 4, answers = sampleAnswers)
        )

        assertEquals(77L, result.answerSheetId)
        assertEquals(5, result.versionNo)
        inOrder(answerSheetRepository).apply {
            verify(answerSheetRepository).incrementDraftVersion(77L, 4)
            verify(answerSheetRepository).replaceAnswerItems(77L, sampleAnswers)
        }
        verify(answerSheetRepository, never()).createDraftAnswerSheetIfAbsent(anyLong(), anyLong(), anyLong())
    }

    @Test
    fun `save rejects an existing draft when optimistic lock metadata is missing`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 77L, versionNo = 4)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)

        val ex = assertThrows<BizException> {
            answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))
        }

        assertEquals("ANSWER_SHEET_VERSION_CONFLICT", ex.code)
        verify(answerSheetRepository, never()).incrementDraftVersion(anyLong(), org.mockito.ArgumentMatchers.any())
        verify(answerSheetRepository, never()).replaceAnswerItems(anyLong(), org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `save throws version conflict when draft version mismatches`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 77L, versionNo = 4)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)

        val ex = assertThrows<BizException> {
            answerSheetService.save(
                SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answerSheetId = 77L, versionNo = 3, answers = sampleAnswers)
            )
        }

        assertEquals("ANSWER_SHEET_VERSION_CONFLICT", ex.code)
        verify(answerSheetRepository, never()).incrementDraftVersion(anyLong(), org.mockito.ArgumentMatchers.any())
        verify(answerSheetRepository, never()).replaceAnswerItems(anyLong(), org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `save allows new draft after submission when retake is enabled`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload(allowRetakeFlag = true))
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(101L)
        `when`(answerSheetRepository.replaceAnswerItems(101L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(101L, 1)).thenReturn(2)

        val result = answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))

        assertEquals(101L, result.answerSheetId)
        assertEquals(2, result.versionNo)
    }

    @Test
    fun `save allows incomplete draft when required questions are still missing`() {
        val partialAnswers = listOf(AnswerItemRequest(questionId = 1L, optionId = 11L))
        val partialScoreMap = mapOf(11L to BigDecimal("4"))
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(102L)
        `when`(answerSheetRepository.replaceAnswerItems(102L, partialAnswers)).thenReturn(partialScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(102L, 1)).thenReturn(2)

        val result = answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = partialAnswers))

        assertEquals(102L, result.answerSheetId)
        assertEquals(2, result.versionNo)
    }

    @Test
    fun `submit returns existing result when submit token is retried`() {
        val existing = AnswerSubmitResult(
            answerSheetId = 66L,
            resultId = 201L,
            reportId = 301L,
            riskLevel = "MODERATE",
            versionNo = 5
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findSubmittedResultBySubmitToken(1L, 5L, "token-1")).thenReturn(existing)

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-1", answers = sampleAnswers)
        )

        assertEquals(301L, result.reportId)
        verify(answerSheetRepository, never()).replaceAnswerItems(anyLong(), org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `submit creates report and sends REPORT_GENERATED notification for non-NORMAL risk`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-2", 1)).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        val expectedSummary = messages.get("report.result.summary.with_title", "15", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "15")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(answerSheetRepository.createResult(100L, BigDecimal("15"), "MODERATE", true, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true)).thenReturn(
            ScoreResult(
                totalScore = BigDecimal("15"),
                riskLevel = "MODERATE",
                resultTitle = "Moderate risk",
                resultDescription = null,
                suggestionText = "Need counseling",
                dimensionScores = emptyList()
            )
        )

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-2", answers = sampleAnswers)
        )

        assertEquals(100L, result.answerSheetId)
        assertEquals("MODERATE", result.riskLevel)
        verify(notificationDispatchService).notifyReportGenerated(301L, 201L, 1L, "MODERATE", false, listOf(5L))
    }

    @Test
    fun `submit still succeeds when notification dispatch fails after warning is created`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-safe", 1)).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        val expectedSummary = messages.get("report.result.summary.with_title", "15", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "15")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(answerSheetRepository.createResult(100L, BigDecimal("15"), "MODERATE", true, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true)).thenReturn(
            ScoreResult(
                totalScore = BigDecimal("15"),
                riskLevel = "MODERATE",
                resultTitle = "Moderate risk",
                resultDescription = null,
                suggestionText = "Need counseling",
                dimensionScores = emptyList()
            )
        )
        doThrow(RuntimeException("notify failed")).`when`(notificationDispatchService).notifyReportGenerated(301L, 201L, 1L, "MODERATE", false, listOf(5L))

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-safe", answers = sampleAnswers)
        )

        assertEquals(100L, result.answerSheetId)
        assertEquals(201L, result.resultId)
        assertEquals(301L, result.reportId)
        verify(answerSheetRepository).createWarningIfNeeded(
            201L,
            "MODERATE",
            "MODERATE",
            messages.get("warning.auto.reason", "MODERATE")
        )
    }

    @Test
    fun `submit fails when warning creation fails for risky result`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-warning", 1)).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        val expectedSummary = messages.get("report.result.summary.with_title", "15", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "15")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(answerSheetRepository.createResult(100L, BigDecimal("15"), "MODERATE", true, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true)).thenReturn(
            ScoreResult(
                totalScore = BigDecimal("15"),
                riskLevel = "MODERATE",
                resultTitle = "Moderate risk",
                resultDescription = null,
                suggestionText = "Need counseling",
                dimensionScores = emptyList()
            )
        )
        `when`(
            answerSheetRepository.createWarningIfNeeded(
                201L,
                "MODERATE",
                "MODERATE",
                messages.get("warning.auto.reason", "MODERATE")
            )
        ).thenThrow(RuntimeException("warning failed"))

        val ex = assertThrows<BizException> {
            answerSheetService.submit(
                SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-warning", answers = sampleAnswers)
            )
        }

        assertEquals("WARNING_CREATE_FAILED", ex.code)
        verify(notificationDispatchService, never()).notifyReportGenerated(301L, 201L, 1L, "MODERATE", false, listOf(5L))
    }

    @Test
    fun `submit throws version conflict when draft changed elsewhere`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 50L, versionNo = 4)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)

        val ex = assertThrows<BizException> {
            answerSheetService.submit(
                SubmitAnswerSheetRequest(
                    taskId = 1L,
                    scaleId = 2L,
                    answerSheetId = 50L,
                    versionNo = 3,
                    submitToken = "token-3",
                    answers = sampleAnswers
                )
            )
        }

        assertEquals("ANSWER_SHEET_VERSION_CONFLICT", ex.code)
        verify(answerSheetRepository, never()).submitDraftAnswerSheet(anyLong(), anyString(), org.mockito.ArgumentMatchers.any())
        verify(answerSheetRepository, never()).replaceAnswerItems(anyLong(), org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `submit returns existing result when submit token unique index is hit concurrently`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 50L, versionNo = 4)
        val existing = AnswerSubmitResult(
            answerSheetId = 66L,
            resultId = 201L,
            reportId = 301L,
            riskLevel = "MODERATE",
            versionNo = 5
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findSubmittedResultBySubmitToken(1L, 5L, "token-unique")).thenReturn(null, existing)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)
        `when`(answerSheetRepository.submitDraftAnswerSheet(50L, "token-unique", 4)).thenReturn(0)

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(
                taskId = 1L,
                scaleId = 2L,
                answerSheetId = 50L,
                versionNo = 4,
                submitToken = "token-unique",
                answers = sampleAnswers
            )
        )

        assertEquals(301L, result.reportId)
    }

    @Test
    fun `autoSubmitOverdueDrafts submits eligible drafts`() {
        val scanTime = LocalDateTime.of(2026, 4, 12, 10, 0)
        val overdueDraft = AnswerSheetRepository.OverdueDraftAnswerSheet(
            answerSheetId = 88L,
            taskId = 1L,
            scaleId = 2L,
            userId = 5L,
            responseLocaleCode = "ja-JP"
        )
        `when`(answerSheetRepository.findOverdueDraftAnswerSheets(scanTime)).thenReturn(listOf(overdueDraft))
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(
            AnswerSheetRepository.DraftAnswerSheetInfo(88L, 4, "ja-JP")
        )
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.loadAnswerItems(88L)).thenReturn(sampleAnswers)
        `when`(answerSheetRepository.replaceAnswerItems(88L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheetWithLocale(eq(88L), anyString(), eq(4), anyString())).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        val expectedSummary = messages.getForLocale("ja-JP", "report.result.summary.with_title", "12", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "12")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(answerSheetRepository.createResult(88L, BigDecimal("12"), "MODERATE", true, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true, "ja-JP")).thenReturn(
            ScoreResult(
                totalScore = BigDecimal("12"),
                riskLevel = "MODERATE",
                resultTitle = "Moderate risk",
                resultDescription = null,
                suggestionText = "Need counseling",
                dimensionScores = emptyList()
            )
        )

        val submittedCount = answerSheetService.autoSubmitOverdueDrafts(scanTime)

        assertEquals(1, submittedCount)
        verify(notificationDispatchService).notifyReportGenerated(301L, 201L, 1L, "MODERATE", true, listOf(5L))
    }

    @Test
    fun `cleanupExpiredDrafts deletes drafts older than retention cutoff`() {
        answerSheetService = AnswerSheetService(
            answerSheetRepository = answerSheetRepository,
            scoreCalculator = scoreCalculator,
            currentUserFacade = currentUserFacade,
            notificationDispatchService = notificationDispatchService,
            securityAuditService = securityAuditService,
            messages = messages,
            tenantAccessPolicy = tenantAccessPolicy,
            draftRetentionDays = 7
        )
        val now = LocalDateTime.of(2026, 4, 13, 12, 0)
        val cutoff = LocalDateTime.of(2026, 4, 6, 12, 0)
        `when`(answerSheetRepository.deleteDraftAnswerSheetsUpdatedBefore(cutoff)).thenReturn(3)

        val deletedCount = answerSheetService.cleanupExpiredDrafts(now)

        assertEquals(3, deletedCount)
        verify(answerSheetRepository).deleteDraftAnswerSheetsUpdatedBefore(cutoff)
    }

    @Test
    fun `submit throws TASK_ALREADY_SUBMITTED when task already submitted`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(true)

        val ex = assertThrows<BizException> {
            answerSheetService.submit(SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))
        }

        assertEquals("TASK_ALREADY_SUBMITTED", ex.code)
    }

    @Test
    fun `submit allows resubmission when retake is enabled`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload(allowRetakeFlag = true))
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(true)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-retake", 1)).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        val expectedSummary = messages.get("report.result.summary.with_title", "15", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "15")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(answerSheetRepository.createResult(100L, BigDecimal("15"), "MODERATE", true, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true)).thenReturn(
            ScoreResult(
                totalScore = BigDecimal("15"),
                riskLevel = "MODERATE",
                resultTitle = "Moderate risk",
                resultDescription = null,
                suggestionText = "Need counseling",
                dimensionScores = emptyList()
            )
        )

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-retake", answers = sampleAnswers)
        )

        assertEquals(201L, result.resultId)
        assertEquals(301L, result.reportId)
    }

    @Test
    fun `submit accepts multi select answers and delegates grouped scoring`() {
        val multiAnswers = listOf(
            AnswerItemRequest(questionId = 1L, optionId = 11L),
            AnswerItemRequest(questionId = 1L, optionId = 21L),
            AnswerItemRequest(questionId = 2L, optionId = 12L)
        )
        val multiScoreMap = mapOf(11L to BigDecimal("4"), 21L to BigDecimal.ZERO, 12L to BigDecimal("6"))
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload(mapOf(1L to "MULTI_SELECT", 2L to "SINGLE_CHOICE")))
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, multiAnswers)).thenReturn(multiScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-ms", 1)).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        val scoringContexts = listOf(
            QuestionScoreContext(1L, null, false, BigDecimal.ONE, BigDecimal("4")),
            QuestionScoreContext(2L, null, false, BigDecimal.ONE, BigDecimal("6"))
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, multiAnswers, multiScoreMap)).thenReturn(scoringContexts)
        val expectedSummary = messages.get("report.result.summary.with_title", "10", "NORMAL", "Normal")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "10")).append("\n")
            append(messages.get("report.auto.risk", "NORMAL")).append("\n")
            append("Stay stable")
        }
        `when`(answerSheetRepository.createResult(100L, BigDecimal("10"), "NORMAL", false, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, scoringContexts, null, true)).thenReturn(
            ScoreResult(BigDecimal("10"), "NORMAL", "Normal", null, "Stay stable", emptyList())
        )

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-ms", answers = multiAnswers)
        )

        assertEquals(201L, result.resultId)
    }

    @Test
    fun `submit excludes a triggered skipped required question from validation persistence and scoring`() {
        val submittedAnswers = listOf(
            AnswerItemRequest(questionId = 1L, optionId = 11L),
            AnswerItemRequest(questionId = 2L, optionId = 12L)
        )
        val effectiveAnswers = listOf(AnswerItemRequest(questionId = 1L, optionId = 11L))
        val activeQuestionIds = setOf(1L)
        val scoreMap = mapOf(11L to BigDecimal("4"))
        val payload = sampleTaskPayload(
            skipRules = listOf(TaskSkipRule(whenQuestionNo = 1, whenOptionCode = "A", skipQuestionNos = listOf(2)))
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(payload)
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(100L)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-skip", 1)).thenReturn(1)
        `when`(answerSheetRepository.replaceAnswerItems(100L, effectiveAnswers)).thenReturn(scoreMap)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L, activeQuestionIds)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext(
                "SIMPLE_SUM", BigDecimal.ONE, null, null, totalQuestionCount = 1, totalWeight = BigDecimal.ONE
            ) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, effectiveAnswers, scoreMap, activeQuestionIds)).thenReturn(emptyList())
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true)).thenReturn(
            ScoreResult(BigDecimal("4"), "NORMAL", "Normal", null, null, emptyList())
        )
        val expectedSummary = messages.get("report.result.summary.with_title", "4", "NORMAL", "Normal")
        `when`(answerSheetRepository.createResult(100L, BigDecimal("4"), "NORMAL", false, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-skip", answers = submittedAnswers)
        )

        assertEquals(201L, result.resultId)
        verify(answerSheetRepository).replaceAnswerItems(100L, effectiveAnswers)
        verify(answerSheetRepository).loadScaleScoringContext(2L, 5L, activeQuestionIds)
        verify(answerSheetRepository).loadQuestionScoringMeta(2L, effectiveAnswers, scoreMap, activeQuestionIds)
    }

    @Test
    fun `save accepts a valid TIME answer in canonical HH mm format`() {
        val answers = listOf(
            AnswerItemRequest(questionId = 1L, answerText = "23:30"),
            AnswerItemRequest(questionId = 2L, optionId = 12L)
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(
            sampleTaskPayload(mapOf(1L to "TIME", 2L to "SINGLE_CHOICE"))
        )
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createDraftAnswerSheetIfAbsent(1L, 2L, 5L)).thenReturn(77L)
        `when`(answerSheetRepository.incrementDraftVersion(77L, 1)).thenReturn(2)

        val result = answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = answers))

        assertEquals(77L, result.answerSheetId)
        verify(answerSheetRepository).replaceAnswerItems(77L, answers)
    }

    @Test
    fun `save rejects a TIME answer outside canonical HH mm format`() {
        val answers = listOf(
            AnswerItemRequest(questionId = 1L, answerText = "25:99"),
            AnswerItemRequest(questionId = 2L, optionId = 12L)
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(
            sampleTaskPayload(mapOf(1L to "TIME", 2L to "SINGLE_CHOICE"))
        )

        val error = assertThrows<BizException> {
            answerSheetService.save(SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = answers))
        }

        assertEquals("ANSWER_TIME_INVALID", error.code)
        verify(answerSheetRepository, never()).createDraftAnswerSheetIfAbsent(anyLong(), anyLong(), anyLong())
    }

    @Test
    fun `submit rejects slider answer outside range`() {
        val sliderAnswers = listOf(
            AnswerItemRequest(questionId = 1L, answerValue = BigDecimal("11")),
            AnswerItemRequest(questionId = 2L, optionId = 12L)
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload(mapOf(1L to "SLIDER", 2L to "SINGLE_CHOICE")))

        val ex = assertThrows<BizException> {
            answerSheetService.submit(
                SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-slider", answers = sliderAnswers)
            )
        }

        assertEquals("ANSWER_SLIDER_OUT_OF_RANGE", ex.code)
        verify(answerSheetRepository, never()).createDraftAnswerSheetIfAbsent(anyLong(), anyLong(), anyLong())
    }

    @Test
    fun `submit enforces the configured missing-answer ratio before creating a draft`() {
        val partialAnswers = listOf(AnswerItemRequest(questionId = 1L, optionId = 11L))
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.loadScaleQualityPolicy(2L)).thenReturn(
            org.sainm.psy.scale.domain.ScalePackageQualityPolicy(
                missingAnswerPolicy = "ALLOW",
                maxMissingRatio = BigDecimal("0.25"),
                requireAllRequiredAnswers = false
            )
        )

        val ex = assertThrows<BizException> {
            answerSheetService.submit(
                SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "quality-ratio", answers = partialAnswers)
            )
        }

        assertEquals("ANSWER_QUALITY_INVALID", ex.code)
        verify(answerSheetRepository, never()).createDraftAnswerSheetIfAbsent(anyLong(), anyLong(), anyLong())
    }

    @Test
    fun `rescoreResult appends calculation version, preserves previous result, and creates report`() {
        val context = AnswerSheetRescoreContext(
            answerSheetId = 88L,
            taskId = 1L,
            scaleId = 2L,
            userId = 5L,
            resultId = 201L,
            previousRiskLevel = "NORMAL",
            calculationVersion = 1
        )
        val dimensionScores = listOf(DimensionScoreResult(1L, BigDecimal("6.5000"), "MODERATE", "Anxiety"))
        val scored = ScoreResult(
            totalScore = BigDecimal("12"),
            riskLevel = "MODERATE",
            resultTitle = "Moderate risk",
            resultDescription = null,
            suggestionText = "Need counseling",
            dimensionScores = dimensionScores
        )
        val expectedSummary = messages.get("report.result.summary.with_title", "12", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "12")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser.copy(userId = 99L, tenantId = 7L))
        `when`(tenantAccessPolicy.currentTenantFilter("ASSESSMENT_RESULT", "RESCORE")).thenReturn(7L)
        `when`(answerSheetRepository.findRescoreContextByResultId(201L, 7L)).thenReturn(context)
        `when`(answerSheetRepository.loadAnswerItems(88L)).thenReturn(sampleAnswers)
        `when`(answerSheetRepository.loadOptionScoreMap(sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null, true)).thenReturn(scored)
        `when`(answerSheetRepository.findDimensionReportMeta(listOf(1L))).thenReturn(
            mapOf(1L to AnswerSheetRepository.DimensionReportMeta(1L, "ANX", "Anxiety", 1))
        )
        `when`(
            answerSheetRepository.createRescoreResult(
                201L, 99L, BigDecimal("12"), "MODERATE", true, expectedSummary,
                "RAW_SCORE", null, null, null, null, false, null
            )
        ).thenReturn(202L)
        `when`(answerSheetRepository.createReport(anyLong(), anyLong(), anyString(), anyString())).thenReturn(301L)

        val result = answerSheetService.rescoreResult(201L)

        assertEquals(202L, result.resultId)
        assertEquals(201L, result.previousResultId)
        assertEquals(2, result.calculationVersion)
        assertEquals(301L, result.reportId)
        assertEquals("MODERATE", result.riskLevel)
        assertEquals("NORMAL", result.previousRiskLevel)
        verify(answerSheetRepository).createRescoreResult(
            201L, 99L, BigDecimal("12"), "MODERATE", true, expectedSummary,
            "RAW_SCORE", null, null, null, null, false, null
        )
        verify(answerSheetRepository).saveDimensionScores(202L, dimensionScores)
        verify(answerSheetRepository, never()).replaceAnswerItems(88L, sampleAnswers)
        verify(answerSheetRepository).createWarningIfNeeded(202L, "MODERATE", "MODERATE", messages.get("warning.auto.reason", "MODERATE"))
        verify(notificationDispatchService).notifyReportGenerated(301L, 202L, 1L, "MODERATE", false, listOf(5L))
        verify(securityAuditService).recordAssessmentResultRescored(88L, 201L, 202L, 301L, "NORMAL", "MODERATE")
        verify(tenantAccessPolicy).currentTenantFilter("ASSESSMENT_RESULT", "RESCORE")
    }

    @Test
    fun `rescoreResult throws RESULT_NOT_FOUND when result context is missing`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser.copy(userId = 99L, tenantId = 7L))
        `when`(tenantAccessPolicy.currentTenantFilter("ASSESSMENT_RESULT", "RESCORE")).thenReturn(7L)
        `when`(answerSheetRepository.findRescoreContextByResultId(404L, 7L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            answerSheetService.rescoreResult(404L)
        }

        assertEquals("RESULT_NOT_FOUND", ex.code)
        verify(answerSheetRepository, never()).loadAnswerItems(anyLong())
    }

    @Test
    fun `rescoreResult rejects tenantless non-global staff before querying a result`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(
            mockUser.copy(userId = 99L, tenantId = null, roles = setOf("ASSESSMENT_ADMIN"))
        )
        `when`(tenantAccessPolicy.currentTenantFilter("ASSESSMENT_RESULT", "RESCORE")).thenThrow(
            BizException("TENANT_CONTEXT_REQUIRED", messages.get("tenant.context.required"))
        )

        val ex = assertThrows<BizException> {
            answerSheetService.rescoreResult(201L)
        }

        assertEquals("TENANT_CONTEXT_REQUIRED", ex.code)
        verify(answerSheetRepository, never()).findRescoreContextByResultId(anyLong(), org.mockito.ArgumentMatchers.nullable(Long::class.java))
    }

    @Test
    fun `rescoreResult lets audited global scope perform an unfiltered lookup`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(
            mockUser.copy(userId = 99L, tenantId = null, roles = setOf("SUPER_ADMIN"))
        )
        `when`(tenantAccessPolicy.currentTenantFilter("ASSESSMENT_RESULT", "RESCORE")).thenReturn(null)
        `when`(answerSheetRepository.findRescoreContextByResultId(404L, null)).thenReturn(null)

        val ex = assertThrows<BizException> {
            answerSheetService.rescoreResult(404L)
        }

        assertEquals("RESULT_NOT_FOUND", ex.code)
        verify(answerSheetRepository).findRescoreContextByResultId(404L, null)
    }
}
