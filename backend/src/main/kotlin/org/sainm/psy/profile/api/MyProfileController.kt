package org.sainm.psy.profile.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.profile.service.MyProfileService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/my/profile")
class MyProfileController(
    private val myProfileService: MyProfileService
) {

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getMyProfile(): ApiResponse<MyProfileResponse> =
        ApiResponse.ok(myProfileService.getMyProfile())

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun updateMyProfile(@Valid @RequestBody request: UpdateMyProfileRequest): ApiResponse<MyProfileResponse> =
        ApiResponse.ok(myProfileService.updateMyProfile(request))
}
