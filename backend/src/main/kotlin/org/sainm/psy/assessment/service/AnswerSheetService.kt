package org.sainm.psy.assessment.service

import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.assessment.domain.AnswerSheetDraftSaveResult
import org.sainm.psy.assessment.domain.AnswerSheetRescoreResult
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

@Service
class AnswerSheetService(
    private val answerSheetRepository: AnswerSheetRepository,
    private val scoreCalculator: ScoreCalculator,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.assessment.draft-retention-days:30}")
    private val draftRetentionDays: Long = 30
) {

    fun getTaskQuestions(taskId: Long): TaskQuestionPayload {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", messages.get("error.task_forbidden"))
        }
        if (answerSheetRepository.hasSubmittedAnswerSheet(taskId, currentUser.userId)) {
            throw BizException("TASK_ALREADY_SUBMITTED", messages.get("error.task_already_submitted"))
        }
        return answerSheetRepository.findTaskQuestionPayload(taskId, currentUser.userId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
    }

    @Transactional
    fun save(request: SaveAnswerSheetRequest): AnswerSheetDraftSaveResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(request.taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", messages.get("error.task_forbidden"))
        }
        if (answerSheetRepository.isTaskAllowSave(request.taskId) == false) {
            throw BizException("TASK_SAVE_DISABLED", messages.get("error.task_save_disabled"))
        }
        if (answerSheetRepository.hasSubmittedAnswerSheet(request.taskId, currentUser.userId)) {
            throw BizException("TASK_ALREADY_SUBMITTED", messages.get("error.task_already_submitted"))
        }
        validateAnswers(request.taskId, currentUser.userId, request.scaleId, request.answers)
        val draftInfo = answerSheetRepository.findDraftAnswerSheetInfo(request.taskId, currentUser.userId)
        request.answerSheetId?.let { expectedId ->
            if (draftInfo == null) {
                throw BizException("ANSWER_SHEET_DRAFT_NOT_FOUND", messages.get("error.answer_sheet_draft_not_found"))
            }
            if (draftInfo.answerSheetId != expectedId) {
                throw BizException("ANSWER_SHEET_DRAFT_MISMATCH", messages.get("error.answer_sheet_draft_mismatch"))
            }
        }
        val answerSheetId = draftInfo?.answerSheetId
            ?: answerSheetRepository.createAnswerSheet(request.taskId, request.scaleId, currentUser.userId, "DRAFT")
        answerSheetRepository.replaceAnswerItems(answerSheetId, request.answers)
        val versionNo = answerSheetRepository.incrementDraftVersion(
            answerSheetId = answerSheetId,
            expectedVersion = if (draftInfo != null) request.versionNo else null
        )
        if (versionNo == 0) {
            throw BizException("ANSWER_SHEET_VERSION_CONFLICT", messages.get("error.answer_sheet_version_conflict"))
        }
        return AnswerSheetDraftSaveResult(
            answerSheetId = answerSheetId,
            status = "DRAFT",
            versionNo = versionNo
        )
    }

    @Transactional
    fun submit(request: SubmitAnswerSheetRequest): AnswerSubmitResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(request.taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", messages.get("error.task_forbidden"))
        }
        request.submitToken
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                answerSheetRepository.findSubmittedResultBySubmitToken(request.taskId, currentUser.userId, token)
                    ?.let { return it }
            }
        if (answerSheetRepository.hasSubmittedAnswerSheet(request.taskId, currentUser.userId)) {
            throw BizException("TASK_ALREADY_SUBMITTED", messages.get("error.task_already_submitted"))
        }
        validateAnswers(request.taskId, currentUser.userId, request.scaleId, request.answers)
        val draftInfo = answerSheetRepository.findDraftAnswerSheetInfo(request.taskId, currentUser.userId)
        request.answerSheetId?.let { expectedId ->
            if (draftInfo == null) {
                throw BizException("ANSWER_SHEET_DRAFT_NOT_FOUND", messages.get("error.answer_sheet_draft_not_found"))
            }
            if (draftInfo.answerSheetId != expectedId) {
                throw BizException("ANSWER_SHEET_DRAFT_MISMATCH", messages.get("error.answer_sheet_draft_mismatch"))
            }
        }
        val answerSheetId = draftInfo?.answerSheetId
            ?: answerSheetRepository.createAnswerSheet(request.taskId, request.scaleId, currentUser.userId, "DRAFT")
        val optionScoreMap = answerSheetRepository.replaceAnswerItems(answerSheetId, request.answers)
        return finalizeSubmission(
            answerSheetId = answerSheetId,
            taskId = request.taskId,
            scaleId = request.scaleId,
            userId = currentUser.userId,
            answers = request.answers,
            optionScoreMap = optionScoreMap,
            autoSubmitted = false,
            expectedVersion = if (draftInfo != null) request.versionNo else null,
            submitToken = request.submitToken?.takeIf { it.isNotBlank() }
        )
    }

    @Transactional
    fun autoSubmitOverdueDrafts(): Int = autoSubmitOverdueDrafts(LocalDateTime.now())

    @Transactional
    fun autoSubmitOverdueDrafts(now: LocalDateTime): Int {
        val overdueDrafts = answerSheetRepository.findOverdueDraftAnswerSheets(now)
        var submittedCount = 0
        overdueDrafts.forEach { draft ->
            if (answerSheetRepository.hasSubmittedAnswerSheet(draft.taskId, draft.userId)) {
                return@forEach
            }
            val answers = answerSheetRepository.loadAnswerItems(draft.answerSheetId)
            val optionScoreMap = answerSheetRepository.replaceAnswerItems(draft.answerSheetId, answers)
            finalizeSubmission(
                answerSheetId = draft.answerSheetId,
                taskId = draft.taskId,
                scaleId = draft.scaleId,
                userId = draft.userId,
                answers = answers,
                optionScoreMap = optionScoreMap,
                autoSubmitted = true,
                expectedVersion = null,
                submitToken = "AUTO_SUBMIT:${draft.answerSheetId}:${now}"
            )
            submittedCount += 1
        }
        return submittedCount
    }

    @Transactional
    @Scheduled(fixedDelayString = "\${psy.assessment.draft-cleanup-scan-delay-ms:3600000}")
    fun cleanupExpiredDrafts(): Int {
        val now = LocalDateTime.now()
        val lock = schedulerLockService ?: return cleanupExpiredDrafts(now)
        val jobName = "assessment.draft-cleanup"
        val result = lock.withLock("assessment:draft-cleanup", Duration.ofMinutes(10)) {
            psyMetrics?.recordSchedulerRun(jobName) { cleanupExpiredDrafts(now) }
                ?: cleanupExpiredDrafts(now)
        }
        if (result == null) {
            psyMetrics?.recordSchedulerSkipped(jobName)
        }
        return result ?: 0
    }

    @Transactional
    fun cleanupExpiredDrafts(now: LocalDateTime): Int {
        val cutoff = now.minusDays(draftRetentionDays.coerceAtLeast(1))
        return answerSheetRepository.deleteDraftAnswerSheetsUpdatedBefore(cutoff)
    }

    @Transactional
    fun rescoreResult(resultId: Long): AnswerSheetRescoreResult {
        val operatorUserId = currentUserFacade.requireCurrentUserId()
        val context = answerSheetRepository.findRescoreContextByResultId(resultId)
            ?: throw BizException("RESULT_NOT_FOUND", messages.get("error.result_not_found"))
        val answers = answerSheetRepository.loadAnswerItems(context.answerSheetId)
        val optionScoreMap = answerSheetRepository.replaceAnswerItems(context.answerSheetId, answers)
        val scored = calculateScore(context.scaleId, context.userId, answers, optionScoreMap)
        val scoreText = scored.totalScore.stripTrailingZeros().toPlainString()
        val resultSummary = buildResultSummary(scoreText, scored.riskLevel, scored.resultTitle)

        answerSheetRepository.updateResult(
            resultId = context.resultId,
            totalScore = scored.totalScore,
            riskLevel = scored.riskLevel,
            warningFlag = scored.riskLevel != "NORMAL" || scored.highRiskTriggered,
            resultSummary = resultSummary,
            scoreSource = scored.scoreSource,
            standardScore = scored.standardScore,
            zScore = scored.zScore,
            tScore = scored.tScore,
            normCode = scored.normCode,
            highRiskFlag = scored.highRiskTriggered,
            highRiskRuleCode = scored.highRiskRuleCode
        )
        answerSheetRepository.replaceDimensionScores(context.resultId, scored.dimensionScores)
        val reportId = answerSheetRepository.createReport(
            resultId = context.resultId,
            authorUserId = operatorUserId,
            title = scored.resultTitle ?: messages.get("report.system.title"),
            content = buildReportContent(scoreText, scored.riskLevel, scored.standardScore, scored.scoreSource, scored.suggestionText, scored.resultDescription)
        )
        securityAuditService.recordAssessmentResultRescored(
            answerSheetId = context.answerSheetId,
            resultId = context.resultId,
            reportId = reportId,
            previousRiskLevel = context.previousRiskLevel,
            riskLevel = scored.riskLevel
        )
        return AnswerSheetRescoreResult(
            answerSheetId = context.answerSheetId,
            resultId = context.resultId,
            reportId = reportId,
            totalScore = scored.totalScore,
            riskLevel = scored.riskLevel,
            previousRiskLevel = context.previousRiskLevel
        )
    }

    private fun finalizeSubmission(
        answerSheetId: Long,
        taskId: Long,
        scaleId: Long,
        userId: Long,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>,
        optionScoreMap: Map<Long, BigDecimal>,
        autoSubmitted: Boolean,
        expectedVersion: Int?,
        submitToken: String?
    ): AnswerSubmitResult {
        val submitted = try {
            answerSheetRepository.submitDraftAnswerSheet(
                answerSheetId = answerSheetId,
                submitToken = submitToken,
                expectedVersion = expectedVersion
            )
        } catch (e: DuplicateKeyException) {
            submitToken?.let { token ->
                answerSheetRepository.findSubmittedResultBySubmitToken(taskId, userId, token)?.let { return it }
            }
            throw BizException("ANSWER_SHEET_VERSION_CONFLICT", messages.get("error.answer_sheet_version_conflict"))
        }
        if (submitted == 0) {
            submitToken?.let { token ->
                answerSheetRepository.findSubmittedResultBySubmitToken(taskId, userId, token)?.let { return it }
            }
            throw BizException("ANSWER_SHEET_VERSION_CONFLICT", messages.get("error.answer_sheet_version_conflict"))
        }

        val scored = calculateScore(scaleId, userId, answers, optionScoreMap)

        val totalScore = scored.totalScore
        val riskLevel = scored.riskLevel
        val scoreText = totalScore.stripTrailingZeros().toPlainString()
        val resultSummary = buildResultSummary(scoreText, riskLevel, scored.resultTitle)

        val resultId = answerSheetRepository.createResult(
            answerSheetId = answerSheetId,
            totalScore = totalScore,
            riskLevel = riskLevel,
            warningFlag = riskLevel != "NORMAL" || scored.highRiskTriggered,
            resultSummary = resultSummary,
            scoreSource = scored.scoreSource,
            standardScore = scored.standardScore,
            zScore = scored.zScore,
            tScore = scored.tScore,
            normCode = scored.normCode,
            highRiskFlag = scored.highRiskTriggered,
            highRiskRuleCode = scored.highRiskRuleCode
        )
        answerSheetRepository.saveDimensionScores(resultId, scored.dimensionScores)

        val reportContent = buildReportContent(
            scoreText = scoreText,
            riskLevel = riskLevel,
            standardScore = scored.standardScore,
            scoreSource = scored.scoreSource,
            suggestionText = scored.suggestionText,
            resultDescription = scored.resultDescription
        )
        val reportId = answerSheetRepository.createReport(
            resultId = resultId,
            authorUserId = userId,
            title = scored.resultTitle ?: messages.get("report.system.title"),
            content = reportContent
        )
        notificationDispatchService.notifyReportGenerated(
            reportId = reportId,
            resultId = resultId,
            taskId = taskId,
            riskLevel = riskLevel,
            autoSubmitted = autoSubmitted,
            receiverUserIds = listOf(userId)
        )
        answerSheetRepository.createWarningIfNeeded(
            resultId = resultId,
            riskLevel = riskLevel,
            warningLevel = scored.highRiskWarningLevel ?: riskLevel,
            reason = messages.get("warning.auto.reason", scored.highRiskWarningLevel ?: riskLevel)
        )
        return AnswerSubmitResult(
            answerSheetId = answerSheetId,
            resultId = resultId,
            reportId = reportId,
            riskLevel = riskLevel,
            versionNo = (expectedVersion ?: 0) + 1
        )
    }

    private fun calculateScore(
        scaleId: Long,
        userId: Long,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>,
        optionScoreMap: Map<Long, BigDecimal>
    ): ScoreResult {
        val (scaleContext, normContext) = answerSheetRepository.loadScaleScoringContext(scaleId, userId)
        val questionContexts = answerSheetRepository.loadQuestionScoringMeta(scaleId, answers, optionScoreMap)
        return scoreCalculator.calculate(scaleId, scaleContext.scoreMethod, scaleContext.scoreCoefficient, questionContexts, normContext)
    }

    private fun validateAnswers(
        taskId: Long,
        userId: Long,
        scaleId: Long,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        val payload = answerSheetRepository.findTaskQuestionPayload(taskId, userId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        if (payload.scaleId != scaleId) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        val questionMap = payload.questions.associateBy { it.questionId }
        val answersByQuestionId = answers.groupBy { it.questionId }

        answersByQuestionId.keys
            .filterNot(questionMap::containsKey)
            .firstOrNull()
            ?.let { throw BizException("ANSWER_QUESTION_INVALID", messages.get("error.answer_question_invalid", it)) }

        payload.questions
            .filter { it.requiredFlag && answersByQuestionId[it.questionId].isNullOrEmpty() }
            .firstOrNull()
            ?.let { throw BizException("ANSWER_REQUIRED_MISSING", messages.get("error.answer_required_missing", it.questionNo)) }

        answersByQuestionId.forEach { (questionId, questionAnswers) ->
            val question = questionMap.getValue(questionId)
            when (question.questionType) {
                "SINGLE_CHOICE" -> validateSingleChoice(question, questionAnswers)
                "MULTI_SELECT" -> validateMultiSelect(question, questionAnswers)
                "SLIDER" -> validateSlider(question, questionAnswers)
                "TEXT" -> validateText(question, questionAnswers)
                "TEXT_WITH_OPTION" -> validateTextWithOption(question, questionAnswers)
                "MATRIX" -> validateMatrix(question, questionAnswers)
                else -> throw BizException("QUESTION_TYPE_UNSUPPORTED", messages.get("error.question_type_not_supported", question.questionType))
            }
        }
    }

    private fun validateSingleChoice(
        question: org.sainm.psy.assessment.domain.TaskQuestionItem,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        if (answers.size != 1) {
            throw BizException("ANSWER_SINGLE_CHOICE_INVALID", messages.get("error.answer_single_choice_invalid", question.questionNo))
        }
        val answer = answers.first()
        if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
            throw BizException("ANSWER_OPTION_INVALID", messages.get("error.answer_option_invalid", question.questionNo))
        }
        if (answer.answerValue != null) {
            throw BizException("ANSWER_VALUE_UNEXPECTED", messages.get("error.answer_value_unexpected", question.questionNo))
        }
        if (!answer.answerText.isNullOrBlank()) {
            throw BizException("ANSWER_TEXT_UNEXPECTED", messages.get("error.answer_text_unexpected", question.questionNo))
        }
    }

    private fun validateMultiSelect(
        question: org.sainm.psy.assessment.domain.TaskQuestionItem,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        if (answers.isEmpty()) {
            throw BizException("ANSWER_MULTI_SELECT_EMPTY", messages.get("error.answer_multi_select_empty", question.questionNo))
        }
        val optionIds = answers.mapNotNull { it.optionId }
        if (optionIds.size != answers.size || optionIds.distinct().size != optionIds.size) {
            throw BizException("ANSWER_MULTI_SELECT_INVALID", messages.get("error.answer_multi_select_invalid", question.questionNo))
        }
        val optionMap = question.options.associateBy { it.optionId }
        if (optionIds.any { it !in optionMap }) {
            throw BizException("ANSWER_OPTION_INVALID", messages.get("error.answer_option_invalid", question.questionNo))
        }
        if (question.optionSelectionLimit != null && optionIds.size > question.optionSelectionLimit) {
            throw BizException(
                "ANSWER_SELECTION_LIMIT_EXCEEDED",
                messages.get("error.answer_selection_limit_exceeded", question.questionNo, question.optionSelectionLimit)
            )
        }
        val exclusiveSelected = optionIds.count { optionMap.getValue(it).exclusiveFlag }
        if (exclusiveSelected > 1 || (exclusiveSelected == 1 && optionIds.size > 1)) {
            throw BizException("ANSWER_EXCLUSIVE_OPTION_CONFLICT", messages.get("error.answer_exclusive_option_conflict", question.questionNo))
        }
        if (answers.any { it.answerValue != null }) {
            throw BizException("ANSWER_VALUE_UNEXPECTED", messages.get("error.answer_value_unexpected", question.questionNo))
        }
        if (answers.any { !it.answerText.isNullOrBlank() }) {
            throw BizException("ANSWER_TEXT_UNEXPECTED", messages.get("error.answer_text_unexpected", question.questionNo))
        }
    }

    private fun validateSlider(
        question: org.sainm.psy.assessment.domain.TaskQuestionItem,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        if (answers.size != 1) {
            throw BizException("ANSWER_SLIDER_INVALID", messages.get("error.answer_slider_invalid", question.questionNo))
        }
        val answer = answers.first()
        val value = answer.answerValue
            ?: throw BizException("ANSWER_SLIDER_VALUE_REQUIRED", messages.get("error.answer_slider_value_required", question.questionNo))
        if (answer.optionId != null) {
            throw BizException("ANSWER_OPTION_UNEXPECTED", messages.get("error.answer_option_unexpected", question.questionNo))
        }
        if (!answer.answerText.isNullOrBlank()) {
            throw BizException("ANSWER_TEXT_UNEXPECTED", messages.get("error.answer_text_unexpected", question.questionNo))
        }
        val min = question.sliderMin
        val max = question.sliderMax
        if (min == null || max == null || value < min || value > max) {
            throw BizException("ANSWER_SLIDER_OUT_OF_RANGE", messages.get("error.answer_slider_out_of_range", question.questionNo))
        }
        question.sliderStep?.takeIf { it > BigDecimal.ZERO }?.let { step ->
            val offset = value.subtract(min)
            if (offset.remainder(step).compareTo(BigDecimal.ZERO) != 0) {
                throw BizException("ANSWER_SLIDER_STEP_INVALID", messages.get("error.answer_slider_step_invalid", question.questionNo, step.stripTrailingZeros().toPlainString()))
            }
        }
    }

    private fun validateText(
        question: org.sainm.psy.assessment.domain.TaskQuestionItem,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        if (answers.size != 1) {
            throw BizException("ANSWER_TEXT_INVALID", messages.get("error.answer_text_invalid", question.questionNo))
        }
        val answer = answers.first()
        if (answer.optionId != null || answer.answerValue != null) {
            throw BizException("ANSWER_OPTION_UNEXPECTED", messages.get("error.answer_option_unexpected", question.questionNo))
        }
        if (answer.answerText.isNullOrBlank()) {
            throw BizException("ANSWER_TEXT_REQUIRED", messages.get("error.answer_text_required", question.questionNo))
        }
    }

    private fun validateTextWithOption(
        question: org.sainm.psy.assessment.domain.TaskQuestionItem,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        if (answers.size != 1) {
            throw BizException("ANSWER_TEXT_WITH_OPTION_INVALID", messages.get("error.answer_single_choice_invalid", question.questionNo))
        }
        val answer = answers.first()
        if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
            throw BizException("ANSWER_OPTION_INVALID", messages.get("error.answer_option_invalid", question.questionNo))
        }
        if (answer.answerValue != null) {
            throw BizException("ANSWER_VALUE_UNEXPECTED", messages.get("error.answer_value_unexpected", question.questionNo))
        }
        if (question.textInputEnabled) {
            if (answer.answerText.isNullOrBlank()) {
                throw BizException("ANSWER_TEXT_REQUIRED", messages.get("error.answer_text_required", question.questionNo))
            }
        } else if (!answer.answerText.isNullOrBlank()) {
            throw BizException("ANSWER_TEXT_UNEXPECTED", messages.get("error.answer_text_unexpected", question.questionNo))
        }
    }

    private fun validateMatrix(
        question: org.sainm.psy.assessment.domain.TaskQuestionItem,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>
    ) {
        validateSingleChoice(question, answers)
    }

    private fun buildResultSummary(scoreText: String, riskLevel: String, resultTitle: String?): String =
        resultTitle
            ?.takeIf { it.isNotBlank() }
            ?.let { messages.get("report.result.summary.with_title", scoreText, riskLevel, it) }
            ?: messages.get("report.result.summary.without_title", scoreText, riskLevel)

    private fun buildReportContent(
        scoreText: String,
        riskLevel: String,
        standardScore: BigDecimal?,
        scoreSource: String,
        suggestionText: String?,
        resultDescription: String?
    ): String = buildString {
        append(messages.get("report.auto.header")).append("\n")
        append(messages.get("report.auto.score", scoreText)).append("\n")
        append(messages.get("report.auto.risk", riskLevel)).append("\n")
        standardScore?.let {
            append(messages.get("report.auto.standard", scoreSource, it.stripTrailingZeros().toPlainString())).append("\n")
        }
        suggestionText?.takeIf { it.isNotBlank() }?.let { append(it) }
            ?: resultDescription?.takeIf { it.isNotBlank() }?.let { append(it) }
    }
}
