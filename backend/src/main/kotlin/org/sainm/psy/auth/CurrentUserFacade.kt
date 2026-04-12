package org.sainm.psy.auth

import org.sainm.auth.core.spi.PermissionService
import org.sainm.auth.core.spi.UserLookupService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CurrentUserFacade(
    private val userLookupService: UserLookupService,
    private val permissionService: PermissionService,
    private val messages: LocalizedMessages
) {

    fun requireCurrentUserId(): Long =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)
            ?: throw BizException("AUTH_401001", messages.get("AUTH_401001"))

    fun requireCurrentUser(): CurrentUser {
        val userId = requireCurrentUserId()
        val principal = userLookupService.findById(userId)
            ?: throw BizException("AUTH_401001", messages.get("AUTH_401001"))
        return CurrentUser(
            userId = principal.userId,
            username = principal.username,
            displayName = principal.displayName,
            tenantId = principal.tenantId,
            groupId = principal.groupId,
            roles = permissionService.loadRoles(userId),
            permissions = permissionService.loadPermissions(userId)
        )
    }
}
