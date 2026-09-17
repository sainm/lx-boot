package org.sainm.psy.warning.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.warning.api.CreateSafetyResponsePolicyRequest
import org.sainm.psy.warning.domain.SafetyResponsePolicy
import org.sainm.psy.warning.repository.SafetyResponsePolicyRepository
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.Locale

@ExtendWith(MockitoExtension::class)
class SafetyResponsePolicyServiceTest {

    @Mock private lateinit var repository: SafetyResponsePolicyRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy

    private lateinit var service: SafetyResponsePolicyService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        service = SafetyResponsePolicyService(repository, currentUserFacade, LocalizedMessages(messageSource), tenantAccessPolicy)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(admin)
        org.mockito.Mockito.lenient().`when`(tenantAccessPolicy.requireTenantId()).thenReturn(7L)
    }

    @AfterEach
    fun clearLocale() = LocaleContextHolder.resetLocaleContext()

    @Test
    fun `create rejects unsupported risk with localized stable error`() {
        LocaleContextHolder.setLocale(Locale.JAPAN)
        val exception = assertThrows<BizException> {
            service.create(validRequest.copy(riskCategory = "SEVERE"))
        }

        assertEquals("SAFETY_POLICY_RISK_INVALID", exception.code)
        assertEquals("安全対応ポリシーのリスク区分は P0、P1、P2、P3 のいずれかである必要があります。", exception.message)
        verify(repository, never()).create(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `create rejects escalation earlier than first response`() {
        val exception = assertThrows<BizException> {
            service.create(validRequest.copy(firstResponseMinutes = 30, escalationMinutes = 10))
        }

        assertEquals("SAFETY_POLICY_SLA_INVALID", exception.code)
    }

    @Test
    fun `professional review requires an active counselor`() {
        val exception = assertThrows<BizException> {
            service.professionalReview(1L)
        }

        assertEquals("SAFETY_POLICY_REVIEWER_INVALID", exception.code)
        verify(repository, never()).markProfessionallyReviewed(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `approval cannot activate a draft without authenticated professional review`() {
        `when`(repository.findById(1L, 7L)).thenReturn(draft())

        val exception = assertThrows<BizException> {
            service.approve(1L)
        }

        assertEquals("SAFETY_POLICY_PROFESSIONAL_REVIEW_REQUIRED", exception.code)
        verify(repository, never()).approveAndActivate(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `approval rejects the professional reviewer as the approving user`() {
        val reviewer = admin.copy(userId = 11L, username = "counselor", roles = setOf("COUNSELOR"))
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(reviewer)
        `when`(repository.findById(1L, 7L)).thenReturn(draft(professionalReviewerId = reviewer.userId))

        val exception = assertThrows<BizException> {
            service.approve(1L)
        }

        assertEquals("SAFETY_POLICY_DUAL_REVIEW_REQUIRED", exception.code)
        verify(repository, never()).approveAndActivate(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `approval activates only a reviewed draft`() {
        val reviewed = draft(professionalReviewerId = 11L)
        val approved = reviewed.copy(status = "APPROVED", activeFlag = true, approvedBy = admin.userId)
        `when`(repository.findById(1L, 7L)).thenReturn(reviewed, approved)
        `when`(repository.isCounselorInTenant(11L, 7L)).thenReturn(true)
        `when`(repository.approveAndActivate(1L, 7L, admin.userId)).thenReturn(true)

        assertEquals(approved, service.approve(1L))
        verify(repository).approveAndActivate(1L, 7L, admin.userId)
    }

    private fun draft(professionalReviewerId: Long? = null) = SafetyResponsePolicy(
        id = 1L,
        tenantId = 7L,
        policyCode = "P0-RESPONSE",
        versionNo = 1,
        riskCategory = "P0",
        firstResponseMinutes = 10,
        escalationMinutes = 30,
        followUpMinutes = 1440,
        responsibleRole = "COUNSELOR",
        backupRole = "ORG_MANAGER",
        emergencyContactText = "Approved emergency contact",
        status = "DRAFT",
        activeFlag = false,
        approvedBy = null,
        professionalReviewerId = professionalReviewerId,
        professionalReviewedAt = professionalReviewerId?.let { java.time.LocalDateTime.of(2026, 8, 17, 10, 0) },
        approvedAt = null,
        createdAt = java.time.LocalDateTime.of(2026, 8, 17, 9, 0)
    )

    private val validRequest = CreateSafetyResponsePolicyRequest(
        policyCode = "P0-RESPONSE",
        versionNo = 1,
        riskCategory = "P0",
        firstResponseMinutes = 10,
        escalationMinutes = 30,
        followUpMinutes = 1440,
        responsibleRole = "COUNSELOR",
        backupRole = "ORG_MANAGER",
        emergencyContactText = "Use the organization-approved emergency contact."
    )

    private val admin = UserPrincipal(
        userId = 10L,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 1L,
        groupId = null,
        roles = setOf("ORG_MANAGER"),
        permissions = emptySet()
    )
}
