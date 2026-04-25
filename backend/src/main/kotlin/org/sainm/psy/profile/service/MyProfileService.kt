package org.sainm.psy.profile.service

import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.profile.api.MyProfileResponse
import org.sainm.psy.profile.api.UpdateMyProfileRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

@Service
class MyProfileService(
    private val jdbcTemplate: JdbcTemplate,
    private val currentUserFacade: CurrentUserFacade
) {

    fun getMyProfile(): MyProfileResponse =
        findProfile(currentUserFacade.requireCurrentUserId())
            ?: throw BizException("PROFILE_NOT_FOUND", "Current user profile was not found")

    @Transactional
    fun updateMyProfile(request: UpdateMyProfileRequest): MyProfileResponse {
        val userId = currentUserFacade.requireCurrentUserId()
        val email = request.email.normalized()
        val mobile = request.mobile.normalized()

        ensureUniqueContact(userId, "email", email, "PROFILE_EMAIL_EXISTS", "Email is already used by another account")
        ensureUniqueContact(userId, "mobile", mobile, "PROFILE_MOBILE_EXISTS", "Mobile is already used by another account")

        val updated = jdbcTemplate.update(
            """
            update sys_user
            set nickname = ?,
                display_name = ?,
                email = ?,
                mobile = ?,
                avatar_url = ?,
                updated_at = current_timestamp
            where id = ?
              and deleted = 0
            """.trimIndent(),
            request.nickname.normalized(),
            request.displayName.normalized(),
            email,
            mobile,
            request.avatarUrl.normalized(),
            userId
        )
        if (updated == 0) {
            throw BizException("PROFILE_NOT_FOUND", "Current user profile was not found")
        }
        return getMyProfile()
    }

    private fun ensureUniqueContact(userId: Long, columnName: String, value: String?, code: String, message: String) {
        if (value == null) return
        val exists = jdbcTemplate.queryForObject(
            """
            select exists(
                select 1
                from sys_user
                where $columnName = ?
                  and id <> ?
                  and deleted = 0
            )
            """.trimIndent(),
            Boolean::class.java,
            value,
            userId
        ) ?: false
        if (exists) {
            throw BizException(code, message)
        }
    }

    private fun findProfile(userId: Long): MyProfileResponse? =
        jdbcTemplate.query(
            """
            select u.id,
                   u.username,
                   u.nickname,
                   u.display_name,
                   u.email,
                   u.mobile,
                   u.avatar_url,
                   u.group_id,
                   g.group_name,
                   u.tenant_id,
                   t.tenant_name,
                   u.updated_at
            from sys_user u
            left join sys_group g on g.id = u.group_id
            left join sys_tenant t on t.id = u.tenant_id
            where u.id = ?
              and u.deleted = 0
            """.trimIndent(),
            { rs, _ -> rs.toProfile(findRoles(userId)) },
            userId
        ).firstOrNull()

    private fun findRoles(userId: Long): Set<String> =
        jdbcTemplate.query(
            """
            select r.role_code
            from sys_user_role ur
            join sys_role r on r.id = ur.role_id
            where ur.user_id = ?
            order by r.role_code
            """.trimIndent(),
            { rs, _ -> rs.getString("role_code") },
            userId
        ).toSet()

    private fun ResultSet.toProfile(roles: Set<String>) = MyProfileResponse(
        userId = getLong("id"),
        username = getString("username"),
        nickname = getString("nickname"),
        displayName = getString("display_name"),
        email = getString("email"),
        mobile = getString("mobile"),
        avatarUrl = getString("avatar_url"),
        groupId = getNullableLong("group_id"),
        groupName = getString("group_name"),
        tenantId = getNullableLong("tenant_id"),
        tenantName = getString("tenant_name"),
        roles = roles,
        updatedAt = getTimestamp("updated_at")?.toLocalDateTime()?.toString()
    )

    private fun ResultSet.getNullableLong(column: String): Long? =
        getLong(column).takeUnless { wasNull() }

    private fun String?.normalized(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }
}
