package org.sainm.psy.common.security

import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.stereotype.Component

/**
 * Resolves tenant scope explicitly for staff-facing application services.
 *
 * A missing tenant is not itself a global-access grant. Only a tenantless
 * SYS_ADMIN or SUPER_ADMIN principal may use the cross-tenant exception.
 * Tenant-bound administrators remain restricted to their own tenant.
 */
@Component
class TenantAccessPolicy(
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val securityAuditService: SecurityAuditService
) {

    fun currentTenantFilter(resourceType: String, action: String): Long? {
        val scope = currentScope()
        if (scope.global) {
            securityAuditService.recordTenantScopeOverride(
                resourceType = resourceType,
                resourceId = null,
                action = action,
                targetTenantId = null
            )
        }
        return scope.tenantId
    }

    fun requireTenantId(): Long = currentScope().tenantId
        ?: throw BizException("TENANT_CONTEXT_REQUIRED", messages.get("tenant.context.required"))

    fun canAccess(
        targetTenantId: Long?,
        resourceType: String,
        resourceId: Long,
        action: String
    ): Boolean = canAccessResource(targetTenantId, resourceType, resourceId, action)

    fun canAccess(
        targetTenantId: Long?,
        resourceType: String,
        resourceId: String,
        action: String
    ): Boolean = canAccessResource(targetTenantId, resourceType, resourceId, action)

    private fun canAccessResource(
        targetTenantId: Long?,
        resourceType: String,
        resourceId: Any,
        action: String
    ): Boolean {
        val scope = currentScope()
        if (!scope.global) {
            return targetTenantId != null && targetTenantId == scope.tenantId
        }
        securityAuditService.recordTenantScopeOverride(
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            targetTenantId = targetTenantId
        )
        return true
    }

    fun currentPrincipal(): UserPrincipal = currentScope().principal

    private fun currentScope(): TenantAccessScope {
        val principal = currentUserFacade.requireCurrentUser()
        val global = principal.tenantId == null && principal.roles.any(GLOBAL_ROLES::contains)
        if (principal.tenantId == null && !global) {
            throw BizException("TENANT_CONTEXT_REQUIRED", messages.get("tenant.context.required"))
        }
        return TenantAccessScope(principal = principal, tenantId = principal.tenantId, global = global)
    }

    private data class TenantAccessScope(
        val principal: UserPrincipal,
        val tenantId: Long?,
        val global: Boolean
    )

    companion object {
        private val GLOBAL_ROLES = setOf("SYS_ADMIN", "SUPER_ADMIN")
    }
}
