package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.scale.api.UpdateScalePackageRequest
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePackageValidityRule
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ScalePackageServiceTest {
    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var packageRepository: ScalePackageRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy
    private lateinit var service: ScalePackageService

    @BeforeEach
    fun setUp() {
        val source = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        service = ScalePackageService(scaleRepository, packageRepository, currentUserFacade, LocalizedMessages(source), ObjectMapper(), securityAuditService, tenantAccessPolicy)
        lenient().`when`(scaleRepository.lockById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true)
        lenient().`when`(
            tenantAccessPolicy.canAccess(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
            )
        ).thenReturn(true)
    }

    @Test
    fun `cross tenant package is hidden as not found`() {
        `when`(scaleRepository.findDetailById(1L)).thenReturn(scale(tenantId = 99L))

        val exception = assertThrows<BizException> { service.find(1L) }

        assertEquals("SCALE_NOT_FOUND", exception.code)
        verify(packageRepository, never()).find(1L)
    }

    @Test
    fun `published scale package cannot be replaced`() {
        `when`(scaleRepository.findDetailById(1L)).thenReturn(scale(status = "PUBLISHED"))

        val exception = assertThrows<BizException> { service.replace(1L, UpdateScalePackageRequest()) }

        assertEquals("SCALE_NOT_DRAFT", exception.code)
        verify(packageRepository, never()).replace(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `invalid validity JSON is rejected before database write`() {
        `when`(scaleRepository.findDetailById(1L)).thenReturn(scale())
        val request = UpdateScalePackageRequest(
            validityRules = listOf(ScalePackageValidityRule("V1", "CONSISTENCY", "1", "{broken"))
        )

        val exception = assertThrows<BizException> { service.replace(1L, request) }

        assertEquals("SCALE_PACKAGE_JSON_INVALID", exception.code)
    }

    @Test
    fun `draft package is replaced and returned`() {
        val request = UpdateScalePackageRequest(
            translations = listOf(ScalePackageTranslation("zh-CN", "量表"), ScalePackageTranslation("ja-JP", "尺度"), ScalePackageTranslation("en", "Scale"))
        )
        val expected = ScalePackageSnapshot(1L, translations = request.translations)
        `when`(scaleRepository.findDetailById(1L)).thenReturn(scale())
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(10L)
        `when`(packageRepository.find(1L)).thenReturn(expected)

        val result = service.replace(1L, request)

        assertEquals(expected, result)
        verify(packageRepository).replace(1L, request, 10L)
        verify(securityAuditService).recordScalePackageUpdated(1L, listOf("zh-CN", "ja-JP", "en"), 0)
    }

    private fun scale(status: String = "DRAFT", tenantId: Long? = 7L) = ScaleDetail(
        id = 1L,
        scaleCode = "TEST",
        scaleName = "Test",
        description = null,
        applicableTarget = null,
        versionNo = "v1",
        versionGroupId = 1L,
        currentVersionFlag = false,
        status = status,
        scoreMethod = "SIMPLE_SUM",
        scoreCoefficient = BigDecimal.ONE,
        normStrategy = "RAW_SCORE",
        normDefaultGroup = null,
        highRiskWarningEnabled = false,
        anonymousSupported = false,
        reportTemplate = null,
        createdBy = 10L,
        createdAt = LocalDateTime.of(2026, 8, 8, 12, 0),
        updatedBy = 10L,
        updatedAt = LocalDateTime.of(2026, 8, 8, 12, 0),
        dimensions = emptyList(),
        questions = emptyList(),
        resultRules = emptyList(),
        norms = emptyList(),
        tenantId = tenantId
    )

    private val user = UserPrincipal(
        userId = 10L,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 7L,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )
}
