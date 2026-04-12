package org.sainm.psy.assessment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.assessment.api.AnswerItemRequest
import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.assessment.domain.AnswerSheetRescoreContext
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionItem
import org.sainm.psy.assessment.domain.TaskQuestionOption
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
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
            messages = messages
        )
    }

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

    private val sampleOptionScoreMap = mapOf(
        11L to BigDecimal("4"),
        12L to BigDecimal("6")
    )

    private fun sampleTaskPayload(questionTypeById: Map<Long, String> = mapOf(1L to "SINGLE_CHOICE", 2L to "SINGLE_CHOICE")) =
        TaskQuestionPayload(
            taskId = 1L,
            scaleId = 2L,
            scaleName = "PHQ-9",
            allowSaveFlag = true,
            draftAnswerSheetId = null,
            draftVersionNo = null,
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
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
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
            questions = emptyList()
        )
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(payload)

        val result = answerSheetService.getTaskQuestions(1L)

        assertEquals(88L, result.draftAnswerSheetId)
        assertEquals(3, result.draftVersionNo)
    }

    @Test
    fun `save creates new answer sheet when no draft exists`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.isTaskAllowSave(1L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(null)
        `when`(answerSheetRepository.createAnswerSheet(1L, 2L, 5L, "DRAFT")).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(100L, null)).thenReturn(2)

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
        `when`(answerSheetRepository.isTaskAllowSave(1L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)
        `when`(answerSheetRepository.replaceAnswerItems(77L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(77L, 4)).thenReturn(5)

        val result = answerSheetService.save(
            SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answerSheetId = 77L, versionNo = 4, answers = sampleAnswers)
        )

        assertEquals(77L, result.answerSheetId)
        assertEquals(5, result.versionNo)
        verify(answerSheetRepository, never()).createAnswerSheet(anyLong(), anyLong(), anyLong(), anyString())
    }

    @Test
    fun `save throws version conflict when draft version mismatches`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 77L, versionNo = 4)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.isTaskAllowSave(1L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)
        `when`(answerSheetRepository.replaceAnswerItems(77L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.incrementDraftVersion(77L, 3)).thenReturn(0)

        val ex = assertThrows<BizException> {
            answerSheetService.save(
                SaveAnswerSheetRequest(taskId = 1L, scaleId = 2L, answerSheetId = 77L, versionNo = 3, answers = sampleAnswers)
            )
        }

        assertEquals("ANSWER_SHEET_VERSION_CONFLICT", ex.code)
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
        `when`(answerSheetRepository.createAnswerSheet(1L, 2L, 5L, "DRAFT")).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-2", null)).thenReturn(1)
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
        `when`(answerSheetRepository.createReport(201L, 5L, "Moderate risk", expectedReportContent)).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null)).thenReturn(
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
    fun `submit throws version conflict when draft changed elsewhere`() {
        val draftInfo = AnswerSheetRepository.DraftAnswerSheetInfo(answerSheetId = 50L, versionNo = 4)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.findTaskQuestionPayload(1L, 5L)).thenReturn(sampleTaskPayload())
        `when`(answerSheetRepository.findDraftAnswerSheetInfo(1L, 5L)).thenReturn(draftInfo)
        `when`(answerSheetRepository.replaceAnswerItems(50L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(50L, "token-3", 3)).thenReturn(0)
        `when`(answerSheetRepository.findSubmittedResultBySubmitToken(1L, 5L, "token-3")).thenReturn(null)

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
    }

    @Test
    fun `autoSubmitOverdueDrafts submits eligible drafts`() {
        val scanTime = LocalDateTime.of(2026, 4, 12, 10, 0)
        val overdueDraft = AnswerSheetRepository.OverdueDraftAnswerSheet(
            answerSheetId = 88L,
            taskId = 1L,
            scaleId = 2L,
            userId = 5L
        )
        `when`(answerSheetRepository.findOverdueDraftAnswerSheets(scanTime)).thenReturn(listOf(overdueDraft))
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(false)
        `when`(answerSheetRepository.loadAnswerItems(88L)).thenReturn(sampleAnswers)
        `when`(answerSheetRepository.replaceAnswerItems(88L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(eq(88L), anyString(), isNull<Int>())).thenReturn(1)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        val expectedSummary = messages.get("report.result.summary.with_title", "12", "MODERATE", "Moderate risk")
        val expectedReportContent = buildString {
            append(messages.get("report.auto.header")).append("\n")
            append(messages.get("report.auto.score", "12")).append("\n")
            append(messages.get("report.auto.risk", "MODERATE")).append("\n")
            append("Need counseling")
        }
        `when`(answerSheetRepository.createResult(88L, BigDecimal("12"), "MODERATE", true, expectedSummary)).thenReturn(201L)
        `when`(answerSheetRepository.createReport(201L, 5L, "Moderate risk", expectedReportContent)).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null)).thenReturn(
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
    fun `submit throws TASK_ALREADY_SUBMITTED when task already submitted`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(mockUser)
        `when`(answerSheetRepository.isAssignedToUser(1L, 5L, 10L)).thenReturn(true)
        `when`(answerSheetRepository.hasSubmittedAnswerSheet(1L, 5L)).thenReturn(true)

        val ex = assertThrows<BizException> {
            answerSheetService.submit(SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, answers = sampleAnswers))
        }

        assertEquals("TASK_ALREADY_SUBMITTED", ex.code)
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
        `when`(answerSheetRepository.createAnswerSheet(1L, 2L, 5L, "DRAFT")).thenReturn(100L)
        `when`(answerSheetRepository.replaceAnswerItems(100L, multiAnswers)).thenReturn(multiScoreMap)
        `when`(answerSheetRepository.submitDraftAnswerSheet(100L, "token-ms", null)).thenReturn(1)
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
        `when`(answerSheetRepository.createReport(201L, 5L, "Normal", expectedReportContent)).thenReturn(301L)
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, scoringContexts, null)).thenReturn(
            ScoreResult(BigDecimal("10"), "NORMAL", "Normal", null, "Stay stable", emptyList())
        )

        val result = answerSheetService.submit(
            SubmitAnswerSheetRequest(taskId = 1L, scaleId = 2L, submitToken = "token-ms", answers = multiAnswers)
        )

        assertEquals(201L, result.resultId)
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
        verify(answerSheetRepository, never()).createAnswerSheet(anyLong(), anyLong(), anyLong(), anyString())
    }

    @Test
    fun `rescoreResult recalculates result, replaces dimension scores, and creates report`() {
        val context = AnswerSheetRescoreContext(
            answerSheetId = 88L,
            taskId = 1L,
            scaleId = 2L,
            userId = 5L,
            resultId = 201L,
            previousRiskLevel = "NORMAL"
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
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(99L)
        `when`(answerSheetRepository.findRescoreContextByResultId(201L)).thenReturn(context)
        `when`(answerSheetRepository.loadAnswerItems(88L)).thenReturn(sampleAnswers)
        `when`(answerSheetRepository.replaceAnswerItems(88L, sampleAnswers)).thenReturn(sampleOptionScoreMap)
        `when`(answerSheetRepository.loadScaleScoringContext(2L, 5L)).thenReturn(
            AnswerSheetRepository.ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null) to null
        )
        `when`(answerSheetRepository.loadQuestionScoringMeta(2L, sampleAnswers, sampleOptionScoreMap)).thenReturn(emptyList())
        `when`(scoreCalculator.calculate(2L, "SIMPLE_SUM", BigDecimal.ONE, emptyList(), null)).thenReturn(scored)
        `when`(answerSheetRepository.createReport(201L, 99L, "Moderate risk", expectedReportContent)).thenReturn(301L)

        val result = answerSheetService.rescoreResult(201L)

        assertEquals(201L, result.resultId)
        assertEquals(301L, result.reportId)
        assertEquals("MODERATE", result.riskLevel)
        assertEquals("NORMAL", result.previousRiskLevel)
        verify(answerSheetRepository).updateResult(201L, BigDecimal("12"), "MODERATE", true, expectedSummary)
        verify(answerSheetRepository).replaceDimensionScores(201L, dimensionScores)
        verify(securityAuditService).recordAssessmentResultRescored(88L, 201L, 301L, "NORMAL", "MODERATE")
    }

    @Test
    fun `rescoreResult throws RESULT_NOT_FOUND when result context is missing`() {
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(99L)
        `when`(answerSheetRepository.findRescoreContextByResultId(404L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            answerSheetService.rescoreResult(404L)
        }

        assertEquals("RESULT_NOT_FOUND", ex.code)
        verify(answerSheetRepository, never()).loadAnswerItems(anyLong())
    }
}
