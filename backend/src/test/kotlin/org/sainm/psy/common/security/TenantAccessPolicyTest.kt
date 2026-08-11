package org.sainm.psy.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.context.support.ReloadableResourceBundleMessageSource

class TenantAccessPolicyTest {

    private val currentUserFacade = mock(CurrentUserFacade::class.java)
    private val securityAuditService = mock(SecurityAuditService::class.java)
    private val messages = LocalizedMessages(
        ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
    )
    private val policy = TenantAccessPolicy(currentUserFacade, messages, securityAuditService)

    @Test
    fun `tenant-bound administrator is restricted to its own tenant`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(principal(tenantId = 7L, roles = setOf("SYS_ADMIN")))

        assertEquals(7L, policy.currentTenantFilter("SCALE", "LIST"))
        assertTrue(policy.canAccess(7L, "SCALE", 1L, "READ"))
        assertFalse(policy.canAccess(8L, "SCALE", 2L, "READ"))
        verify(securityAuditService, never()).recordTenantScopeOverride(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(Long::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(Long::class.java)
        )
    }

    @Test
    fun `tenantless global administrator can access all tenants and is audited`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(principal(tenantId = null, roles = setOf("SUPER_ADMIN")))

        assertNull(policy.currentTenantFilter("REPORT", "SEARCH"))
        assertTrue(policy.canAccess(8L, "REPORT", 20L, "READ"))

        verify(securityAuditService).recordTenantScopeOverride("REPORT", null, "SEARCH", null)
        verify(securityAuditService).recordTenantScopeOverride("REPORT", 20L, "READ", 8L)
    }

    @Test
    fun `tenantless non-global staff role is rejected`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(principal(tenantId = null, roles = setOf("ASSESSMENT_ADMIN")))

        val error = assertThrows<BizException> { policy.currentTenantFilter("SCALE", "LIST") }

        assertEquals("TENANT_CONTEXT_REQUIRED", error.code)
        verify(securityAuditService, never()).recordTenantScopeOverride(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(Long::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(Long::class.java)
        )
    }

    private fun principal(tenantId: Long?, roles: Set<String>) = UserPrincipal(
        userId = 1L,
        username = "operator",
        displayName = "Operator",
        status = UserStatus.ENABLED,
        tenantId = tenantId,
        groupId = null,
        roles = roles,
        permissions = emptySet()
    )
}
