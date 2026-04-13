package org.sainm.psy.auth.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp

data class LoginActivitySummary(
    val id: Long,
    val userId: Long?,
    val principal: String?,
    val loginType: String,
    val result: String,
    val ip: String?,
    val userAgent: String?,
    val location: String?,
    val reason: String?,
    val createdAt: String
)

data class SecurityEventSummary(
    val id: Long,
    val eventType: String,
    val userId: Long?,
    val tenantId: Long?,
    val detail: Map<String, Any?>,
    val ip: String?,
    val createdAt: String
)

@Service
class AuthSessionService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    fun findRecentLoginActivities(userId: Long, principal: String, limit: Int = 10): List<LoginActivitySummary> =
        jdbcTemplate.query(
            """
            select id, user_id, principal, login_type, result, ip, user_agent, location, reason, created_at
            from sys_login_log
            where user_id = :userId or principal = :principal
            order by id desc
            limit :limit
            """.trimIndent(),
            mapOf(
                "userId" to userId,
                "principal" to principal,
                "limit" to limit.coerceIn(1, 50)
            )
        ) { rs, _ ->
            LoginActivitySummary(
                id = rs.getLong("id"),
                userId = rs.getNullableLong("user_id"),
                principal = rs.getString("principal"),
                loginType = rs.getString("login_type"),
                result = rs.getString("result"),
                ip = rs.getString("ip"),
                userAgent = rs.getString("user_agent"),
                location = rs.getString("location"),
                reason = rs.getString("reason"),
                createdAt = rs.getTimestamp("created_at").toInstant().toString()
            )
        }

    fun findRecentSecurityEvents(userId: Long, limit: Int = 10): List<SecurityEventSummary> =
        jdbcTemplate.query(
            """
            select id, event_type, user_id, tenant_id, detail_json, ip, created_at
            from sys_security_event
            where user_id = :userId
            order by id desc
            limit :limit
            """.trimIndent(),
            mapOf(
                "userId" to userId,
                "limit" to limit.coerceIn(1, 50)
            )
        ) { rs, _ ->
            SecurityEventSummary(
                id = rs.getLong("id"),
                eventType = rs.getString("event_type"),
                userId = rs.getNullableLong("user_id"),
                tenantId = rs.getNullableLong("tenant_id"),
                detail = parseDetailJson(rs.getString("detail_json")),
                ip = rs.getString("ip"),
                createdAt = rs.getTimestamp("created_at").toInstant().toString()
            )
        }

    private fun parseDetailJson(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return runCatching {
            objectMapper.readValue(raw, object : TypeReference<Map<String, Any?>>() {})
        }.getOrDefault(emptyMap())
    }

    private fun java.sql.ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }
}
