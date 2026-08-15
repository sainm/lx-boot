package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.assessment.service.NormMatchingContext
import org.sainm.psy.assessment.service.QuestionScoreContext
import org.sainm.psy.assessment.service.ScoreCalculator
import org.sainm.psy.assessment.service.ScoreCalculationOptions
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.exception.NotFoundBizException
import org.sainm.psy.common.api.CursorPage
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.CreateScaleGoldenCaseRequest
import org.sainm.psy.scale.api.GoldenCaseExpected
import org.sainm.psy.scale.api.GoldenCaseInput
import org.sainm.psy.scale.api.GoldenCaseRunResponse
import org.sainm.psy.scale.api.ScalePublicationReviewRequest
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseHistory
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScaleGoldenCaseReadiness
import org.sainm.psy.scale.domain.ScalePublicationHistory
import org.sainm.psy.scale.domain.ScalePublicationReadiness
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ScalePublicationGovernanceService(
    private val scaleRepository: ScaleRepository,
    private val packageRepository: ScalePackageRepository,
    private val publicationRepository: ScalePublicationRepository,
    private val fingerprintService: ScaleContentFingerprintService,
    private val scoreCalculator: ScoreCalculator,
    private val currentUserFacade: CurrentUserFacade,
    private val objectMapper: ObjectMapper,
    private val messages: LocalizedMessages,
    private val securityAuditService: SecurityAuditService,
    private val tenantAccessPolicy: TenantAccessPolicy
) {
    @Transactional
    fun saveGoldenCase(scaleId: Long, request: CreateScaleGoldenCaseRequest): ScaleGoldenCase {
        val scale = requireDraftOwnedScale(scaleId)
        val normalizedCode = request.caseCode.trim().uppercase()
        val normalizedType = request.caseType.trim().uppercase()
        validateCaseRequest(scale, normalizedCode, normalizedType, request)
        val scaleHash = fingerprintService.calculate(scale)
        val inputJson = objectMapper.writeValueAsString(request.input)
        val expectedJson = objectMapper.writeValueAsString(request.expected)
        val caseHash = fingerprintService.sha256(
            listOf(normalizedCode, normalizedType, request.sourceReference.trim(), inputJson, expectedJson).joinToString("|")
        )
        val saved = publicationRepository.saveCaseRevision(
            scaleId = scaleId,
            caseCode = normalizedCode,
            caseType = normalizedType,
            sourceReference = request.sourceReference.trim(),
            scaleContentHash = scaleHash,
            caseContentHash = caseHash,
            inputJson = inputJson,
            expectedJson = expectedJson,
            userId = currentUserFacade.requireCurrentUserId()
        )
        securityAuditService.recordScaleGoldenCaseSaved(scaleId, saved.id, saved.caseCode, saved.revisionNo)
        return saved
    }

    fun listGoldenCases(scaleId: Long): List<ScaleGoldenCase> {
        requireOwnedScale(scaleId)
        return publicationRepository.findLatestCases(scaleId)
    }

    @Transactional(readOnly = true)
    fun history(scaleId: Long): ScalePublicationHistory {
        requireOwnedScale(scaleId)
        val runsByCase = publicationRepository.findAllRuns(scaleId).groupBy { it.goldenCaseId }
        return ScalePublicationHistory(
            cases = publicationRepository.findAllCases(scaleId).map { goldenCase ->
                ScaleGoldenCaseHistory(goldenCase, runsByCase[goldenCase.id].orEmpty())
            },
            reviews = publicationRepository.findAllReviews(scaleId)
        )
    }

    @Transactional(readOnly = true)
    fun historyCases(scaleId: Long, afterId: Long?, limit: Int): CursorPage<ScaleGoldenCase> {
        requireOwnedScale(scaleId)
        return publicationRepository.findCaseHistoryPage(scaleId, normalizeHistoryCursor(afterId), limit)
    }

    @Transactional(readOnly = true)
    fun historyRuns(scaleId: Long, afterId: Long?, limit: Int): CursorPage<ScaleGoldenCaseRun> {
        requireOwnedScale(scaleId)
        return publicationRepository.findRunHistoryPage(scaleId, normalizeHistoryCursor(afterId), limit)
    }

    @Transactional(readOnly = true)
    fun historyReviews(scaleId: Long, afterId: Long?, limit: Int): CursorPage<ScalePublicationReview> {
        requireOwnedScale(scaleId)
        return publicationRepository.findReviewHistoryPage(scaleId, normalizeHistoryCursor(afterId), limit)
    }

    @Transactional
    fun runGoldenCase(scaleId: Long, caseId: Long): GoldenCaseRunResponse {
        val scale = requireDraftOwnedScale(scaleId)
        val case = requireCurrentCase(scaleId, caseId)
        val currentHash = fingerprintService.calculate(scale)
        if (case.scaleContentHash != currentHash) {
            throw BizException("GOLDEN_CASE_CONTENT_STALE", messages.get("scale.golden_case.content_stale"))
        }
        val input = objectMapper.readValue(case.inputJson, GoldenCaseInput::class.java)
        val expected = objectMapper.readValue(case.expectedJson, GoldenCaseExpected::class.java)
        val evaluation = evaluate(scale, input, expected)
        val algorithm = packageRepository.find(scaleId).algorithmBinding
        val run = publicationRepository.saveRun(
            case = case,
            algorithmCode = algorithm?.algorithmCode,
            algorithmVersion = algorithm?.algorithmVersion,
            passed = evaluation.differences.isEmpty(),
            actualJson = objectMapper.writeValueAsString(evaluation.actual),
            differencesJson = objectMapper.writeValueAsString(evaluation.differences),
            userId = currentUserFacade.requireCurrentUserId()
        )
        securityAuditService.recordScaleGoldenCaseRun(scaleId, caseId, run.id, run.passed)
        return GoldenCaseRunResponse(run.id, caseId, run.passed, evaluation.actual, evaluation.differences)
    }

    @Transactional
    fun approveGoldenCase(scaleId: Long, caseId: Long): ScaleGoldenCase {
        val scale = requireDraftOwnedScale(scaleId)
        val case = requireCurrentCase(scaleId, caseId)
        val currentUser = currentUserFacade.requireCurrentUser()
        if ("COUNSELOR" !in currentUser.roles) {
            throw BizException("GOLDEN_CASE_PROFESSIONAL_REQUIRED", messages.get("scale.golden_case.professional_required"))
        }
        if (case.createdBy == currentUser.userId) {
            throw BizException("GOLDEN_CASE_INDEPENDENT_REVIEW_REQUIRED", messages.get("scale.golden_case.independent_review_required"))
        }
        if (case.scaleContentHash != fingerprintService.calculate(scale) || publicationRepository.findLatestRun(case.id)?.passed != true) {
            throw BizException("GOLDEN_CASE_PASS_REQUIRED", messages.get("scale.golden_case.pass_required"))
        }
        if (case.approvedBy != null) {
            if (case.approvedBy != currentUser.userId) {
                throw BizException("GOLDEN_CASE_ALREADY_APPROVED", messages.get("scale.golden_case.already_approved"))
            }
            return case
        }
        publicationRepository.approveCase(case.id, currentUser.userId)
        securityAuditService.recordScaleGoldenCaseApproved(scaleId, caseId)
        return requireNotNull(publicationRepository.findCase(case.id))
    }

    fun readiness(scaleId: Long): ScalePublicationReadiness {
        val scale = requireOwnedScale(scaleId)
        return buildReadiness(scale, fingerprintService.calculate(scale))
    }

    @Transactional
    fun review(scaleId: Long, reviewType: String, request: ScalePublicationReviewRequest): ScalePublicationReview {
        val scale = requireDraftOwnedScale(scaleId)
        val normalizedType = reviewType.trim().uppercase()
        val decision = request.decision.trim().uppercase()
        val token = request.reviewToken.trim()
        val comment = request.comment?.trim()?.takeIf(String::isNotEmpty)
        val qualificationReference = request.qualificationReference?.trim()?.takeIf(String::isNotEmpty)
        val evidenceReference = request.evidenceReference?.trim()?.takeIf(String::isNotEmpty)
        val reviewScope = request.reviewScope?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedType !in reviewTypes || decision !in reviewDecisions || token.length !in 1..128 ||
            comment?.length?.let { it > MAX_REVIEW_TEXT_LENGTH } == true ||
            qualificationReference?.length?.let { it > MAX_EVIDENCE_REFERENCE_LENGTH } == true ||
            evidenceReference?.length?.let { it > MAX_EVIDENCE_REFERENCE_LENGTH } == true ||
            reviewScope?.length?.let { it > MAX_REVIEW_TEXT_LENGTH } == true
        ) {
            throw BizException("SCALE_PUBLICATION_REVIEW_INVALID", messages.get("scale.publication.review_invalid"))
        }
        if (decision == "APPROVED" &&
            (evidenceReference == null || reviewScope == null ||
                (normalizedType == "PROFESSIONAL" && qualificationReference == null))
        ) {
            throw BizException(
                "SCALE_PUBLICATION_REVIEW_EVIDENCE_REQUIRED",
                messages.get("scale.publication.review_evidence_required", normalizedType)
            )
        }
        val currentUser = currentUserFacade.requireCurrentUser()
        val eligibleRoles = if (normalizedType == "PROFESSIONAL") professionalRoles else businessRoles
        val reviewerRole = currentUser.roles.firstOrNull { it in eligibleRoles }
            ?: throw BizException("SCALE_PUBLICATION_REVIEW_ROLE_REQUIRED", messages.get("scale.publication.review_role_required", normalizedType))
        if (scale.createdBy == currentUser.userId) {
            throw BizException("SCALE_PUBLICATION_INDEPENDENT_REVIEW_REQUIRED", messages.get("scale.publication.independent_review_required"))
        }
        val currentHash = fingerprintService.calculate(scale)
        val readiness = buildReadiness(scale, currentHash)
        if (decision == "APPROVED" && readiness.blockers.any { !it.startsWith("REVIEW_") }) {
            throw BizException("SCALE_PUBLICATION_EVIDENCE_INCOMPLETE", messages.get("scale.publication.evidence_incomplete"))
        }
        val saved = publicationRepository.saveReview(
            scaleId, normalizedType, decision, currentUser.userId, reviewerRole,
            currentUser.displayName?.trim()?.takeIf(String::isNotEmpty) ?: currentUser.username,
            currentHash, readiness.releaseFingerprint, token, comment,
            qualificationReference, evidenceReference, reviewScope
        )
        if (saved.decision != decision || saved.reviewerId != currentUser.userId ||
            saved.scaleContentHash != currentHash || saved.releaseFingerprint != readiness.releaseFingerprint ||
            saved.commentText != comment || saved.qualificationReference != qualificationReference ||
            saved.evidenceReference != evidenceReference || saved.reviewScope != reviewScope
        ) {
            throw BizException("SCALE_PUBLICATION_REVIEW_TOKEN_CONFLICT", messages.get("scale.publication.review_token_conflict"))
        }
        securityAuditService.recordScalePublicationReviewed(scaleId, normalizedType, decision, readiness.releaseFingerprint)
        return saved
    }

    fun assertReadyForPublication(scale: ScaleDetail, scaleContentHash: String) {
        val readiness = buildReadiness(scale, scaleContentHash)
        if (!readiness.ready) {
            throw BizException(
                "SCALE_PUBLICATION_NOT_READY",
                messages.get("scale.publication.not_ready", readiness.blockers.joinToString(","))
            )
        }
    }

    private fun buildReadiness(scale: ScaleDetail, scaleHash: String): ScalePublicationReadiness {
        val requiredTypes = requiredCaseTypesForScale(scale)
        val latestCases = publicationRepository.findLatestCases(scale.id)
        val releaseFingerprint = fingerprintService.calculateReleaseFingerprint(scaleHash, latestCases)
        val caseReadiness = latestCases.map { case ->
            ScaleGoldenCaseReadiness(
                id = case.id,
                caseCode = case.caseCode,
                revisionNo = case.revisionNo,
                caseType = case.caseType,
                currentContent = case.scaleContentHash == scaleHash,
                approved = case.approvedBy != null,
                latestRunPassed = publicationRepository.findLatestRun(case.id)?.passed == true
            )
        }
        val reviews = publicationRepository.findLatestReviews(scale.id, releaseFingerprint)
        val professional = reviews["PROFESSIONAL"]
        val business = reviews["BUSINESS"]
        val blockers = buildList {
            addAll(packageBlockers(scale))
            requiredTypes.filterNot { required -> caseReadiness.any { it.caseType == required } }
                .forEach { add("GOLDEN_CASE_TYPE_MISSING:$it") }
            caseReadiness.filterNot { it.currentContent }.forEach { add("GOLDEN_CASE_STALE:${it.caseCode}") }
            caseReadiness.filterNot { it.latestRunPassed }.forEach { add("GOLDEN_CASE_NOT_PASSING:${it.caseCode}") }
            caseReadiness.filterNot { it.approved }.forEach { add("GOLDEN_CASE_NOT_APPROVED:${it.caseCode}") }
            if (professional?.decision != "APPROVED") add("REVIEW_PROFESSIONAL_MISSING")
            if (business?.decision != "APPROVED") add("REVIEW_BUSINESS_MISSING")
            if (professional?.decision == "APPROVED" && professional.qualificationReference.isNullOrBlank()) {
                add("REVIEW_PROFESSIONAL_QUALIFICATION_MISSING")
            }
            if (professional?.decision == "APPROVED" && professional.evidenceReference.isNullOrBlank()) {
                add("REVIEW_PROFESSIONAL_EVIDENCE_MISSING")
            }
            if (professional?.decision == "APPROVED" && professional.reviewScope.isNullOrBlank()) {
                add("REVIEW_PROFESSIONAL_SCOPE_MISSING")
            }
            if (business?.decision == "APPROVED" && business.evidenceReference.isNullOrBlank()) {
                add("REVIEW_BUSINESS_EVIDENCE_MISSING")
            }
            if (business?.decision == "APPROVED" && business.reviewScope.isNullOrBlank()) {
                add("REVIEW_BUSINESS_SCOPE_MISSING")
            }
            if (professional?.decision == "APPROVED" && business?.decision == "APPROVED" && professional.reviewerId == business.reviewerId) {
                add("REVIEWERS_MUST_BE_DISTINCT")
            }
        }.distinct()
        return ScalePublicationReadiness(
            scaleId = scale.id,
            scaleContentHash = scaleHash,
            releaseFingerprint = releaseFingerprint,
            ready = blockers.isEmpty(),
            requiredCaseTypes = requiredTypes,
            cases = caseReadiness,
            professionalReview = professional,
            businessReview = business,
            blockers = blockers
        )
    }

    private fun packageBlockers(scale: ScaleDetail): List<String> {
        val pkg = packageRepository.find(scale.id)
        return buildList {
            val governance = pkg.governance
            if (governance == null) {
                add("GOVERNANCE_MISSING")
            } else {
                if (governance.governanceStatus != "APPROVED") add("GOVERNANCE_NOT_APPROVED")
                if (governance.sourceTitle.isNullOrBlank() && governance.citationText.isNullOrBlank()) add("SOURCE_REFERENCE_MISSING")
                if (governance.copyrightStatus !in setOf("AUTHORIZED", "PUBLIC_DOMAIN")) add("COPYRIGHT_NOT_CLEARED")
                if (governance.authorizationStatus !in setOf("AUTHORIZED", "NOT_REQUIRED")) add("AUTHORIZATION_NOT_CLEARED")
                if (governance.nonDiagnosticStatement.isNullOrBlank()) add("NON_DIAGNOSTIC_STATEMENT_MISSING")
            }
            val locales = setOf("zh-CN", "ja-JP", "en")
            locales.filterNot { locale -> pkg.translations.any { it.localeCode == locale && it.reviewStatus == "APPROVED" } }
                .forEach { add("SCALE_TRANSLATION_NOT_APPROVED:$it") }
            pkg.translations.filter { it.localeCode in locales && it.nonDiagnosticText.isNullOrBlank() }
                .forEach { add("NON_DIAGNOSTIC_TRANSLATION_MISSING:${it.localeCode}") }
            if (scale.highRiskWarningEnabled) {
                pkg.translations.filter { it.localeCode in locales && it.highRiskActionText.isNullOrBlank() }
                    .forEach { add("HIGH_RISK_ACTION_TRANSLATION_MISSING:${it.localeCode}") }
            }
            scale.dimensions.forEach { dimension ->
                locales.filterNot { locale -> pkg.dimensionTranslations.any { it.dimensionId == dimension.id && it.localeCode == locale && it.reviewStatus == "APPROVED" } }
                    .forEach { add("DIMENSION_TRANSLATION_NOT_APPROVED:${dimension.dimensionCode}:$it") }
            }
            scale.questions.forEach { question ->
                locales.filterNot { locale -> pkg.questionTranslations.any { it.questionId == question.id && it.localeCode == locale && it.reviewStatus == "APPROVED" } }
                    .forEach { add("QUESTION_TRANSLATION_NOT_APPROVED:${question.questionNo}:$it") }
                question.options.forEach { option ->
                    locales.filterNot { locale -> pkg.optionTranslations.any { it.optionId == option.id && it.localeCode == locale && it.reviewStatus == "APPROVED" } }
                        .forEach { add("OPTION_TRANSLATION_NOT_APPROVED:${question.questionNo}:${option.optionCode}:$it") }
                }
            }
            scale.resultRules.forEach { rule ->
                locales.filterNot { locale -> pkg.resultRuleTranslations.any { it.resultRuleId == rule.id && it.localeCode == locale && it.reviewStatus == "APPROVED" } }
                    .forEach { add("RESULT_TRANSLATION_NOT_APPROVED:${rule.id}:$it") }
            }
            if (scale.highRiskWarningEnabled) {
                scale.highRiskRules.forEach { rule ->
                    locales.filterNot { locale -> pkg.highRiskRuleTranslations.any { it.highRiskRuleId == rule.id && it.localeCode == locale && it.reviewStatus == "APPROVED" } }
                        .forEach { add("HIGH_RISK_RULE_TRANSLATION_NOT_APPROVED:${rule.ruleCode}:$it") }
                }
            }
            val quality = pkg.qualityPolicy
            if (quality == null) {
                add("QUALITY_POLICY_MISSING")
            } else if (quality.missingAnswerPolicy !in setOf("REJECT", "ALLOW", "PRORATE") ||
                quality.maxMissingRatio < BigDecimal.ZERO || quality.maxMissingRatio > BigDecimal.ONE ||
                quality.invalidResultAction !in setOf("INVALIDATE", "REQUIRE_REVIEW", "ALLOW_WITH_WARNING")
            ) {
                add("QUALITY_POLICY_RUNTIME_UNSUPPORTED")
            }
            val algorithm = pkg.algorithmBinding
            if (algorithm?.reviewStatus != "APPROVED") add("ALGORITHM_NOT_APPROVED")
            if (algorithm != null && !isRuntimeSupportedAlgorithm(algorithm)) {
                add("ALGORITHM_RUNTIME_UNSUPPORTED")
            }
            pkg.validityRules.filter { it.enabled }.forEach {
                if (it.reviewStatus != "APPROVED") add("VALIDITY_RULE_NOT_APPROVED:${it.ruleCode}")
                add("VALIDITY_RULE_RUNTIME_UNSUPPORTED:${it.ruleCode}")
            }
            pkg.normGovernance.filter {
                it.reviewStatus != "APPROVED" || it.sourceReference.isNullOrBlank() ||
                    it.normVersion.isNullOrBlank() || it.sampleSize == null
            }.forEach { add("NORM_NOT_APPROVED:${it.normId}") }
            scale.resultRules.filter { it.scoreSource in setOf("Z_SCORE", "T_SCORE") }.forEach { rule ->
                val matchingNorms = scale.norms.filter {
                    it.dimensionId == rule.dimensionId &&
                        (rule.normCode.isNullOrBlank() || it.normCode == rule.normCode)
                }
                if (matchingNorms.none {
                        it.reviewStatus == "APPROVED" &&
                            !it.sourceReference.isNullOrBlank() &&
                            !it.normVersion.isNullOrBlank() &&
                            (it.sampleSize ?: 0) > 0
                    }
                ) {
                    add("NORM_RUNTIME_NOT_READY:${rule.normCode ?: rule.dimensionId ?: "GLOBAL"}")
                }
            }
        }
    }

    private fun isRuntimeSupportedAlgorithm(algorithm: org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding): Boolean =
        (algorithm.implementationType == "BUILTIN" &&
            algorithm.algorithmCode == "GENERIC_SCORE_CALCULATOR" && algorithm.algorithmVersion == "1") ||
            (algorithm.implementationType == "RESTRICTED_EXTENSION" &&
                algorithm.algorithmCode == "SCL90_PROFILE" && algorithm.algorithmVersion == "1")

    private fun normalizeHistoryCursor(afterId: Long?): Long? {
        if (afterId != null && afterId <= 0) {
            throw BizException("SCALE_HISTORY_CURSOR_INVALID", messages.get("scale.publication.history_cursor_invalid"))
        }
        return afterId
    }

    private data class Evaluation(val actual: Map<String, Any?>, val differences: List<String>)
    private class GoldenCaseValidation(val code: String) : RuntimeException(code)

    private fun evaluate(scale: ScaleDetail, input: GoldenCaseInput, expected: GoldenCaseExpected): Evaluation {
        val actual = try {
            val contexts = buildQuestionContexts(scale, input)
            val qualityPolicy = packageRepository.find(scale.id).qualityPolicy
            val normContext = input.norm?.let {
                NormMatchingContext(it.age, it.gender, it.orgType, it.applicableTarget, it.preferredNormCode)
            }
            val score = if (qualityPolicy?.missingAnswerPolicy == "REJECT" || qualityPolicy == null) {
                scoreCalculator.calculate(
                    scale.id,
                    scale.scoreMethod,
                    scale.scoreCoefficient,
                    contexts,
                    normContext,
                    scale.highRiskWarningEnabled
                )
            } else {
                scoreCalculator.calculate(
                    scale.id,
                    scale.scoreMethod,
                    scale.scoreCoefficient,
                    contexts,
                    normContext,
                    scale.highRiskWarningEnabled,
                    options = ScoreCalculationOptions(
                        qualityPolicy = qualityPolicy,
                        totalQuestionCount = scale.questions.size,
                        answeredQuestionCount = contexts.map { it.questionId }.distinct().size,
                        totalWeight = scale.questions.fold(BigDecimal.ZERO) { total, question -> total + question.weightValue },
                        answeredWeight = contexts.fold(BigDecimal.ZERO) { total, item -> total + item.weightValue }
                    )
                )
            }
            val dimensionCodes = scale.dimensions.associate { it.id to it.dimensionCode }
            linkedMapOf<String, Any?>(
                "valid" to true,
                "errorCode" to null,
                "totalScore" to score.totalScore,
                "riskLevel" to score.riskLevel,
                "highRiskTriggered" to score.highRiskTriggered,
                "highRiskRuleCode" to score.highRiskRuleCode,
                "normCode" to score.normCode,
                "metrics" to score.metrics,
                "trace" to score.scoringTrace,
                "dimensions" to score.dimensionScores.associate { dimension ->
                    (dimensionCodes[dimension.dimensionId] ?: dimension.dimensionId.toString()) to mapOf(
                        "score" to dimension.score,
                        "riskLevel" to dimension.riskLevel,
                        "normCode" to dimension.normCode
                    )
                }
            )
        } catch (error: GoldenCaseValidation) {
            linkedMapOf("valid" to false, "errorCode" to error.code)
        } catch (_: Exception) {
            linkedMapOf("valid" to false, "errorCode" to "SCORING_ERROR")
        }
        return Evaluation(actual, compareExpected(expected, actual))
    }

    private fun buildQuestionContexts(scale: ScaleDetail, input: GoldenCaseInput): List<QuestionScoreContext> {
        val quality = packageRepository.find(scale.id).qualityPolicy
        if (input.answers.map { it.questionNo }.let { it.size != it.toSet().size }) throw GoldenCaseValidation("DUPLICATE_QUESTION")
        if (input.durationSeconds != null && input.durationSeconds < 0) throw GoldenCaseValidation("DURATION_INVALID")
        if (quality?.minimumDurationSeconds != null && (input.durationSeconds ?: 0) < quality.minimumDurationSeconds) {
            throw GoldenCaseValidation("DURATION_TOO_SHORT")
        }
        if (quality?.maximumDurationSeconds != null && input.durationSeconds != null && input.durationSeconds > quality.maximumDurationSeconds) {
            throw GoldenCaseValidation("DURATION_TOO_LONG")
        }
        val questionByNo = scale.questions.associateBy { it.questionNo }
        val answerByNo = input.answers.associateBy { it.questionNo }
        if (input.answers.any { it.questionNo !in questionByNo }) throw GoldenCaseValidation("QUESTION_NOT_FOUND")
        if (quality?.requireAllRequiredAnswers != false && scale.questions.any { it.requiredFlag && it.questionNo !in answerByNo }) {
            throw GoldenCaseValidation("MISSING_REQUIRED_ANSWER")
        }
        if (scale.questions.isNotEmpty()) {
            val missingRatio = BigDecimal(scale.questions.count { it.questionNo !in answerByNo })
                .divide(BigDecimal(scale.questions.size), 5, RoundingMode.HALF_UP)
            if (quality != null && missingRatio > quality.maxMissingRatio) {
                throw GoldenCaseValidation("MISSING_RATIO_EXCEEDED")
            }
        }
        val dimensionQuestionCounts = scale.questions
            .groupingBy { it.dimensionId }
            .eachCount()
        val dimensionWeightTotals = scale.questions
            .groupBy { it.dimensionId }
            .mapValues { (_, questions) -> questions.fold(BigDecimal.ZERO) { total, question -> total + question.weightValue } }
        return input.answers.map { answer ->
            val question = questionByNo.getValue(answer.questionNo)
            val optionCodes = answer.optionCodes.map(String::trim)
            if (optionCodes.size != optionCodes.toSet().size) throw GoldenCaseValidation("DUPLICATE_OPTION")
            val optionsByCode = question.options.associateBy { it.optionCode }
            if (optionCodes.any { it !in optionsByCode }) throw GoldenCaseValidation("OPTION_NOT_FOUND")
            val selected = optionCodes.map(optionsByCode::getValue)
            val rawScore = when (question.questionType) {
                "SINGLE_CHOICE", "MATRIX" -> {
                    if (selected.size != 1 || answer.answerValue != null) throw GoldenCaseValidation("SINGLE_CHOICE_INVALID")
                    selected.single().scoreValue
                }
                "MULTI_SELECT" -> {
                    if (selected.isEmpty() || answer.answerValue != null ||
                        (question.optionSelectionLimit != null && selected.size > question.optionSelectionLimit)
                    ) throw GoldenCaseValidation("MULTI_SELECT_INVALID")
                    if (selected.any { it.exclusiveFlag } && selected.size > 1) throw GoldenCaseValidation("EXCLUSIVE_OPTION_CONFLICT")
                    selected.fold(BigDecimal.ZERO) { sum, option -> sum + option.scoreValue }
                }
                "SLIDER" -> {
                    val value = answer.answerValue ?: throw GoldenCaseValidation("SLIDER_VALUE_REQUIRED")
                    if ((question.sliderMin != null && value < question.sliderMin) ||
                        (question.sliderMax != null && value > question.sliderMax)
                    ) throw GoldenCaseValidation("SLIDER_VALUE_OUT_OF_RANGE")
                    value
                }
                "TEXT" -> {
                    if (answer.answerText.isNullOrBlank()) throw GoldenCaseValidation("TEXT_ANSWER_REQUIRED")
                    BigDecimal.ZERO
                }
                "TEXT_WITH_OPTION" -> {
                    if (selected.size != 1 ||
                        (question.textInputEnabled && answer.answerText.isNullOrBlank()) ||
                        (!question.textInputEnabled && !answer.answerText.isNullOrBlank())
                    ) throw GoldenCaseValidation("TEXT_WITH_OPTION_INVALID")
                    selected.single().scoreValue
                }
                "TIME" -> {
                    if (selected.isNotEmpty() || answer.answerValue != null ||
                        answer.answerText?.matches(TIME_VALUE_PATTERN) != true
                    ) throw GoldenCaseValidation("TIME_ANSWER_INVALID")
                    BigDecimal.ZERO
                }
                else -> throw GoldenCaseValidation("QUESTION_TYPE_UNSUPPORTED")
            }
            QuestionScoreContext(
                questionId = question.id,
                dimensionId = question.dimensionId,
                reverseScoreFlag = question.reverseScoreFlag,
                weightValue = question.weightValue,
                rawScore = rawScore,
                selectedOptionIds = selected.map { it.id },
                answerText = answer.answerText,
                answerValue = answer.answerValue,
                dimensionQuestionCount = dimensionQuestionCounts[question.dimensionId],
                dimensionWeightTotal = dimensionWeightTotals[question.dimensionId]
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareExpected(expected: GoldenCaseExpected, actual: Map<String, Any?>): List<String> = buildList {
        if (actual["valid"] != expected.valid) add("valid: expected=${expected.valid}, actual=${actual["valid"]}")
        if (!expected.valid) {
            if (actual["errorCode"] != expected.errorCode) add("errorCode: expected=${expected.errorCode}, actual=${actual["errorCode"]}")
            return@buildList
        }
        compareDecimal("totalScore", expected.totalScore, actual["totalScore"])?.let(::add)
        if (actual["riskLevel"] != expected.riskLevel) add("riskLevel: expected=${expected.riskLevel}, actual=${actual["riskLevel"]}")
        expected.highRiskTriggered?.let { if (actual["highRiskTriggered"] != it) add("highRiskTriggered: expected=$it, actual=${actual["highRiskTriggered"]}") }
        expected.highRiskRuleCode?.let { if (actual["highRiskRuleCode"] != it) add("highRiskRuleCode: expected=$it, actual=${actual["highRiskRuleCode"]}") }
        expected.normCode?.let { if (actual["normCode"] != it) add("normCode: expected=$it, actual=${actual["normCode"]}") }
        val actualMetrics = actual["metrics"] as? Map<String, Any?> ?: emptyMap()
        expected.metrics.forEach { (code, value) ->
            compareDecimal("metric:$code", value, actualMetrics[code])?.let(::add)
        }
        expected.trace?.takeUnless { it.isNull || it.isMissingNode }?.let { expectedTrace ->
            val actualTrace = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(actual["trace"])
            if (actualTrace != expectedTrace) add("trace: expected and actual scoring evidence differ")
        }
        val actualDimensions = actual["dimensions"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        expected.dimensions.forEach { (code, dimension) ->
            val actualDimension = actualDimensions[code]
            if (actualDimension == null) {
                add("dimension:$code missing")
            } else {
                compareDecimal("dimension:$code:score", dimension.score, actualDimension["score"])?.let(::add)
                dimension.riskLevel?.let { if (actualDimension["riskLevel"] != it) add("dimension:$code:riskLevel expected=$it, actual=${actualDimension["riskLevel"]}") }
                dimension.normCode?.let { if (actualDimension["normCode"] != it) add("dimension:$code:normCode expected=$it, actual=${actualDimension["normCode"]}") }
            }
        }
    }

    private fun compareDecimal(name: String, expected: BigDecimal?, actual: Any?): String? {
        if (expected == null) return "$name: expected value is required"
        val actualDecimal = when (actual) {
            is BigDecimal -> actual
            is Number -> actual.toString().toBigDecimalOrNull()
            is String -> actual.toBigDecimalOrNull()
            else -> null
        }
        return if (actualDecimal == null || expected.compareTo(actualDecimal) != 0) "$name: expected=$expected, actual=$actual" else null
    }

    private fun validateCaseRequest(scale: ScaleDetail, code: String, type: String, request: CreateScaleGoldenCaseRequest) {
        if (!code.matches(Regex("[A-Z0-9][A-Z0-9_.-]{0,63}")) || type !in knownCaseTypes || request.sourceReference.isBlank()) {
            throw BizException("GOLDEN_CASE_INVALID", messages.get("scale.golden_case.invalid"))
        }
        if (request.expected.valid && (request.expected.totalScore == null || request.expected.riskLevel.isNullOrBlank())) {
            throw BizException("GOLDEN_CASE_EXPECTED_INVALID", messages.get("scale.golden_case.expected_invalid"))
        }
        if (!request.expected.valid && request.expected.errorCode.isNullOrBlank()) {
            throw BizException("GOLDEN_CASE_EXPECTED_INVALID", messages.get("scale.golden_case.expected_invalid"))
        }
        if (request.input.answers.any { it.questionNo <= 0 } || scale.questions.isEmpty()) {
            throw BizException("GOLDEN_CASE_INVALID", messages.get("scale.golden_case.invalid"))
        }
        val answeredNos = request.input.answers.map { it.questionNo }.toSet()
        when (type) {
            "NORMAL" -> if (!request.expected.valid) throw BizException("GOLDEN_CASE_EXPECTED_INVALID", messages.get("scale.golden_case.expected_invalid"))
            "BOUNDARY" -> if (!request.expected.valid || request.expected.totalScore == null ||
                scale.resultRules.none { it.dimensionId == null && (it.scoreMin.compareTo(request.expected.totalScore) == 0 || it.scoreMax.compareTo(request.expected.totalScore) == 0) }
            ) throw BizException("GOLDEN_CASE_BOUNDARY_INVALID", messages.get("scale.golden_case.boundary_invalid"))
            "REVERSE" -> if (!request.expected.valid || scale.questions.none { it.reverseScoreFlag && it.questionNo in answeredNos }) {
                throw BizException("GOLDEN_CASE_REVERSE_INVALID", messages.get("scale.golden_case.reverse_invalid"))
            }
            "MISSING" -> if (scale.questions.all { it.questionNo in answeredNos }) {
                throw BizException("GOLDEN_CASE_MISSING_INVALID", messages.get("scale.golden_case.missing_invalid"))
            }
            "INVALID" -> if (request.expected.valid) throw BizException("GOLDEN_CASE_EXPECTED_INVALID", messages.get("scale.golden_case.expected_invalid"))
            "HIGH_RISK" -> if (!request.expected.valid || request.expected.highRiskTriggered != true) {
                throw BizException("GOLDEN_CASE_HIGH_RISK_INVALID", messages.get("scale.golden_case.high_risk_invalid"))
            }
        }
    }

    private fun requireCurrentCase(scaleId: Long, caseId: Long): ScaleGoldenCase {
        val case = publicationRepository.findCase(caseId)
            ?.takeIf { it.scaleId == scaleId }
            ?: throw NotFoundBizException("GOLDEN_CASE_NOT_FOUND", messages.get("scale.golden_case.not_found"))
        if (publicationRepository.findLatestCase(scaleId, case.caseCode)?.id != case.id) {
            throw BizException("GOLDEN_CASE_REVISION_STALE", messages.get("scale.golden_case.revision_stale"))
        }
        return case
    }

    private fun requireDraftOwnedScale(scaleId: Long): ScaleDetail {
        if (!scaleRepository.lockById(scaleId)) {
            throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        val scale = requireOwnedScale(scaleId)
        if (scale.status != "DRAFT") throw BizException("SCALE_NOT_DRAFT", messages.get("scale.publish.draft_required"))
        return scale
    }

    private fun requireOwnedScale(scaleId: Long): ScaleDetail {
        val scale = scaleRepository.findDetailById(scaleId)
            ?: throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        if (!tenantAccessPolicy.canAccess(scale.tenantId, "SCALE_PUBLICATION", scaleId, "READ_OR_MUTATE")) {
            throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        return scale
    }

    companion object {
        /**
         * The full universe of valid Golden Case types accepted on input. This
         * is intentionally separate from [requiredCaseTypesForScale], which
         * derives the subset that a given scale must actually evidence before
         * publication.
         */
        val knownCaseTypes = setOf("NORMAL", "BOUNDARY", "REVERSE", "MISSING", "INVALID", "HIGH_RISK")
        /**
         * Golden evidence is capability-aware.  Every scale needs normal,
         * boundary, missing and invalid cases; reverse/high-risk cases are
         * required only when the scale actually exposes those behaviours.
         * This prevents a single six-case template from forcing unsupported
         * semantics onto instruments such as K6 while retaining the strict
         * checks for scales that do support them.
         */
        fun requiredCaseTypesForScale(scale: ScaleDetail): Set<String> = buildSet {
            add("NORMAL")
            add("BOUNDARY")
            add("MISSING")
            add("INVALID")
            if (scale.questions.any { it.reverseScoreFlag }) add("REVERSE")
            if (scale.highRiskWarningEnabled && scale.highRiskRules.isNotEmpty()) add("HIGH_RISK")
        }
        private val reviewTypes = setOf("PROFESSIONAL", "BUSINESS")
        private val reviewDecisions = setOf("APPROVED", "REJECTED")
        private val professionalRoles = setOf("COUNSELOR")
        private val businessRoles = setOf("ASSESSMENT_ADMIN", "ORG_MANAGER")
        private const val MAX_EVIDENCE_REFERENCE_LENGTH = 1_000
        private const val MAX_REVIEW_TEXT_LENGTH = 4_000
        private val TIME_VALUE_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
    }
}
