package org.sainm.psy.auth.api

import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.api.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthProfileController(
    private val currentUserFacade: CurrentUserFacade
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
}
