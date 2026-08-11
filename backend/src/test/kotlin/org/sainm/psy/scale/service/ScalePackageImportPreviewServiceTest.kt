package org.sainm.psy.scale.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ScalePackageImportPreviewServiceTest {
    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var scaleImportRepository: ScaleImportRepository
    @Mock private lateinit var fingerprintService: ScaleContentFingerprintService
    @Mock private lateinit var integrityService: ScalePackageExportIntegrityService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var service: ScalePackageImportPreviewService

    @BeforeEach
    fun setUp() {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        service = ScalePackageImportPreviewService(
            scaleRepository, scaleImportRepository, fingerprintService, integrityService, currentUserFacade, LocalizedMessages(source), objectMapper, securityAuditService,
            tenantAccessPolicy
        )
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        whenever(tenantAccessPolicy.requireTenantId()).thenReturn(7)
        whenever(scaleImportRepository.createJob(any(), eq("PACKAGE_CREATE_ONLY"), eq(true), eq(10), eq(7))).thenReturn(99)
    }

    @Test
    fun `valid exported package is persisted and ready for controlled confirmation`() {
        whenever(scaleRepository.existsByScaleCode("TEST", 7)).thenReturn(false)
        whenever(fingerprintService.calculateReleaseFingerprint(scaleHash, emptyList())).thenReturn(releaseHash)
        whenever(integrityService.calculate(eq(scaleHash), eq(releaseHash), any(), any(), any(), any())).thenReturn(payloadHash)

        val result = service.preview(jsonFile(document()))

        assertTrue(result.readyForControlledImport)
        assertTrue(result.confirmationSupported)
        assertEquals(99, result.importId)
        assertEquals(0, result.errorCount)
        assertEquals(0, result.warningCount)
        assertEquals("TEST", result.scaleCode)
        verify(securityAuditService).runCatchingAudit(eq("PSY_SCALE_PACKAGE_IMPORT_PREVIEWED"), any())
    }

    @Test
    fun `schema v1 package remains accepted during v2 rollout`() {
        whenever(scaleRepository.existsByScaleCode("TEST", 7)).thenReturn(false)
        whenever(fingerprintService.calculateReleaseFingerprint(scaleHash, emptyList())).thenReturn(releaseHash)
        whenever(integrityService.calculate(eq(scaleHash), eq(releaseHash), any(), any(), any(), any())).thenReturn(payloadHash)

        val result = service.preview(jsonFile(document().copy(schemaVersion = 1)))

        assertTrue(result.readyForControlledImport)
        assertEquals(1, result.schemaVersion)
    }

    @Test
    fun `invalid JSON is reported as preview evidence instead of creating an import`() {
        val result = service.preview(MockMultipartFile("file", "broken.json", "application/json", "{".toByteArray()))

        assertFalse(result.readyForControlledImport)
        assertEquals("PACKAGE_JSON_INVALID", result.errors.single().errorCode)
        verify(securityAuditService).runCatchingAudit(eq("PSY_SCALE_PACKAGE_IMPORT_PREVIEWED"), any())
    }

    @Test
    fun `conflicting scale and changed release fingerprint block controlled import`() {
        whenever(scaleRepository.existsByScaleCode("TEST", 7)).thenReturn(true)
        whenever(fingerprintService.calculateReleaseFingerprint(scaleHash, emptyList())).thenReturn("d".repeat(64))
        whenever(integrityService.calculate(eq(scaleHash), eq(releaseHash), any(), any(), any(), any())).thenReturn(payloadHash)

        val result = service.preview(jsonFile(document()))

        assertFalse(result.readyForControlledImport)
        assertEquals(setOf("SCALE_CODE_CONFLICT", "PACKAGE_RELEASE_FINGERPRINT_MISMATCH"), result.errors.map { it.errorCode }.toSet())
    }

    @Test
    fun `database decimal scale survives exported JSON preview integrity validation`() {
        val actualFingerprint = ScaleContentFingerprintService(mock<ScalePackageRepository>(), mock<VisualizationService>())
        val actualIntegrity = ScalePackageExportIntegrityService(objectMapper, actualFingerprint)
        val base = document().copy(
            scale = document().scale.copy(scoreCoefficient = BigDecimal("1.0000")),
            releaseFingerprint = actualFingerprint.calculateReleaseFingerprint(scaleHash, emptyList())
        )
        val exported = base.copy(
            payloadHash = actualIntegrity.calculate(
                base.scaleContentHash, base.releaseFingerprint, base.scale, base.scalePackage,
                base.goldenCases, base.publicationReviews
            )
        )
        val previewService = ScalePackageImportPreviewService(
            scaleRepository, scaleImportRepository, actualFingerprint, actualIntegrity, currentUserFacade,
            localizedMessages(), objectMapper, securityAuditService, tenantAccessPolicy
        )
        whenever(scaleRepository.existsByScaleCode("TEST", 7)).thenReturn(false)

        val result = previewService.preview(jsonFile(exported))

        assertTrue(result.confirmationSupported)
        assertFalse(result.errors.any { it.errorCode == "PACKAGE_PAYLOAD_HASH_MISMATCH" })
    }

    private fun jsonFile(document: ScalePackageExportDocument) = MockMultipartFile(
        "file", "TEST-v1-scale-package-v1.json", "application/vnd.psy-scale-package+json", objectMapper.writeValueAsBytes(document)
    )

    private fun localizedMessages(): LocalizedMessages {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        return LocalizedMessages(source)
    }

    private fun document() = ScalePackageExportDocument(
        exportId = "export-1",
        exportedAt = Instant.parse("2026-08-08T12:00:00Z"),
        exportedBy = 10,
        scaleContentHash = scaleHash,
        releaseFingerprint = releaseHash,
        payloadHash = payloadHash,
        scale = ScaleDetail(
            id = 1, scaleCode = "TEST", scaleName = "Test", description = null, applicableTarget = null,
            versionNo = "v1", versionGroupId = 1, currentVersionFlag = false, status = "DRAFT",
            scoreMethod = "SIMPLE_SUM", scoreCoefficient = BigDecimal.ONE, normStrategy = "RAW_SCORE",
            normDefaultGroup = null, highRiskWarningEnabled = false, anonymousSupported = false, reportTemplate = null,
            createdBy = 10, createdAt = LocalDateTime.of(2026, 8, 8, 9, 0), updatedBy = 10,
            updatedAt = LocalDateTime.of(2026, 8, 8, 9, 0), dimensions = emptyList(), questions = emptyList(),
            resultRules = emptyList(), norms = emptyList(), tenantId = 7
        ),
        scalePackage = ScalePackageSnapshot(
            scaleId = 1,
            governance = ScalePackageGovernance(sourceTitle = "Manual", authorizationStatus = "AUTHORIZED"),
            translations = listOf("zh-CN", "ja-JP", "en").map { ScalePackageTranslation(it, "Test") }
        ),
        goldenCases = emptyList(),
        publicationReviews = emptyList()
    )

    private val user = UserPrincipal(
        userId = 10, username = "admin", displayName = "Admin", status = UserStatus.ENABLED,
        tenantId = 7, groupId = null, roles = setOf("ASSESSMENT_ADMIN"), permissions = emptySet()
    )
    private val scaleHash = "a".repeat(64)
    private val releaseHash = "b".repeat(64)
    private val payloadHash = "c".repeat(64)
}
