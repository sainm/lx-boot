package org.sainm.psy.auth.api

import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.auth.service.AuthSessionService
import org.sainm.psy.common.api.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthProfileController(
    private val currentUserFacade: CurrentUserFacade,
    private val authSessionService: AuthSessionService
) {

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    fun me(): ApiResponse<AuthProfileResponse> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return ApiResponse.ok(
            AuthProfileResponse(
                userId = currentUser.userId,
                username = currentUser.username,
                displayName = currentUser.displayName,
                roles = currentUser.roles.sorted(),
                permissions = currentUser.permissions.sorted()
            )
        )
    }

    @GetMapping("/me/login-activities")
    @PreAuthorize("isAuthenticated()")
    fun myLoginActivities(): ApiResponse<List<LoginActivityResponse>> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return ApiResponse.ok(
            authSessionService.findRecentLoginActivities(currentUser.userId, currentUser.username).map {
                LoginActivityResponse(
                    id = it.id,
                    userId = it.userId,
                    principal = it.principal,
                    loginType = it.loginType,
                    result = it.result,
                    ip = it.ip,
                    userAgent = it.userAgent,
                    location = it.location,
                    reason = it.reason,
                    createdAt = it.createdAt
                )
            }
        )
    }

    @GetMapping("/me/security-events")
    @PreAuthorize("isAuthenticated()")
    fun mySecurityEvents(): ApiResponse<List<SecurityEventResponse>> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return ApiResponse.ok(
            authSessionService.findRecentSecurityEvents(currentUser.userId).map {
                SecurityEventResponse(
                    id = it.id,
                    eventType = it.eventType,
                    userId = it.userId,
                    tenantId = it.tenantId,
                    detail = it.detail,
                    ip = it.ip,
                    createdAt = it.createdAt
                )
            }
        )
    }
}
