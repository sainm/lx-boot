package org.sainm.psy.auth

import org.sainm.auth.core.spi.PermissionService
import org.sainm.auth.core.spi.UserLookupService
import org.sainm.psy.common.exception.BizException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CurrentUserFacade(
    private val userLookupService: UserLookupService,
    private val permissionService: PermissionService
) {

    fun requireCurrentUserId(): Long =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)
            ?: throw BizException("AUTH_401001", "当前登录用户不存在")

    fun requireCurrentUser(): CurrentUser {
        val userId = requireCurrentUserId()
        val principal = userLookupService.findById(userId)
            ?: throw BizException("AUTH_401001", "当前登录用户不存在")
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
