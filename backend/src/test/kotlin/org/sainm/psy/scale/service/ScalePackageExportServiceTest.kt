package org.sainm.psy.scale.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.lenient
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class ScalePackageExportServiceTest {
    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var packageRepository: ScalePackageRepository
    @Mock private lateinit var publicationRepository: ScalePublicationRepository
    @Mock private lateinit var visualizationService: VisualizationService
    @Mock private lateinit var fingerprintService: ScaleContentFingerprintService
    @Mock private lateinit var integrityService: ScalePackageExportIntegrityService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val clock = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var service: ScalePackageExportService

    @BeforeEach
    fun setUp() {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        service = ScalePackageExportService(
            scaleRepository, packageRepository, publicationRepository, visualizationService,
            fingerprintService, integrityService, currentUserFacade, LocalizedMessages(source), objectMapper,
            securityAuditService, clock, tenantAccessPolicy
        )
        lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        lenient().whenever(
            tenantAccessPolicy.canAccess(eq(7L), any<String>(), any<Long>(), any<String>())
        ).thenReturn(true)
    }

    @Test
    fun `export contains versioned scale package and append only publication evidence`() {
        val scale = scale()
        val scalePackage = ScalePackageSnapshot(
            scaleId = 1,
            algorithmBinding = ScalePackageAlgorithmBinding("GENERIC_SCORE_CALCULATOR", "1", "BUILTIN")
        )
        val goldenCase = ScaleGoldenCase(
            11, 1, "NORMAL-1", 2, "NORMAL", "manual p1", scaleHash, caseHash,
            "{}", "{}", 10, LocalDateTime.of(2026, 8, 8, 10, 0), 20, LocalDateTime.of(2026, 8, 8, 11, 0)
        )
        val run = ScaleGoldenCaseRun(
            21, 11, scaleHash, caseHash, "GENERIC_SCORE_CALCULATOR", "1", true,
            "{}", "[]", 10, LocalDateTime.of(2026, 8, 8, 10, 30)
        )
        val review = ScalePublicationReview(
            31, "PROFESSIONAL", "APPROVED", 20, "COUNSELOR", scaleHash, releaseHash,
            "checked", LocalDateTime.of(2026, 8, 8, 11, 30)
        )
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale)
        whenever(visualizationService.findConfigs(1)).thenReturn(emptyList())
        whenever(packageRepository.find(1)).thenReturn(scalePackage)
        whenever(publicationRepository.findAllCases(1)).thenReturn(listOf(goldenCase))
        whenever(publicationRepository.findAllRuns(1)).thenReturn(listOf(run))
        whenever(publicationRepository.findAllReviews(1)).thenReturn(listOf(review))
        whenever(fingerprintService.calculate(any())).thenReturn(scaleHash)
        whenever(fingerprintService.calculateReleaseFingerprint(scaleHash, listOf(goldenCase))).thenReturn(releaseHash)
        whenever(integrityService.calculate(eq(scaleHash), eq(releaseHash), eq(scale), eq(scalePackage), any(), eq(listOf(review)))).thenReturn(payloadHash)
        whenever(currentUserFacade.requireCurrentUserId()).thenReturn(10)

        val artifact = service.export(1)
        val json = objectMapper.readTree(artifact.bytes)

        assertEquals("application/vnd.psy-scale-package+json", artifact.contentType)
        assertEquals("TEST-v1-scale-package-v2.json", artifact.fileName)
        assertEquals("PSY_SCALE_PACKAGE", json["format"].asText())
        assertEquals(2, json["schemaVersion"].asInt())
        assertEquals(scaleHash, json["scaleContentHash"].asText())
        assertEquals(releaseHash, json["releaseFingerprint"].asText())
        assertEquals(payloadHash, json["payloadHash"].asText())
        assertEquals("GENERIC_SCORE_CALCULATOR", json["scalePackage"]["algorithmBinding"]["algorithmCode"].asText())
        assertEquals(2, json["goldenCases"][0]["goldenCase"]["revisionNo"].asInt())
        assertEquals(21, json["goldenCases"][0]["runs"][0]["id"].asInt())
        assertEquals("APPROVED", json["publicationReviews"][0]["decision"].asText())
        assertEquals("2026-08-08T12:00:00Z", json["exportedAt"].asText())
        assertTrue(json["exportId"].asText().isNotBlank())
        verify(securityAuditService).recordScalePackageExported(
            eq(1), any(), eq(scaleHash), eq(releaseHash), eq(2), eq(1), eq(1), eq(1)
        )
    }

    @Test
    fun `cross tenant export is hidden before package or evidence is queried`() {
        whenever(scaleRepository.findDetailById(1)).thenReturn(scale().copy(tenantId = 9))

        val error = assertThrows<BizException> { service.export(1) }

        assertEquals("SCALE_NOT_FOUND", error.code)
        verify(packageRepository, never()).find(any())
        verify(publicationRepository, never()).findAllCases(any())
        verify(securityAuditService, never()).recordScalePackageExported(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `integrity payload survives JSON round trip with database decimal scale`() {
        val fingerprint = ScaleContentFingerprintService(packageRepository, visualizationService)
        val integrity = ScalePackageExportIntegrityService(objectMapper, fingerprint)
        val sourceScale = scale().copy(scoreCoefficient = BigDecimal("1.0000"))
        val scalePackage = ScalePackageSnapshot(scaleId = sourceScale.id)
        val calculated = integrity.calculate(scaleHash, releaseHash, sourceScale, scalePackage, emptyList(), emptyList())
        val document = ScalePackageExportDocument(
            exportId = "round-trip",
            exportedAt = Instant.parse("2026-08-08T12:00:00Z"),
            exportedBy = 10,
            scaleContentHash = scaleHash,
            releaseFingerprint = releaseHash,
            payloadHash = calculated,
            scale = sourceScale,
            scalePackage = scalePackage,
            goldenCases = emptyList(),
            publicationReviews = emptyList()
        )

        val decoded = objectMapper.readValue(objectMapper.writeValueAsBytes(document), ScalePackageExportDocument::class.java)
        val recalculated = integrity.calculate(
            decoded.scaleContentHash, decoded.releaseFingerprint, decoded.scale, decoded.scalePackage,
            decoded.goldenCases, decoded.publicationReviews
        )

        assertEquals(calculated, recalculated)
    }

    private fun scale() = ScaleDetail(
        id = 1,
        scaleCode = "TEST",
        scaleName = "Test",
        description = null,
        applicableTarget = null,
        versionNo = "v1",
        versionGroupId = 1,
        currentVersionFlag = false,
        status = "DRAFT",
        scoreMethod = "SIMPLE_SUM",
        scoreCoefficient = BigDecimal.ONE,
        normStrategy = "RAW_SCORE",
        normDefaultGroup = null,
        highRiskWarningEnabled = false,
        anonymousSupported = false,
        reportTemplate = null,
        createdBy = 10,
        createdAt = LocalDateTime.of(2026, 8, 8, 9, 0),
        updatedBy = 10,
        updatedAt = LocalDateTime.of(2026, 8, 8, 9, 0),
        dimensions = emptyList(),
        questions = emptyList(),
        resultRules = emptyList(),
        norms = emptyList(),
        tenantId = 7
    )

    private val user = UserPrincipal(
        userId = 10,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 7,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )

    private val scaleHash = "a".repeat(64)
    private val caseHash = "b".repeat(64)
    private val releaseHash = "c".repeat(64)
    private val payloadHash = "d".repeat(64)
}
