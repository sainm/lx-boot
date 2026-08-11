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
import org.sainm.psy.warning.api.ApproveSafetyResponsePolicyRequest
import org.sainm.psy.warning.api.CreateSafetyResponsePolicyRequest
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
    fun `approve requires different professional reviewer`() {
        val exception = assertThrows<BizException> {
            service.approve(1L, ApproveSafetyResponsePolicyRequest(professionalReviewerId = admin.userId))
        }

        assertEquals("SAFETY_POLICY_DUAL_REVIEW_REQUIRED", exception.code)
        verify(repository, never()).approveAndActivate(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

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
