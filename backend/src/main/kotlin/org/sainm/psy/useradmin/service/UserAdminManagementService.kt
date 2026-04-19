package org.sainm.psy.useradmin.service

import org.sainm.auth.core.spi.OrganizationService
import org.sainm.auth.core.spi.PasswordManagementService
import org.sainm.auth.core.spi.ResetPasswordCommand
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.useradmin.api.CreateUserAdminUserRequest
import org.sainm.psy.useradmin.api.UserAdminGroupResponse
import org.sainm.psy.useradmin.api.UserAdminRoleResponse
import org.sainm.psy.useradmin.api.UserAdminTenantResponse
import org.sainm.psy.useradmin.api.UserAdminUserSummaryResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Statement
import java.sql.Timestamp

data class UserAdminListQuery(
    val username: String? = null,
    val status: String? = null,
    val tenantId: Long? = null,
    val groupId: Long? = null,
    val page: Int = 1,
    val size: Int = 20
)

@Service
class UserAdminManagementService(
    private val jdbcTemplate: JdbcTemplate,
    private val passwordEncoder: PasswordEncoder,
    private val passwordManagementService: PasswordManagementService,
    private val organizationService: OrganizationService,
    private val currentUserFacade: CurrentUserFacade
) {

    fun findUserPage(query: UserAdminListQuery): PageResponse<UserAdminUserSummaryResponse> {
        val scopedTenantId = scopedTenantId(query.tenantId)
        val normalizedStatus = query.status?.trim()?.uppercase()?.ifEmpty { null }
        val normalizedKeyword = query.username?.trim()?.takeIf { it.isNotEmpty() }
        val statusValue = statusCode(normalizedStatus)
        val scopedGroupId = scopedGroupId(query.groupId, scopedTenantId)
        val page = query.page.coerceAtLeast(1)
        val size = query.size.coerceIn(1, 100)
        val offset = (page - 1) * size

        val whereClause = buildString {
            append("where u.deleted = 0 ")
            if (scopedTenantId != null) {
                append("and u.tenant_id = ? ")
            }
            if (normalizedKeyword != null) {
                append("and (u.username ilike ? or coalesce(u.display_name, '') ilike ?) ")
            }
            if (statusValue != null) {
                append("and u.status = ? ")
            }
            if (scopedGroupId != null) {
                append("and u.group_id = ? ")
            }
        }
        val params = mutableListOf<Any>()
        if (scopedTenantId != null) {
            params += scopedTenantId
        }
        if (normalizedKeyword != null) {
            val keyword = "%$normalizedKeyword%"
            params += keyword
            params += keyword
        }
        if (statusValue != null) {
            params += statusValue
        }
        if (scopedGroupId != null) {
            params += scopedGroupId
        }

        val total = jdbcTemplate.queryForObject(
            """
            select count(1)
            from sys_user u
            $whereClause
            """.trimIndent(),
            Long::class.java,
            *params.toTypedArray()
        ) ?: 0L

        val items = jdbcTemplate.query(
            """
            select u.id,
                   u.username,
                   u.display_name,
                   u.email,
                   u.mobile,
                   u.status,
                   u.group_id,
                   g.group_name,
                   u.tenant_id,
                   t.tenant_name,
                   u.created_at,
                   u.updated_at
            from sys_user u
            left join sys_group g on g.id = u.group_id
            left join sys_tenant t on t.id = u.tenant_id
            $whereClause
            order by u.id desc
            limit ? offset ?
            """.trimIndent(),
            { rs, _ ->
                UserRow(
                    userId = rs.getLong("id"),
                    username = rs.getString("username"),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    mobile = rs.getString("mobile"),
                    status = rs.getInt("status"),
                    groupId = rs.getNullableLong("group_id"),
                    groupName = rs.getString("group_name"),
                    tenantId = rs.getNullableLong("tenant_id"),
                    tenantName = rs.getString("tenant_name"),
                    createdAt = rs.getTimestamp("created_at")?.toLocalDateTime()?.toString(),
                    updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime()?.toString()
                )
            },
            *(params + listOf(size, offset)).toTypedArray()
        )

        return PageResponse(
            list = toResponses(items),
            page = page,
            size = size,
            total = total
        )
    }

    fun listRoles(tenantId: Long?): List<UserAdminRoleResponse> {
        val scopedTenantId = scopedTenantId(tenantId)
        return if (scopedTenantId == null) {
            jdbcTemplate.query(
                """
                select id, role_code, role_name, tenant_id
                from sys_role
                where enabled = 1
                order by tenant_id nulls first, id
                """.trimIndent(),
                { rs, _ ->
                    UserAdminRoleResponse(
                        roleId = rs.getLong("id"),
                        roleCode = rs.getString("role_code"),
                        roleName = rs.getString("role_name"),
                        tenantId = rs.getNullableLong("tenant_id")
                    )
                }
            )
        } else {
            jdbcTemplate.query(
                """
                select id, role_code, role_name, tenant_id
                from sys_role
                where enabled = 1
                  and (tenant_id is null or tenant_id = ?)
                order by tenant_id nulls first, id
                """.trimIndent(),
                { rs, _ ->
                    UserAdminRoleResponse(
                        roleId = rs.getLong("id"),
                        roleCode = rs.getString("role_code"),
                        roleName = rs.getString("role_name"),
                        tenantId = rs.getNullableLong("tenant_id")
                    )
                },
                scopedTenantId
            )
        }
    }

    fun listTenants(): List<UserAdminTenantResponse> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return if (isSuperScope(currentUser.roles)) {
            organizationService.listTenants().map {
                UserAdminTenantResponse(
                    tenantId = it.tenantId,
                    tenantCode = it.tenantCode,
                    tenantName = it.tenantName
                )
            }
        } else {
            currentUser.tenantId?.let { tenantId ->
                organizationService.listTenants(tenantId).map {
                    UserAdminTenantResponse(
                        tenantId = it.tenantId,
                        tenantCode = it.tenantCode,
                        tenantName = it.tenantName
                    )
                }
            }.orEmpty()
        }
    }

    fun listGroups(tenantId: Long?): List<UserAdminGroupResponse> {
        val scopedTenantId = scopedTenantId(tenantId)
        return organizationService.listGroups(scopedTenantId).map {
            UserAdminGroupResponse(
                groupId = it.groupId,
                groupCode = it.groupCode,
                groupName = it.groupName,
                tenantId = it.tenantId,
                parentId = it.parentId
            )
        }
    }

    @Transactional
    fun createUser(request: CreateUserAdminUserRequest): UserAdminUserSummaryResponse {
        val normalizedUsername = request.username.trim()
        require(normalizedUsername.isNotEmpty()) { "user.admin.username.required" }
        val scopedTenantId = scopedTenantId(request.tenantId)
        val scopedGroupId = scopedGroupId(request.groupId, scopedTenantId)
        assertUsernameAvailable(normalizedUsername)

        val userId = insertUser(
            username = normalizedUsername,
            displayName = request.displayName?.trim()?.ifEmpty { null } ?: normalizedUsername,
            email = request.email?.trim()?.ifEmpty { null },
            mobile = request.mobile?.trim()?.ifEmpty { null },
            tenantId = scopedTenantId,
            groupId = scopedGroupId ?: findDefaultGroupId(scopedTenantId)
        )
        jdbcTemplate.update(
            """
            insert into sys_auth (user_id, identity_type, principal_key, credential_hash, metadata_json, enabled)
            values (?, 'PASSWORD', ?, ?, '{}'::jsonb, 1)
            """.trimIndent(),
            userId,
            normalizedUsername,
            passwordEncoder.encode(request.password)
        )
        val roleCodes = request.roleCodes.takeIf { it.isNotEmpty() } ?: setOf("USER")
        replaceRoles(userId, roleCodes, scopedTenantId)
        return loadUser(userId)
    }

    @Transactional
    fun assignRoles(userId: Long, roleCodes: Set<String>): UserAdminUserSummaryResponse {
        val target = requireManageableUser(userId)
        replaceRoles(target.userId, roleCodes, target.tenantId)
        return loadUser(target.userId)
    }

    @Transactional
    fun updateStatus(userId: Long, enabled: Boolean): UserAdminUserSummaryResponse {
        val target = requireManageableUser(userId)
        jdbcTemplate.update(
            """
            update sys_user
            set status = ?,
                failed_login_attempts = 0,
                locked_until = null,
                updated_at = current_timestamp
            where id = ?
            """.trimIndent(),
            if (enabled) 1 else 0,
            target.userId
        )
        return loadUser(target.userId)
    }

    @Transactional
    fun resetPassword(userId: Long, newPassword: String) {
        val target = requireManageableUser(userId)
        passwordManagementService.resetPassword(
            ResetPasswordCommand(
                principal = target.username,
                newPassword = newPassword
            )
        )
    }

    private fun scopedTenantId(requestTenantId: Long?): Long? {
        val currentUser = currentUserFacade.requireCurrentUser()
        return if (isSuperScope(currentUser.roles)) {
            requestTenantId
        } else {
            currentUser.tenantId
        }
    }

    private fun scopedGroupId(requestGroupId: Long?, tenantId: Long?): Long? {
        if (requestGroupId == null) {
            return null
        }
        val matchingGroup = organizationService.listGroups(tenantId).firstOrNull { it.groupId == requestGroupId }
            ?: throw BizException("user.admin.group.not_found", "Group not found")
        return matchingGroup.groupId
    }

    private fun isSuperScope(roles: Set<String>): Boolean =
        roles.any { it in setOf("SYS_ADMIN", "SUPER_ADMIN", "ADMIN") }

    private fun assertUsernameAvailable(username: String) {
        val exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from sys_user where username = ? and deleted = 0)",
            Boolean::class.java,
            username
        ) ?: false
        if (exists) {
            throw BizException("user.admin.username.exists", "Username already exists")
        }
    }

    private fun insertUser(
        username: String,
        displayName: String,
        email: String?,
        mobile: String?,
        tenantId: Long?,
        groupId: Long?
    ): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(
                """
                insert into sys_user (
                    username, display_name, email, mobile, status, register_source, password_version, deleted, group_id, tenant_id
                ) values (?, ?, ?, ?, 1, 'ADMIN_CREATE', 1, 0, ?, ?)
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS
            ).apply {
                setString(1, username)
                setString(2, displayName)
                setString(3, email)
                setString(4, mobile)
                setNullableLong(5, groupId)
                setNullableLong(6, tenantId)
            }
        }, keyHolder)
        return keyHolder.keys?.get("id")?.let { (it as Number).toLong() }
            ?: keyHolder.key?.toLong()
            ?: error("Failed to create user")
    }

    private fun replaceRoles(userId: Long, roleCodes: Set<String>, tenantId: Long?) {
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", userId)
        if (roleCodes.isEmpty()) {
            return
        }
        val normalizedCodes = roleCodes.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        if (normalizedCodes.isEmpty()) {
            return
        }
        val placeholders = normalizedCodes.joinToString(",") { "?" }
        val args = mutableListOf<Any>(userId)
        args.addAll(normalizedCodes)
        val tenantSql = if (tenantId == null) {
            ""
        } else {
            " and (tenant_id is null or tenant_id = ?) "
        }
        if (tenantId != null) {
            args += tenantId
        }
        jdbcTemplate.update(
            """
            insert into sys_user_role (user_id, role_id)
            select ?, id
            from sys_role
            where enabled = 1
              and role_code in ($placeholders)
              $tenantSql
            """.trimIndent(),
            *args.toTypedArray()
        )
    }

    private fun requireManageableUser(userId: Long): UserRow {
        val target = loadUserRow(userId)
            ?: throw BizException("user.admin.user.not_found", "User not found")
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!isSuperScope(currentUser.roles) && currentUser.tenantId != null && currentUser.tenantId != target.tenantId) {
            throw BizException("user.admin.user.out_of_scope", "User is out of scope")
        }
        return target
    }

    private fun loadUser(userId: Long): UserAdminUserSummaryResponse =
        toResponses(listOf(requireManageableUser(userId))).first()

    private fun loadUserRow(userId: Long): UserRow? =
        jdbcTemplate.query(
            """
            select u.id,
                   u.username,
                   u.display_name,
                   u.email,
                   u.mobile,
                   u.status,
                   u.group_id,
                   g.group_name,
                   u.tenant_id,
                   t.tenant_name,
                   u.created_at,
                   u.updated_at
            from sys_user u
            left join sys_group g on g.id = u.group_id
            left join sys_tenant t on t.id = u.tenant_id
            where u.id = ?
              and u.deleted = 0
            """.trimIndent(),
            { rs, _ ->
                UserRow(
                    userId = rs.getLong("id"),
                    username = rs.getString("username"),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    mobile = rs.getString("mobile"),
                    status = rs.getInt("status"),
                    groupId = rs.getNullableLong("group_id"),
                    groupName = rs.getString("group_name"),
                    tenantId = rs.getNullableLong("tenant_id"),
                    tenantName = rs.getString("tenant_name"),
                    createdAt = rs.getTimestamp("created_at")?.toLocalDateTime()?.toString(),
                    updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime()?.toString()
                )
            },
            userId
        ).firstOrNull()

    private fun toResponses(rows: List<UserRow>): List<UserAdminUserSummaryResponse> {
        val rolesByUserId = loadRolesByUserIds(rows.map { it.userId })
        return rows.map { row ->
            UserAdminUserSummaryResponse(
                userId = row.userId,
                username = row.username,
                displayName = row.displayName,
                email = row.email,
                mobile = row.mobile,
                status = when (row.status) {
                    0 -> "DISABLED"
                    2 -> "LOCKED"
                    else -> "ENABLED"
                },
                groupId = row.groupId,
                groupName = row.groupName,
                tenantId = row.tenantId,
                tenantName = row.tenantName,
                roles = rolesByUserId[row.userId].orEmpty(),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt
            )
        }
    }

    private fun loadRolesByUserIds(userIds: List<Long>): Map<Long, Set<String>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }
        val placeholders = userIds.joinToString(",") { "?" }
        val args = userIds.toTypedArray()
        val rolesByUserId = linkedMapOf<Long, MutableSet<String>>()
        jdbcTemplate.query(
            """
            select distinct user_scope.user_id, r.role_code
            from sys_role r
            join (
                select ur.user_id, ur.role_id
                from sys_user_role ur
                where ur.user_id in ($placeholders)
                union
                select u.id as user_id, gr.role_id
                from sys_group_role gr
                join sys_user u on u.group_id = gr.group_id
                where u.id in ($placeholders)
            ) user_scope on user_scope.role_id = r.id
            order by user_scope.user_id, r.role_code
            """.trimIndent(),
            { rs ->
                val userId = rs.getLong("user_id")
                rolesByUserId.computeIfAbsent(userId) { linkedSetOf() }.add(rs.getString("role_code"))
            },
            *args,
            *args
        )
        return rolesByUserId
    }

    private fun findDefaultGroupId(tenantId: Long?): Long? =
        if (tenantId == null) {
            jdbcTemplate.queryForObject(
                "select id from sys_group where is_default = 1 order by id limit 1",
                Long::class.java
            )
        } else {
            jdbcTemplate.query(
                """
                select id
                from sys_group
                where is_default = 1
                  and (tenant_id = ? or tenant_id is null)
                order by case when tenant_id = ? then 0 else 1 end, id
                limit 1
                """.trimIndent(),
                { rs, _ -> rs.getLong("id") },
                tenantId,
                tenantId
            ).firstOrNull()
        }

    private fun statusCode(status: String?): Int? =
        when (status) {
            null -> null
            "ENABLED" -> 1
            "DISABLED" -> 0
            "LOCKED" -> 2
            else -> throw BizException("user.admin.status.invalid", "Invalid user status")
        }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) {
            setNull(index, java.sql.Types.BIGINT)
        } else {
            setLong(index, value)
        }
    }

    private fun java.sql.ResultSet.getNullableLong(columnName: String): Long? =
        getLong(columnName).takeUnless { wasNull() }

    private data class UserRow(
        val userId: Long,
        val username: String,
        val displayName: String?,
        val email: String?,
        val mobile: String?,
        val status: Int,
        val groupId: Long?,
        val groupName: String?,
        val tenantId: Long?,
        val tenantName: String?,
        val createdAt: String?,
        val updatedAt: String?
    )
}
