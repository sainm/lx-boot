package org.sainm.psy.scale.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.BatchCreateResponse
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.api.UpdateScalePackageRequest
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseHistory
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScaleImportJobRecord
import org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePackageValidityRule
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ScalePackageImportServiceTest {
    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var packageRepository: ScalePackageRepository
    @Mock private lateinit var publicationRepository: ScalePublicationRepository
    @Mock private lateinit var importRepository: ScaleImportRepository
    @Mock private lateinit var visualizationService: VisualizationService
    @Mock private lateinit var fingerprintService: ScaleContentFingerprintService
    @Mock private lateinit var integrityService: ScalePackageExportIntegrityService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var transactionTemplate: TransactionTemplate
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var service: ScalePackageImportService

    @Test
    fun `confirmation creates a draft and resets external approvals while discarding runs and reviews`() {
        setUpService()
        val document = document()
        whenever(importRepository.findJobById(99, 7)).thenReturn(job(objectMapper.writeValueAsString(document)))
        whenever(fingerprintService.calculateReleaseFingerprint(scaleHash, listOf(document.goldenCases.single().goldenCase))).thenReturn(releaseHash)
        whenever(integrityService.calculate(eq(scaleHash), eq(releaseHash), any(), any(), any(), any())).thenReturn(payloadHash)
        whenever(importRepository.claimForConfirmation(99, 7, "PACKAGE_CREATE_ONLY")).thenReturn(true)
        whenever(scaleRepository.existsByScaleCode("TEST", 7)).thenReturn(false)
        whenever(scaleRepository.create(any(), eq(10))).thenReturn(100)
        whenever(scaleRepository.createDimensions(eq(100), any())).thenReturn(BatchCreateResponse(emptyList()))
        whenever(scaleRepository.createQuestions(eq(100), any())).thenReturn(BatchCreateResponse(emptyList()))
        whenever(scaleRepository.createResultRules(eq(100), any())).thenReturn(BatchCreateResponse(emptyList()))
        whenever(scaleRepository.createNorms(eq(100), any())).thenReturn(BatchCreateResponse(emptyList()))
        whenever(scaleRepository.createHighRiskRules(eq(100), any())).thenReturn(BatchCreateResponse(emptyList()))
        whenever(scaleRepository.findDimensionCodeIdMapByScaleId(100)).thenReturn(emptyMap())
        whenever(scaleRepository.findQuestionNoIdMapByScaleId(100)).thenReturn(emptyMap())
        whenever(scaleRepository.findOptionIdMapByScaleId(100)).thenReturn(emptyMap())
        whenever(scaleRepository.findDetailById(100)).thenReturn(scale().copy(id = 100, tenantId = 7))
        whenever(fingerprintService.calculate(any())).thenReturn(targetHash)
        whenever(fingerprintService.sha256(any())).thenReturn(caseHash)
        whenever(publicationRepository.saveCaseRevision(eq(100), any(), any(), any(), eq(targetHash), eq(caseHash), any(), any(), eq(10)))
            .thenReturn(document.goldenCases.single().goldenCase.copy(id = 501, scaleId = 100, scaleContentHash = targetHash, caseContentHash = caseHash, approvedBy = null, approvedAt = null))

        val result = service.confirm(99)

        assertEquals(100, result.scaleId)
        assertEquals(1, result.importedGoldenCaseRevisionCount)
        assertEquals(1, result.discardedGoldenCaseRunCount)
        assertEquals(1, result.discardedPublicationReviewCount)
        val request = argumentCaptor<UpdateScalePackageRequest>()
        verify(packageRepository).replace(eq(100), request.capture(), eq(10))
        assertEquals("PENDING_REVIEW", request.firstValue.governance?.authorizationStatus)
        assertEquals("PENDING_REVIEW", request.firstValue.governance?.copyrightStatus)
        assertEquals("DRAFT", request.firstValue.governance?.governanceStatus)
        assertEquals(setOf("DRAFT"), request.firstValue.translations.map { it.reviewStatus }.toSet())
        assertEquals(setOf("DRAFT"), request.firstValue.validityRules.map { it.reviewStatus }.toSet())
        assertEquals("DRAFT", request.firstValue.algorithmBinding?.reviewStatus)
        verify(importRepository).markSuccess(99, 100)
        verify(securityAuditService).recordScalePackageImported(99, 100, payloadHash, 1, 1, 1)
        verify(publicationRepository, never()).saveRun(any(), any(), any(), any(), any(), any(), any())
        verify(publicationRepository, never()).saveReview(any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `cross tenant or missing import remains indistinguishable and creates nothing`() {
        setUpService()
        whenever(importRepository.findJobById(99, 7)).thenReturn(null)

        val error = assertThrows<BizException> { service.confirm(99) }

        assertEquals("SCALE_IMPORT_JOB_NOT_FOUND", error.code)
        verify(scaleRepository, never()).create(any(), any())
    }

    private fun setUpService() {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        lenient().doAnswer { invocation ->
            invocation.getArgument<TransactionCallback<Any?>>(0).doInTransaction(org.mockito.Mockito.mock(TransactionStatus::class.java))
        }.`when`(transactionTemplate).execute<Any?>(org.mockito.ArgumentMatchers.any())
        lenient().whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        service = ScalePackageImportService(
            scaleRepository, packageRepository, publicationRepository, importRepository, visualizationService,
            fingerprintService, integrityService, currentUserFacade, LocalizedMessages(source), objectMapper,
            transactionTemplate, securityAuditService, tenantAccessPolicy
        )
        lenient().whenever(tenantAccessPolicy.requireTenantId()).thenReturn(7)
    }

    private fun document(): ScalePackageExportDocument {
        val golden = ScaleGoldenCase(
            11, 1, "NORMAL", 1, "NORMAL", "manual", scaleHash, caseHash,
            "{\"answers\":[]}", "{\"valid\":true}", 20, LocalDateTime.of(2026, 8, 8, 10, 0), 21, LocalDateTime.of(2026, 8, 8, 11, 0)
        )
        val run = ScaleGoldenCaseRun(12, 11, scaleHash, caseHash, "GENERIC_SCORE_CALCULATOR", "1", true, "{}", "[]", 20, LocalDateTime.of(2026, 8, 8, 10, 30))
        val review = ScalePublicationReview(13, "PROFESSIONAL", "APPROVED", 21, "COUNSELOR", scaleHash, releaseHash, "approved", LocalDateTime.of(2026, 8, 8, 11, 30))
        return ScalePackageExportDocument(
            exportId = "export-1", exportedAt = Instant.parse("2026-08-08T12:00:00Z"), exportedBy = 20,
            scaleContentHash = scaleHash, releaseFingerprint = releaseHash, payloadHash = payloadHash,
            scale = scale(),
            scalePackage = ScalePackageSnapshot(
                scaleId = 1,
                governance = ScalePackageGovernance(sourceTitle = "Manual", copyrightStatus = "AUTHORIZED", authorizationStatus = "AUTHORIZED", governanceStatus = "APPROVED"),
                translations = listOf(ScalePackageTranslation("en", "Test", reviewStatus = "APPROVED")),
                validityRules = listOf(ScalePackageValidityRule("V1", "CONSISTENCY", "1", reviewStatus = "APPROVED")),
                algorithmBinding = ScalePackageAlgorithmBinding("GENERIC_SCORE_CALCULATOR", "1", "BUILTIN", reviewStatus = "APPROVED")
            ),
            goldenCases = listOf(ScaleGoldenCaseHistory(golden, listOf(run))), publicationReviews = listOf(review)
        )
    }

    private fun scale() = ScaleDetail(
        id = 1, scaleCode = "TEST", scaleName = "Test", description = null, applicableTarget = null,
        versionNo = "v1", versionGroupId = 1, currentVersionFlag = true, status = "PUBLISHED",
        scoreMethod = "SIMPLE_SUM", scoreCoefficient = BigDecimal.ONE, normStrategy = "RAW_SCORE",
        normDefaultGroup = null, highRiskWarningEnabled = false, anonymousSupported = false, reportTemplate = null,
        createdBy = 20, createdAt = LocalDateTime.of(2026, 8, 8, 9, 0), updatedBy = 20,
        updatedAt = LocalDateTime.of(2026, 8, 8, 9, 0), dimensions = emptyList(), questions = emptyList(),
        resultRules = emptyList(), norms = emptyList(), tenantId = 8
    )

    private fun job(json: String) = ScaleImportJobRecord(
        99, 7, "package.json", "PACKAGE_CREATE_ONLY", true, "PARSED", "{}", json,
        0, 1, null, 10, LocalDateTime.now(), null, null, LocalDateTime.now(), LocalDateTime.now()
    )

    private val user = UserPrincipal(
        userId = 10,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        groupId = null,
        tenantId = 7,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )
    private val scaleHash = "a".repeat(64)
    private val releaseHash = "b".repeat(64)
    private val payloadHash = "c".repeat(64)
    private val targetHash = "d".repeat(64)
    private val caseHash = "e".repeat(64)
}
