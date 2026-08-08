package org.sainm.psy.assessment.service

import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.assessment.domain.AnswerSheetDraftSaveResult
import org.sainm.psy.assessment.domain.AnswerSheetRescoreResult
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.repository.AnswerSheetRepository
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.notification.service.NotificationDispatchService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Clock
import java.time.LocalDateTime

@Service
class AnswerSheetService(
    private val answerSheetRepository: AnswerSheetRepository,
    private val scoreCalculator: ScoreCalculator,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages,
    private val anonymousAssessmentIdentity: AnonymousAssessmentIdentity? = null,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    @Value("\${psy.assessment.draft-retention-days:30}")
    private val draftRetentionDays: Long = 30
) {
    private val logger = LoggerFactory.getLogger(AnswerSheetService::class.java)

    private enum class ValidationMode {
        DRAFT_SAVE,
        FINAL_SUBMIT
    }

    private data class RespondentContext(
        val payload: TaskQuestionPayload,
        val userId: Long?,
        val anonymousToken: String?
    ) {
        val anonymous: Boolean get() = anonymousToken != null
    }

    fun getTaskQuestions(taskId: Long): TaskQuestionPayload {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", messages.get("error.task_forbidden"))
        }
        val context = loadRespondentContext(taskId, currentUser.userId)
        val payload = context.payload
        ensureTaskReadable(payload, LocalDateTime.now(clock))
        if (hasSubmitted(context)) {
            val submittedReport = context.userId?.let { answerSheetRepository.findLatestSubmittedTaskReport(taskId, it) }
            if (payload.allowRetakeFlag) {
                return payload.copy(
                    completedReportId = submittedReport?.reportId,
                    completedResultId = submittedReport?.resultId,
                    completedRiskLevel = submittedReport?.riskLevel
                )
            }
            return payload.copy(
                completedFlag = true,
                completedReportId = submittedReport?.reportId,
                completedResultId = submittedReport?.resultId,
                completedRiskLevel = submittedReport?.riskLevel,
                draftAnswerSheetId = null,
                draftVersionNo = null,
                draftAnswers = emptyList(),
                questions = emptyList()
            )
        }
        return payload
    }

    @Transactional
    fun save(request: SaveAnswerSheetRequest): AnswerSheetDraftSaveResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!answerSheetRepository.isAssignedToUser(request.taskId, currentUser.userId, currentUser.groupId)) {
            throw BizException("TASK_FORBIDDEN", messages.get("error.task_forbidden"))
        }
        val context = loadRespondentContext(request.taskId, currentUser.userId)
        val payload = context.payload
        ensureTaskAcceptsDraftSave(payload, LocalDateTime.now(clock))
        if (!payload.allowSaveFlag) {
            throw BizException("TASK_SAVE_DISABLED", messages.get("error.task_save_disabled"))
        }
        ensureTaskAcceptsNewSubmission(payload, hasSubmitted(context))
        validateAnswers(payload, request.scaleId, request.answers, ValidationMode.DRAFT_SAVE)
        val draftInfo = findDraftInfo(context)
        request.answerSheetId?.let { expectedId ->
            if (draftInfo == null) {
                throw BizException("ANSWER_SHEET_DRAFT_NOT_FOUND", messages.get("error.answer_sheet_draft_not_found"))
            }
            if (draftInfo.answerSheetId != expectedId) {
                throw BizException("ANSWER_SHEET_DRAFT_MISMATCH", messages.get("error.answer_sheet_draft_mismatch"))
            }
        }
        val answerSheetId = draftInfo?.answerSheetId
            ?: findOrCreateDraft(request.taskId, request.scaleId, context)
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
        val submitToken = effectiveSubmitToken(request, currentUser.userId)
        answerSheetRepository.findSubmittedResultBySubmitToken(request.taskId, currentUser.userId, submitToken)
            ?.let { return it }
        val context = loadRespondentContext(request.taskId, currentUser.userId)
        if (context.anonymous) {
            findSubmittedResult(context, submitToken)?.let { return it }
        }
        val payload = context.payload
        ensureTaskAcceptsFinalSubmit(payload, LocalDateTime.now(clock))
        ensureTaskAcceptsNewSubmission(payload, hasSubmitted(context))
        validateAnswers(payload, request.scaleId, request.answers, ValidationMode.FINAL_SUBMIT)
        val draftInfo = findDraftInfo(context)
        request.answerSheetId?.let { expectedId ->
            if (draftInfo == null) {
                throw BizException("ANSWER_SHEET_DRAFT_NOT_FOUND", messages.get("error.answer_sheet_draft_not_found"))
            }
            if (draftInfo.answerSheetId != expectedId) {
                throw BizException("ANSWER_SHEET_DRAFT_MISMATCH", messages.get("error.answer_sheet_draft_mismatch"))
            }
        }
        val answerSheetId = draftInfo?.answerSheetId
            ?: findOrCreateDraft(request.taskId, request.scaleId, context)
        val optionScoreMap = answerSheetRepository.replaceAnswerItems(answerSheetId, request.answers)
        return finalizeSubmission(
            answerSheetId = answerSheetId,
            taskId = request.taskId,
            scaleId = request.scaleId,
            userId = context.userId,
            anonymousToken = context.anonymousToken,
            answers = request.answers,
            optionScoreMap = optionScoreMap,
            scaleName = payload.scaleName,
            autoSubmitted = false,
            expectedVersion = if (draftInfo != null) request.versionNo else null,
            submitToken = submitToken
        )
    }

    @Transactional
    fun autoSubmitOverdueDrafts(): Int = autoSubmitOverdueDrafts(LocalDateTime.now(clock))

    @Transactional
    fun autoSubmitOverdueDrafts(now: LocalDateTime): Int {
        val overdueDrafts = answerSheetRepository.findOverdueDraftAnswerSheets(now)
        var submittedCount = 0
        overdueDrafts.forEach { draft ->
            val alreadySubmitted = if (draft.userId != null) {
                answerSheetRepository.hasSubmittedAnswerSheet(draft.taskId, draft.userId)
            } else {
                draft.anonymousToken?.let { answerSheetRepository.hasSubmittedAnonymousAnswerSheet(draft.taskId, it) } == true
            }
            if (alreadySubmitted) {
                return@forEach
            }
            val answers = answerSheetRepository.loadAnswerItems(draft.answerSheetId)
            val optionScoreMap = answerSheetRepository.replaceAnswerItems(draft.answerSheetId, answers)
            finalizeSubmission(
                answerSheetId = draft.answerSheetId,
                taskId = draft.taskId,
                scaleId = draft.scaleId,
                userId = draft.userId,
                anonymousToken = draft.anonymousToken,
                answers = answers,
                optionScoreMap = optionScoreMap,
                scaleName = null,
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
        val now = LocalDateTime.now(clock)
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
            content = buildReportContent(scored)
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
        userId: Long?,
        anonymousToken: String?,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>,
        optionScoreMap: Map<Long, BigDecimal>,
        scaleName: String?,
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
                findSubmittedResult(taskId, userId, anonymousToken, token)?.let { return it }
            }
            throw BizException("ANSWER_SHEET_VERSION_CONFLICT", messages.get("error.answer_sheet_version_conflict"))
        }
        if (submitted == 0) {
            submitToken?.let { token ->
                findSubmittedResult(taskId, userId, anonymousToken, token)?.let { return it }
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

        val reportId = userId?.let { identifiedUserId ->
            val reportContent = buildReportContent(scored, scaleName)
            val createdReportId = answerSheetRepository.createReport(
                resultId = resultId,
                authorUserId = identifiedUserId,
                title = scored.resultTitle ?: messages.get("report.system.title"),
                content = reportContent
            )
            createWarningForSubmission(
                answerSheetId = answerSheetId,
                resultId = resultId,
                riskLevel = riskLevel,
                warningLevel = scored.highRiskWarningLevel ?: riskLevel
            )
            runCatching {
                notificationDispatchService.notifyReportGenerated(
                    reportId = createdReportId,
                    resultId = resultId,
                    taskId = taskId,
                    riskLevel = riskLevel,
                    autoSubmitted = autoSubmitted,
                    receiverUserIds = listOf(identifiedUserId)
                )
            }.onFailure { error ->
                logger.error(
                    "Failed to dispatch report notification after submission. answerSheetId={}, resultId={}, reportId={}",
                    answerSheetId,
                    resultId,
                    createdReportId,
                    error
                )
            }
            createdReportId
        }
        return AnswerSubmitResult(
            answerSheetId = answerSheetId,
            resultId = resultId,
            reportId = reportId,
            riskLevel = riskLevel,
            versionNo = (expectedVersion ?: 0) + 1,
            anonymous = anonymousToken != null
        )
    }

    private fun calculateScore(
        scaleId: Long,
        userId: Long?,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>,
        optionScoreMap: Map<Long, BigDecimal>
    ): ScoreResult {
        val (scaleContext, normContext) = answerSheetRepository.loadScaleScoringContext(scaleId, userId)
        val questionContexts = answerSheetRepository.loadQuestionScoringMeta(scaleId, answers, optionScoreMap)
        return scoreCalculator.calculate(scaleId, scaleContext.scoreMethod, scaleContext.scoreCoefficient, questionContexts, normContext)
    }

    private fun loadRespondentContext(taskId: Long, authenticatedUserId: Long): RespondentContext {
        val initialPayload = answerSheetRepository.findTaskQuestionPayload(taskId, authenticatedUserId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        if (!initialPayload.anonymousFlag) {
            return RespondentContext(initialPayload, authenticatedUserId, null)
        }
        val token = anonymousAssessmentIdentity?.token(taskId, authenticatedUserId)
            ?: throw IllegalStateException("Anonymous assessment identity service is not configured")
        val anonymousPayload = answerSheetRepository.findAnonymousTaskQuestionPayload(taskId, token)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        return RespondentContext(anonymousPayload, null, token)
    }

    private fun ensureTaskAcceptsNewSubmission(payload: TaskQuestionPayload, hasSubmitted: Boolean) {
        if (hasSubmitted && !payload.allowRetakeFlag) {
            throw BizException("TASK_ALREADY_SUBMITTED", messages.get("error.task_already_submitted"))
        }
    }

    private fun findOrCreateDraft(taskId: Long, scaleId: Long, context: RespondentContext): Long =
        try {
            if (context.userId != null) {
                answerSheetRepository.createAnswerSheet(taskId, scaleId, context.userId, "DRAFT")
            } else {
                answerSheetRepository.createAnonymousAnswerSheet(taskId, scaleId, requireNotNull(context.anonymousToken), "DRAFT")
            }
        } catch (_: DuplicateKeyException) {
            findDraftInfo(context)?.answerSheetId
                ?: throw BizException("ANSWER_SHEET_VERSION_CONFLICT", messages.get("error.answer_sheet_version_conflict"))
        }

    private fun findDraftInfo(context: RespondentContext): AnswerSheetRepository.DraftAnswerSheetInfo? =
        context.userId?.let { answerSheetRepository.findDraftAnswerSheetInfo(context.payload.taskId, it) }
            ?: context.anonymousToken?.let { answerSheetRepository.findAnonymousDraftAnswerSheetInfo(context.payload.taskId, it) }

    private fun hasSubmitted(context: RespondentContext): Boolean =
        context.userId?.let { answerSheetRepository.hasSubmittedAnswerSheet(context.payload.taskId, it) }
            ?: context.anonymousToken?.let { answerSheetRepository.hasSubmittedAnonymousAnswerSheet(context.payload.taskId, it) }
            ?: false

    private fun findSubmittedResult(context: RespondentContext, submitToken: String): AnswerSubmitResult? =
        findSubmittedResult(context.payload.taskId, context.userId, context.anonymousToken, submitToken)

    private fun findSubmittedResult(
        taskId: Long,
        userId: Long?,
        anonymousToken: String?,
        submitToken: String
    ): AnswerSubmitResult? =
        userId?.let { answerSheetRepository.findSubmittedResultBySubmitToken(taskId, it, submitToken) }
            ?: anonymousToken?.let { answerSheetRepository.findAnonymousSubmittedResultBySubmitToken(taskId, it, submitToken) }

    private fun effectiveSubmitToken(request: SubmitAnswerSheetRequest, userId: Long): String {
        request.submitToken?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
            if (token.length > 128) {
                throw BizException("SUBMIT_TOKEN_INVALID", messages.get("error.submit_token_invalid"))
            }
            return token
        }
        val canonicalAnswers = request.answers
            .sortedWith(compareBy({ it.questionId }, { it.optionId ?: Long.MIN_VALUE }, { it.answerText.orEmpty() }, { it.answerValue }))
            .joinToString("|") { answer ->
                listOf(
                    answer.questionId.toString(),
                    answer.optionId?.toString().orEmpty(),
                    answer.answerText.orEmpty(),
                    answer.answerValue?.stripTrailingZeros()?.toPlainString().orEmpty()
                ).joinToString(":")
            }
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "${request.taskId}:$userId:$canonicalAnswers".toByteArray(StandardCharsets.UTF_8)
        )
        return "legacy:" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun ensureTaskReadable(payload: TaskQuestionPayload, now: LocalDateTime) {
        ensureTaskNotClosed(payload)
        if (now.isBefore(payload.startTime)) {
            throw BizException("TASK_NOT_STARTED", messages.get("error.task_not_started"))
        }
        if (now.isAfter(payload.endTime) && !payload.allowTimeoutSubmitFlag) {
            throw BizException("TASK_EXPIRED", messages.get("error.task_expired"))
        }
    }

    private fun ensureTaskAcceptsDraftSave(payload: TaskQuestionPayload, now: LocalDateTime) {
        ensureTaskNotClosed(payload)
        if (now.isBefore(payload.startTime)) {
            throw BizException("TASK_NOT_STARTED", messages.get("error.task_not_started"))
        }
        if (now.isAfter(payload.endTime)) {
            throw BizException("TASK_EXPIRED", messages.get("error.task_expired"))
        }
    }

    private fun ensureTaskAcceptsFinalSubmit(payload: TaskQuestionPayload, now: LocalDateTime) {
        ensureTaskNotClosed(payload)
        if (now.isBefore(payload.startTime)) {
            throw BizException("TASK_NOT_STARTED", messages.get("error.task_not_started"))
        }
        if (now.isAfter(payload.endTime) && !payload.allowTimeoutSubmitFlag) {
            throw BizException("TASK_EXPIRED", messages.get("error.task_expired"))
        }
    }

    private fun ensureTaskNotClosed(payload: TaskQuestionPayload) {
        if (payload.taskStatus == "CLOSED") {
            throw BizException("TASK_CLOSED", messages.get("error.task_closed"))
        }
    }

    private fun createWarningForSubmission(
        answerSheetId: Long,
        resultId: Long,
        riskLevel: String,
        warningLevel: String
    ) {
        if (riskLevel == "NORMAL" && warningLevel == "NORMAL") {
            return
        }
        try {
            answerSheetRepository.createWarningIfNeeded(
                resultId = resultId,
                riskLevel = riskLevel,
                warningLevel = warningLevel,
                reason = messages.get("warning.auto.reason", warningLevel)
            )
        } catch (error: Exception) {
            logger.error(
                "Failed to create warning after submission. answerSheetId={}, resultId={}, riskLevel={}, warningLevel={}",
                answerSheetId,
                resultId,
                riskLevel,
                warningLevel,
                error
            )
            throw BizException("WARNING_CREATE_FAILED", messages.get("error.warning_create_failed"))
        }
    }

    private fun validateAnswers(
        payload: TaskQuestionPayload,
        scaleId: Long,
        answers: List<org.sainm.psy.assessment.api.AnswerItemRequest>,
        mode: ValidationMode
    ) {
        if (payload.scaleId != scaleId) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        val questionMap = payload.questions.associateBy { it.questionId }
        val answersByQuestionId = answers.groupBy { it.questionId }

        answersByQuestionId.keys
            .filterNot(questionMap::containsKey)
            .firstOrNull()
            ?.let { throw BizException("ANSWER_QUESTION_INVALID", messages.get("error.answer_question_invalid", it)) }

        if (mode == ValidationMode.FINAL_SUBMIT) {
            payload.questions
                .firstOrNull { it.requiredFlag && answersByQuestionId[it.questionId].isNullOrEmpty() }
                ?.let { throw BizException("ANSWER_REQUIRED_MISSING", messages.get("error.answer_required_missing", it.questionNo)) }
        }

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

    private fun buildReportContent(scored: ScoreResult, scaleName: String? = null): String {
        val dimensionScores = scored.dimensionScores
        val dimensionMeta = if (dimensionScores.isEmpty()) {
            emptyMap()
        } else {
            answerSheetRepository.findDimensionReportMeta(dimensionScores.map { it.dimensionId })
        }
        val dimensions = dimensionScores
            .sortedWith(compareBy<DimensionScoreResult> { dimensionMeta[it.dimensionId]?.sortNo ?: Int.MAX_VALUE }.thenBy { it.dimensionId })
        return buildString {
            appendLine(messages.get("report.auto.header"))
            appendLine()
            scaleName?.takeIf { it.isNotBlank() }?.let {
                appendLine(messages.get("report.auto.scale", it))
            }
            appendLine(messages.get("report.auto.score", formatScore(scored.totalScore)))
            appendLine(messages.get("report.auto.risk", scored.riskLevel))
            scored.standardScore?.let {
                appendLine(messages.get("report.auto.standard", scored.scoreSource, formatScore(it)))
            }
            scored.zScore?.let { appendLine(messages.get("report.auto.z_score", formatScore(it))) }
            scored.tScore?.let { appendLine(messages.get("report.auto.t_score", formatScore(it))) }
            scored.normCode?.takeIf { it.isNotBlank() }?.let { appendLine(messages.get("report.auto.norm", it)) }
            if (scored.highRiskTriggered) {
                appendLine(messages.get("report.auto.high_risk", scored.highRiskRuleCode ?: "-"))
            }

            scored.resultDescription?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(messages.get("report.auto.section.interpretation"))
                appendLine(it)
            }

            if (dimensions.isNotEmpty()) {
                appendLine()
                appendLine(messages.get("report.auto.section.dimensions"))
                dimensions.forEach { dimension ->
                    val meta = dimensionMeta[dimension.dimensionId]
                    val name = meta?.dimensionName ?: messages.get("report.auto.dimension.unknown", dimension.dimensionId)
                    val code = meta?.dimensionCode?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    val parts = mutableListOf(
                        messages.get("report.auto.dimension.score", formatScore(dimension.score))
                    )
                    dimension.riskLevel?.takeIf { it.isNotBlank() }?.let {
                        parts += messages.get("report.auto.dimension.risk", it)
                    }
                    dimension.standardScore?.let {
                        parts += messages.get("report.auto.dimension.standard", dimension.scoreSource, formatScore(it))
                    }
                    dimension.zScore?.let { parts += messages.get("report.auto.dimension.z_score", formatScore(it)) }
                    dimension.tScore?.let { parts += messages.get("report.auto.dimension.t_score", formatScore(it)) }
                    dimension.normCode?.takeIf { it.isNotBlank() }?.let {
                        parts += messages.get("report.auto.dimension.norm", it)
                    }
                    appendLine("- $name$code：${parts.joinToString("；")}")
                }
            }

            scored.suggestionText?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(messages.get("report.auto.section.suggestion"))
                appendLine(it)
            }

            appendLine()
            append(messages.get("report.auto.disclaimer"))
        }
    }

    private fun formatScore(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()
}
