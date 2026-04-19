package org.sainm.psy.useradmin.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.useradmin.service.UserAdminListQuery
import org.sainm.psy.useradmin.service.UserAdminManagementService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/user-admin")
class UserAdminController(
    private val userAdminManagementService: UserAdminManagementService
) {

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findUserPage(
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) tenantId: Long?,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<UserAdminUserSummaryResponse>> =
        ApiResponse.ok(
            userAdminManagementService.findUserPage(
                UserAdminListQuery(
                    username = username,
                    status = status,
                    tenantId = tenantId,
                    groupId = groupId,
                    page = page,
                    size = size
                )
            )
        )

    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun listRoles(@RequestParam(required = false) tenantId: Long?): ApiResponse<List<UserAdminRoleResponse>> =
        ApiResponse.ok(userAdminManagementService.listRoles(tenantId))

    @GetMapping("/tenants")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun listTenants(): ApiResponse<List<UserAdminTenantResponse>> =
        ApiResponse.ok(userAdminManagementService.listTenants())

    @GetMapping("/groups")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun listGroups(@RequestParam(required = false) tenantId: Long?): ApiResponse<List<UserAdminGroupResponse>> =
        ApiResponse.ok(userAdminManagementService.listGroups(tenantId))

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun createUser(@Valid @RequestBody request: CreateUserAdminUserRequest): ApiResponse<UserAdminUserSummaryResponse> =
        ApiResponse.ok(userAdminManagementService.createUser(request))

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun assignRoles(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AssignUserRolesRequest
    ): ApiResponse<UserAdminUserSummaryResponse> =
        ApiResponse.ok(userAdminManagementService.assignRoles(userId, request.roleCodes))

    @PostMapping("/users/{userId}/status")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun updateStatus(
        @PathVariable userId: Long,
        @Valid @RequestBody request: UpdateUserStatusRequest
    ): ApiResponse<UserAdminUserSummaryResponse> =
        ApiResponse.ok(userAdminManagementService.updateStatus(userId, request.enabled))

    @PostMapping("/users/{userId}/password/reset")
    @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun resetPassword(
        @PathVariable userId: Long,
        @Valid @RequestBody request: ResetUserPasswordRequest
    ): ApiResponse<Boolean> {
        userAdminManagementService.resetPassword(userId, request.newPassword)
        return ApiResponse.ok(true)
    }
}
