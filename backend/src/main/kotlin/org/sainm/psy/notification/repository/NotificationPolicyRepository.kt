package org.sainm.psy.notification.repository

import org.sainm.psy.notification.domain.NotificationPolicy
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class NotificationPolicyRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findAll(): List<NotificationPolicy> =
        jdbcTemplate.query(
            """
            select id, notification_type, in_app_enabled, push_enabled, cooldown_minutes, created_at, updated_at
            from psy_notification_policy
            order by notification_type asc
            """.trimIndent(),
            emptyMap<String, Any>()
        ) { rs, _ ->
            NotificationPolicy(
                id = rs.getLong("id"),
                notificationType = rs.getString("notification_type"),
                inAppEnabled = rs.getBoolean("in_app_enabled"),
                pushEnabled = rs.getBoolean("push_enabled"),
                cooldownMinutes = rs.getInt("cooldown_minutes"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }

    fun findByType(notificationType: String): NotificationPolicy? =
        jdbcTemplate.query(
            """
            select id, notification_type, in_app_enabled, push_enabled, cooldown_minutes, created_at, updated_at
            from psy_notification_policy
            where notification_type = :notificationType
            """.trimIndent(),
            mapOf("notificationType" to notificationType)
        ) { rs, _ ->
            NotificationPolicy(
                id = rs.getLong("id"),
                notificationType = rs.getString("notification_type"),
                inAppEnabled = rs.getBoolean("in_app_enabled"),
                pushEnabled = rs.getBoolean("push_enabled"),
                cooldownMinutes = rs.getInt("cooldown_minutes"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }.firstOrNull()

    fun upsert(
        notificationType: String,
        inAppEnabled: Boolean,
        pushEnabled: Boolean,
        cooldownMinutes: Int
    ): NotificationPolicy {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val keyHolder = GeneratedKeyHolder()
        val updated = jdbcTemplate.update(
            """
            insert into psy_notification_policy (
                notification_type, in_app_enabled, push_enabled, cooldown_minutes, created_at, updated_at
            ) values (
                :notificationType, :inAppEnabled, :pushEnabled, :cooldownMinutes, :createdAt, :updatedAt
            )
            on conflict (notification_type)
            do update set in_app_enabled = excluded.in_app_enabled,
                          push_enabled = excluded.push_enabled,
                          cooldown_minutes = excluded.cooldown_minutes,
                          updated_at = excluded.updated_at
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("notificationType", notificationType)
                .addValue("inAppEnabled", inAppEnabled)
                .addValue("pushEnabled", pushEnabled)
                .addValue("cooldownMinutes", cooldownMinutes)
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        if (updated == 0) {
            return findByType(notificationType) ?: error("failed to upsert notification policy")
        }
        val id = keyHolder.key?.toLong()
        return if (id != null) {
            NotificationPolicy(
                id = id,
                notificationType = notificationType,
                inAppEnabled = inAppEnabled,
                pushEnabled = pushEnabled,
                cooldownMinutes = cooldownMinutes,
                createdAt = now.toLocalDateTime(),
                updatedAt = now.toLocalDateTime()
            )
        } else {
            findByType(notificationType) ?: error("failed to load notification policy")
        }
    }
}
