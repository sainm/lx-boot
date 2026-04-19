package org.sainm.psy.useradmin.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class UserAdminUserSummaryResponse(
    val userId: Long,
    val username: String,
    val displayName: String?,
    val email: String?,
    val mobile: String?,
    val status: String,
    val groupId: Long?,
    val groupName: String?,
    val tenantId: Long?,
    val tenantName: String?,
    val roles: Set<String>,
    val createdAt: String?,
    val updatedAt: String?
)

data class UserAdminRoleResponse(
    val roleId: Long,
    val roleCode: String,
    val roleName: String,
    val tenantId: Long?
)

data class UserAdminTenantResponse(
    val tenantId: Long,
    val tenantCode: String,
    val tenantName: String
)

data class UserAdminGroupResponse(
    val groupId: Long,
    val groupCode: String,
    val groupName: String,
    val tenantId: Long?,
    val parentId: Long?
)

data class CreateUserAdminUserRequest(
    @field:NotBlank(message = "user.admin.username.required")
    @field:Size(max = 64, message = "user.admin.username.too_long")
    val username: String,

    @field:NotBlank(message = "user.admin.password.required")
    @field:Size(min = 8, max = 128, message = "user.admin.password.invalid")
    val password: String,

    @field:Size(max = 128, message = "user.admin.display_name.too_long")
    val displayName: String? = null,

    @field:Size(max = 128, message = "user.admin.email.too_long")
    val email: String? = null,

    @field:Size(max = 32, message = "user.admin.mobile.too_long")
    val mobile: String? = null,

    val tenantId: Long? = null,

    val groupId: Long? = null,

    val roleCodes: Set<String> = emptySet()
)

data class AssignUserRolesRequest(
    @field:NotEmpty(message = "user.admin.roles.required")
    val roleCodes: Set<String>
)

data class UpdateUserStatusRequest(
    val enabled: Boolean
)

data class ResetUserPasswordRequest(
    @field:NotBlank(message = "user.admin.password.required")
    @field:Size(min = 8, max = 128, message = "user.admin.password.invalid")
    val newPassword: String
)
