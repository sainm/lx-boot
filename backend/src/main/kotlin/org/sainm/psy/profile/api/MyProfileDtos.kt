package org.sainm.psy.profile.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class MyProfileResponse(
    val userId: Long,
    val username: String,
    val nickname: String?,
    val displayName: String?,
    val email: String?,
    val mobile: String?,
    val avatarUrl: String?,
    val groupId: Long?,
    val groupName: String?,
    val tenantId: Long?,
    val tenantName: String?,
    val roles: Set<String>,
    val updatedAt: String?
)

data class UpdateMyProfileRequest(
    @field:Size(max = 128, message = "profile.nickname.too_long")
    val nickname: String? = null,

    @field:Size(max = 128, message = "profile.display_name.too_long")
    val displayName: String? = null,

    @field:Email(message = "profile.email.invalid")
    @field:Size(max = 128, message = "profile.email.too_long")
    val email: String? = null,

    @field:Size(max = 32, message = "profile.mobile.too_long")
    val mobile: String? = null,

    @field:Size(max = 512, message = "profile.avatar_url.too_long")
    val avatarUrl: String? = null
)
