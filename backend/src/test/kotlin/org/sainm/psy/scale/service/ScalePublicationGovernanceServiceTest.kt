package org.sainm.psy.scale.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.assessment.service.ScoreCalculator
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.api.CursorPage
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.CreateScaleGoldenCaseRequest
import org.sainm.psy.scale.api.GoldenCaseExpected
import org.sainm.psy.scale.api.GoldenCaseInput
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScaleHighRiskRule
import org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageHighRiskRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageOptionTranslation
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
import org.sainm.psy.scale.domain.ScalePackageQuestionTranslation
import org.sainm.psy.scale.domain.ScalePackageResultRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.scale.domain.ScaleQuestion
import org.sainm.psy.scale.domain.ScaleQuestionOption
import org.sainm.psy.scale.domain.ScaleResultRule
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ScalePublicationGovernanceServiceTest {
    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var packageRepository: ScalePackageRepository
    @Mock private lateinit var publicationRepository: ScalePublicationRepository
    @Mock private lateinit var fingerprintService: ScaleContentFingerprintService
    @Mock private lateinit var scoreCalculator: ScoreCalculator
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private lateinit var service: ScalePublicationGovernanceService
    private val currentHash = "a".repeat(64)
    private val releaseHash = "b".repeat(64)
    private val admin = UserPrincipal(10, "admin", "Admin", UserStatus.ENABLED, null, 7, setOf("ASSESSMENT_ADMIN"))

    @BeforeEach
    fun setUp() {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        service = ScalePublicationGovernanceService(
            scaleRepository, packageRepository, publicationRepository, fingerprintService,
            scoreCalculator, currentUserFacade, jacksonObjectMapper(), LocalizedMessages(source), securityAuditService,
            tenantAccessPolicy
        )
        lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(admin)
        lenient().`when`(currentUserFacade.requireCurrentUserId()).thenReturn(admin.userId)
        lenient().`when`(fingerprintService.calculate(any())).thenReturn(currentHash)
        lenient().`when`(fingerprintService.sha256(any())).thenReturn(releaseHash)
        lenient().`when`(fingerprintService.calculateReleaseFingerprint(any(), any())).thenReturn(releaseHash)
        lenient().`when`(scaleRepository.lockById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true)
        lenient().whenever(
            tenantAccessPolicy.canAccess(org.mockito.kotlin.eq(7L), any<String>(), any<Long>(), any<String>())
        ).thenReturn(true)
    }

    @Test
    fun `readiness lists concrete blockers when governance and evidence are absent`() {
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale())
        whenever(packageRepository.find(1)).thenReturn(ScalePackageSnapshot(1))
        whenever(publicationRepository.findLatestCases(1)).thenReturn(emptyList())
        whenever(publicationRepository.findLatestReviews(1, releaseHash)).thenReturn(emptyMap())

        val readiness = service.readiness(1)

        assertEquals(false, readiness.ready)
        assertTrue("GOVERNANCE_MISSING" in readiness.blockers)
        assertTrue("GOLDEN_CASE_TYPE_MISSING:HIGH_RISK" in readiness.blockers)
        assertTrue("REVIEW_PROFESSIONAL_MISSING" in readiness.blockers)
    }

    @Test
    fun `fully governed passing case set and distinct approvals is publishable`() {
        val scale = scale()
        val cases = ScalePublicationGovernanceService.requiredCaseTypes.mapIndexed { index, type ->
            goldenCase((index + 1).toLong(), type)
        }
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale)
        whenever(packageRepository.find(1)).thenReturn(approvedPackage(scale))
        whenever(publicationRepository.findLatestCases(1)).thenReturn(cases)
        cases.forEach { case -> whenever(publicationRepository.findLatestRun(case.id)).thenReturn(passingRun(case.id)) }
        whenever(publicationRepository.findLatestReviews(1, releaseHash)).thenReturn(
            mapOf(
                "PROFESSIONAL" to review(1, "PROFESSIONAL", 20),
                "BUSINESS" to review(2, "BUSINESS", 30)
            )
        )

        val readiness = service.readiness(1)

        assertTrue(readiness.ready, readiness.blockers.joinToString())
        assertDoesNotThrow { service.assertReadyForPublication(scale, currentHash) }
    }

    @Test
    fun `high risk case cannot claim success without expected trigger`() {
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale())
        val error = assertThrows<BizException> {
            service.saveGoldenCase(
                1,
                CreateScaleGoldenCaseRequest(
                    caseCode = "HIGH-1",
                    caseType = "HIGH_RISK",
                    sourceReference = "manual section 1",
                    input = GoldenCaseInput(emptyList()),
                    expected = GoldenCaseExpected(valid = true, totalScore = BigDecimal.ZERO, riskLevel = "NORMAL")
                )
            )
        }
        assertEquals("GOLDEN_CASE_HIGH_RISK_INVALID", error.code)
    }

    @Test
    fun `high risk rule requires approved translations in every supported locale`() {
        val riskRule = ScaleHighRiskRule(
            id = 401, scaleId = 1, ruleCode = "SELF_HARM", questionId = 101, questionNo = 1,
            optionId = 202, optionCode = "B", scoreThreshold = null, warningLevel = "HIGH",
            resultTitle = "High risk", resultDescription = null, suggestionText = null, sortNo = 1
        )
        val scale = scale().copy(highRiskWarningEnabled = true, highRiskRules = listOf(riskRule))
        val packageWithoutRuleTranslations = approvedPackage(scale).let { pkg ->
            pkg.copy(translations = pkg.translations.map { it.copy(highRiskActionText = "Escalate") })
        }
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale)
        whenever(packageRepository.find(1)).thenReturn(packageWithoutRuleTranslations)
        whenever(publicationRepository.findLatestCases(1)).thenReturn(emptyList())
        whenever(publicationRepository.findLatestReviews(1, releaseHash)).thenReturn(emptyMap())

        val missing = service.readiness(1)
        assertTrue("HIGH_RISK_RULE_TRANSLATION_NOT_APPROVED:SELF_HARM:ja-JP" in missing.blockers)

        whenever(packageRepository.find(1)).thenReturn(
            packageWithoutRuleTranslations.copy(
                highRiskRuleTranslations = listOf("zh-CN", "ja-JP", "en").map {
                    ScalePackageHighRiskRuleTranslation(401, it, "High risk", reviewStatus = "APPROVED")
                }
            )
        )
        val translated = service.readiness(1)
        assertTrue(translated.blockers.none { it.startsWith("HIGH_RISK_RULE_TRANSLATION_NOT_APPROVED") })
    }

    @Test
    fun `history returns every revision with runs and publication reviews after tenant ownership check`() {
        val first = goldenCase(1, "NORMAL")
        val second = first.copy(id = 2, revisionNo = 2, caseContentHash = "d".repeat(64))
        val firstRun = passingRun(first.id)
        val secondRun = passingRun(second.id)
        val publicationReview = review(9, "PROFESSIONAL", 20)
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale())
        whenever(publicationRepository.findAllCases(1)).thenReturn(listOf(second, first))
        whenever(publicationRepository.findAllRuns(1)).thenReturn(listOf(secondRun, firstRun))
        whenever(publicationRepository.findAllReviews(1)).thenReturn(listOf(publicationReview))

        val history = service.history(1)

        assertEquals(listOf(2L, 1L), history.cases.map { it.goldenCase.id })
        assertEquals(listOf(secondRun.id), history.cases.first().runs.map { it.id })
        assertEquals(listOf(firstRun.id), history.cases.last().runs.map { it.id })
        assertEquals(listOf(publicationReview), history.reviews)
    }

    @Test
    fun `history hides a cross tenant scale and never queries evidence`() {
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale().copy(tenantId = 8))

        val error = assertThrows<BizException> { service.history(1) }

        assertEquals("SCALE_NOT_FOUND", error.code)
        verify(publicationRepository, org.mockito.Mockito.never()).findAllCases(any())
        verify(publicationRepository, org.mockito.Mockito.never()).findAllRuns(any())
        verify(publicationRepository, org.mockito.Mockito.never()).findAllReviews(any())
    }

    @Test
    fun `cursor history endpoints preserve ownership and reject invalid cursors`() {
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale())
        val case = goldenCase(11, "NORMAL")
        whenever(publicationRepository.findCaseHistoryPage(1, 5, 3)).thenReturn(CursorPage(listOf(case), null, 3))

        val page = service.historyCases(1, 5, 3)

        assertEquals(listOf(case), page.list)
        assertEquals(3, page.limit)
        verify(publicationRepository).findCaseHistoryPage(1, 5, 3)
        val error = assertThrows<BizException> { service.historyRuns(1, 0, 3) }
        assertEquals("SCALE_HISTORY_CURSOR_INVALID", error.code)
        verify(publicationRepository, org.mockito.Mockito.never()).findRunHistoryPage(any(), any(), any())
    }

    private fun scale(): ScaleDetail {
        val question = ScaleQuestion(
            id = 101, scaleId = 1, dimensionId = null, questionNo = 1, questionTitle = "Q1",
            questionType = "SINGLE_CHOICE", requiredFlag = true, reverseScoreFlag = true,
            weightValue = BigDecimal.ONE, optionSelectionLimit = null, sliderMin = null, sliderMax = null,
            sliderStep = null, textInputEnabled = false, textInputPlaceholder = null, matrixGroupCode = null,
            rowCode = null, columnCode = null, sortNo = 1,
            options = listOf(
                ScaleQuestionOption(201, 101, "A", "No", BigDecimal.ZERO, false, null, 1),
                ScaleQuestionOption(202, 101, "B", "Yes", BigDecimal.ONE, false, null, 2)
            )
        )
        return ScaleDetail(
            id = 1, scaleCode = "TEST", scaleName = "Test", description = null, applicableTarget = null,
            versionNo = "1", versionGroupId = 1, currentVersionFlag = false, status = "DRAFT",
            scoreMethod = "SIMPLE_SUM", scoreCoefficient = BigDecimal.ONE, normStrategy = "RAW_SCORE",
            normDefaultGroup = null, highRiskWarningEnabled = false, anonymousSupported = false,
            reportTemplate = null, createdBy = 99, createdAt = LocalDateTime.now(), updatedBy = 99,
            updatedAt = LocalDateTime.now(), dimensions = emptyList(), questions = listOf(question),
            resultRules = listOf(ScaleResultRule(301, 1, null, "NORMAL", BigDecimal.ZERO, BigDecimal.ONE, "RAW_SCORE", null, "Normal", null, null)),
            norms = emptyList(), tenantId = 7
        )
    }

    private fun approvedPackage(scale: ScaleDetail): ScalePackageSnapshot {
        val locales = listOf("zh-CN", "ja-JP", "en")
        return ScalePackageSnapshot(
            scaleId = scale.id,
            governance = ScalePackageGovernance(
                sourceTitle = "Licensed manual", copyrightStatus = "AUTHORIZED",
                authorizationStatus = "AUTHORIZED", nonDiagnosticStatement = "Screening only",
                governanceStatus = "APPROVED"
            ),
            translations = locales.map {
                ScalePackageTranslation(it, "Scale", nonDiagnosticText = "Screening only", reviewStatus = "APPROVED")
            },
            questionTranslations = locales.map { ScalePackageQuestionTranslation(101, it, "Question", reviewStatus = "APPROVED") },
            optionTranslations = locales.flatMap { locale ->
                listOf(
                    ScalePackageOptionTranslation(201, locale, "No", "APPROVED"),
                    ScalePackageOptionTranslation(202, locale, "Yes", "APPROVED")
                )
            },
            resultRuleTranslations = locales.map { ScalePackageResultRuleTranslation(301, it, "Normal", reviewStatus = "APPROVED") },
            qualityPolicy = ScalePackageQualityPolicy(),
            algorithmBinding = ScalePackageAlgorithmBinding("GENERIC_SCORE_CALCULATOR", "1", "BUILTIN", reviewStatus = "APPROVED")
        )
    }

    private fun goldenCase(id: Long, type: String) = ScaleGoldenCase(
        id, 1, "$type-1", 1, type, "manual", currentHash, "$id".padStart(64, 'c').takeLast(64),
        "{}", "{}", 10, LocalDateTime.now(), 20, LocalDateTime.now()
    )

    private fun passingRun(caseId: Long) = ScaleGoldenCaseRun(
        caseId, caseId, currentHash, "c".repeat(64), "GENERIC_SCORE_CALCULATOR", "1",
        true, "{}", "[]", 10, LocalDateTime.now()
    )

    private fun review(id: Long, type: String, reviewerId: Long) = ScalePublicationReview(
        id, type, "APPROVED", reviewerId, if (type == "PROFESSIONAL") "COUNSELOR" else "ASSESSMENT_ADMIN",
        currentHash, releaseHash, null, LocalDateTime.now()
    )
}
