package org.sainm.psy.notification.repository

import org.sainm.psy.notification.domain.UserDeviceSummary
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class UserDeviceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun upsertDevice(
        userId: Long,
        deviceType: String,
        deviceId: String,
        pushToken: String?,
        appVersion: String?
    ): UserDeviceSummary {
        val existingId = jdbcTemplate.query(
            """
            select id
            from psy_user_device
            where user_id = :userId
              and device_id = :deviceId
            """.trimIndent(),
            mapOf("userId" to userId, "deviceId" to deviceId)
        ) { rs, _ -> rs.getLong("id") }.firstOrNull()

        val now = Timestamp.valueOf(LocalDateTime.now())
        if (existingId != null) {
            jdbcTemplate.update(
                """
                update psy_user_device
                set device_type = :deviceType,
                    push_token = :pushToken,
                    app_version = :appVersion,
                    active_flag = true,
                    last_active_at = :lastActiveAt,
                    updated_at = :updatedAt
                where id = :id
                  and user_id = :userId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", existingId)
                    .addValue("userId", userId)
                    .addValue("deviceType", deviceType)
                    .addValue("pushToken", pushToken)
                    .addValue("appVersion", appVersion)
                    .addValue("lastActiveAt", now)
                    .addValue("updatedAt", now)
            )
            return findById(existingId, userId) ?: error("device not found after update")
        }

        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """
            insert into psy_user_device (
                user_id, device_type, device_id, push_token, app_version, active_flag, last_active_at, created_at, updated_at
            ) values (
                :userId, :deviceType, :deviceId, :pushToken, :appVersion, true, :lastActiveAt, :createdAt, :updatedAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("deviceType", deviceType)
                .addValue("deviceId", deviceId)
                .addValue("pushToken", pushToken)
                .addValue("appVersion", appVersion)
                .addValue("lastActiveAt", now)
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        val id = keyHolder.key?.toLong() ?: error("failed to create user device")
        return findById(id, userId) ?: error("device not found after insert")
    }

    fun findActiveByUser(userId: Long): List<UserDeviceSummary> =
        findByUser(userId, activeOnly = true)

    fun findByUser(userId: Long, activeOnly: Boolean = false): List<UserDeviceSummary> {
        val activeSql = if (activeOnly) "and active_flag = true" else ""
        return jdbcTemplate.query(
            """
            select id, device_type, device_id, push_token, app_version, active_flag, last_active_at, created_at, updated_at
            from psy_user_device
            where user_id = :userId
            $activeSql
            order by last_active_at desc nulls last, updated_at desc, id desc
            """.trimIndent(),
            mapOf("userId" to userId)
        ) { rs, _ -> rs.toSummary() }
    }

    fun deactivate(userId: Long, deviceId: String): Boolean {
        val updated = jdbcTemplate.update(
            """
            update psy_user_device
            set active_flag = false,
                updated_at = :updatedAt
            where user_id = :userId
              and device_id = :deviceId
              and active_flag = true
            """.trimIndent(),
            mapOf(
                "userId" to userId,
                "deviceId" to deviceId,
                "updatedAt" to Timestamp.valueOf(LocalDateTime.now())
            )
        )
        return updated > 0
    }

    private fun findById(id: Long, userId: Long): UserDeviceSummary? =
        jdbcTemplate.query(
            """
            select id, device_type, device_id, push_token, app_version, active_flag, last_active_at, created_at, updated_at
            from psy_user_device
            where id = :id
              and user_id = :userId
            """.trimIndent(),
            mapOf("id" to id, "userId" to userId)
        ) { rs, _ -> rs.toSummary() }.firstOrNull()

    private fun java.sql.ResultSet.toSummary(): UserDeviceSummary =
        UserDeviceSummary(
            id = getLong("id"),
            deviceType = getString("device_type"),
            deviceId = getString("device_id"),
            pushTokenMasked = maskToken(getString("push_token")),
            appVersion = getString("app_version"),
            activeFlag = getBoolean("active_flag"),
            lastActiveAt = getTimestamp("last_active_at")?.toLocalDateTime(),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            updatedAt = getTimestamp("updated_at").toLocalDateTime()
        )

    private fun maskToken(token: String?): String? {
        if (token.isNullOrBlank()) {
            return null
        }
        return if (token.length <= 8) "****" else "${token.take(4)}****${token.takeLast(4)}"
    }
}
