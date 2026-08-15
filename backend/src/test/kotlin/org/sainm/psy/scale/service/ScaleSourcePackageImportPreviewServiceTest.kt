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
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.ScaleSourcePackageDocument
import org.sainm.psy.scale.api.SourceAlgorithmBinding
import org.sainm.psy.scale.api.SourceDimension
import org.sainm.psy.scale.api.SourceDimensionTranslation
import org.sainm.psy.scale.api.SourceGoldenCase
import org.sainm.psy.scale.api.SourceGovernance
import org.sainm.psy.scale.api.SourceOption
import org.sainm.psy.scale.api.SourceQuestion
import org.sainm.psy.scale.api.SourceQuestionTranslation
import org.sainm.psy.scale.api.SourceReference
import org.sainm.psy.scale.api.SourceResultRule
import org.sainm.psy.scale.api.SourceResultRuleTranslation
import org.sainm.psy.scale.api.SourceScale
import org.sainm.psy.scale.api.SourceScaleTranslation
import org.sainm.psy.scale.api.SourceScoring
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class ScaleSourcePackageImportPreviewServiceTest {
    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var scaleImportRepository: ScaleImportRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private val objectMapper = jacksonObjectMapper()
    private val locales = ScaleSourcePackageValidation.REQUIRED_LOCALES
    private lateinit var service: ScaleSourcePackageImportPreviewService
    private val user = UserPrincipal(10, "admin", "Admin", UserStatus.ENABLED, null, 7, setOf("ASSESSMENT_ADMIN"))

    @BeforeEach
    fun setUp() {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        service = ScaleSourcePackageImportPreviewService(
            scaleRepository, scaleImportRepository, currentUserFacade, LocalizedMessages(source), objectMapper,
            securityAuditService, tenantAccessPolicy
        )
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        whenever(tenantAccessPolicy.requireTenantId()).thenReturn(7)
        whenever(scaleImportRepository.createJob(any(), any(), any(), any(), any())).thenReturn(99L)
        whenever(scaleRepository.existsByScaleCode(any(), any())).thenReturn(false)
    }

    @Test
    fun `valid source package is ready for controlled import`() {
        val result = service.preview(file(validDocument()))

        assertTrue(result.readyForControlledImport)
        assertTrue(result.confirmationSupported)
        assertEquals(0, result.errorCount)
    }

    @Test
    fun `unsupported derived indices are reported as errors`() {
        val result = service.preview(
            file(validDocument().copy(scoring = SourceScoring(indices = mapOf("GSI" to "global severity index"))))
        )

        assertFalse(result.readyForControlledImport)
        assertTrue(result.errors.any { it.errorCode == "SOURCE_PACKAGE_INDICES_UNSUPPORTED" })
    }

    private fun file(document: ScaleSourcePackageDocument): MockMultipartFile {
        val bytes = objectMapper.writeValueAsBytes(document)
        return MockMultipartFile("file", "k6-source-package.json", "application/json", bytes)
    }

    private fun validDocument(): ScaleSourcePackageDocument {
        val emptyObject = objectMapper.readTree("{}")
        return ScaleSourcePackageDocument(
            scale = SourceScale(
                scaleCode = "K6",
                scaleName = "K6",
                versionNo = "test-v1",
                reportTemplate = "SINGLE_SCORE",
                algorithmBinding = SourceAlgorithmBinding("GENERIC_SCORE_CALCULATOR", "1", "BUILTIN"),
                instruction = locales.associateWith { "Instruction" }
            ),
            governance = SourceGovernance(
                sourceTitle = "K6 manual",
                copyrightStatus = "AUTHORIZED",
                authorizationStatus = "AUTHORIZED",
                nonDiagnosticStatement = "Screening only"
            ),
            translations = locales.associateWith {
                SourceScaleTranslation("K6", nonDiagnosticText = "Screening only")
            },
            dimensions = listOf(
                SourceDimension(
                    dimensionCode = "D1",
                    questionNos = listOf(1),
                    translations = locales.associateWith { SourceDimensionTranslation("Dimension") }
                )
            ),
            questions = listOf(
                SourceQuestion(
                    questionNo = 1,
                    dimensionCode = "D1",
                    translations = locales.associateWith { SourceQuestionTranslation("Question") },
                    options = listOf(
                        SourceOption("A", BigDecimal.ZERO, locales.associateWith { "No" }),
                        SourceOption("B", BigDecimal.ONE, locales.associateWith { "Yes" })
                    )
                )
            ),
            resultRules = listOf(
                SourceResultRule(
                    ruleCode = "R1",
                    dimensionCode = null,
                    riskLevel = "NORMAL",
                    scoreMin = BigDecimal.ZERO,
                    scoreMax = BigDecimal(4),
                    translations = locales.associateWith {
                        SourceResultRuleTranslation("Normal", "Normal range", "No action")
                    }
                )
            ),
            goldenCases = listOf(SourceGoldenCase("CASE-1", "NORMAL", null, emptyObject, emptyObject)),
            sourceReferences = listOf(SourceReference("K6 manual", "https://example.com/k6"))
        )
    }
}
