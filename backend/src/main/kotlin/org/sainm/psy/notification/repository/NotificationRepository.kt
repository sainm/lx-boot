package org.sainm.psy.notification.repository

import org.sainm.psy.notification.domain.MyNotificationSummary
import org.sainm.psy.notification.domain.NotificationActionResult
import org.sainm.psy.common.exception.BizException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class NotificationRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun createNotification(
        notificationType: String,
        title: String,
        content: String,
        bizType: String,
        bizId: Long?,
        targetPath: String?,
        receiverUserIds: List<Long>
    ): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """
                insert into psy_notification (
                    notification_type, title, content, biz_type, biz_id, target_path, created_at
                ) values (
                    :notificationType, :title, :content, :bizType, :bizId, :targetPath, :createdAt
                )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("notificationType", notificationType)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("bizType", bizType)
                .addValue("bizId", bizId)
                .addValue("targetPath", targetPath)
                .addValue("createdAt", now),
            keyHolder,
            arrayOf("id")
        )
        val notificationId = keyHolder.key?.toLong() ?: error("failed to create notification")
        receiverUserIds.forEach { receiverUserId ->
            jdbcTemplate.update(
                """
                    insert into psy_notification_delivery (
                        notification_id, receiver_user_id, read_flag, delivery_channel, created_at
                    ) values (
                        :notificationId, :receiverUserId, false, 'IN_APP', :createdAt
                    )
                """.trimIndent(),
                mapOf(
                    "notificationId" to notificationId,
                    "receiverUserId" to receiverUserId,
                    "createdAt" to now
                )
            )
        }
        return notificationId
    }

    fun findMyNotifications(userId: Long): List<MyNotificationSummary> {
        val sql = """
            select n.id,
                   n.notification_type,
                   n.title,
                   n.content,
                   n.biz_type,
                   n.biz_id,
                   n.target_path,
                   d.read_flag,
                   d.read_time,
                   n.created_at
            from psy_notification_delivery d
            join psy_notification n on n.id = d.notification_id
            where d.receiver_user_id = :userId
            order by n.created_at desc, n.id desc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("userId" to userId)) { rs, _ ->
            MyNotificationSummary(
                id = rs.getLong("id"),
                notificationType = rs.getString("notification_type"),
                title = rs.getString("title"),
                content = rs.getString("content"),
                bizType = rs.getString("biz_type"),
                bizId = rs.getObject("biz_id", java.lang.Long::class.java)?.toLong(),
                targetPath = rs.getString("target_path"),
                readFlag = rs.getBoolean("read_flag"),
                readTime = rs.getTimestamp("read_time")?.toLocalDateTime(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }
    }

    fun markAsRead(notificationId: Long, userId: Long): NotificationActionResult {
        val updated = jdbcTemplate.update(
            """
                update psy_notification_delivery
                set read_flag = true,
                    read_time = :readTime
                where notification_id = :notificationId
                  and receiver_user_id = :userId
            """.trimIndent(),
            mapOf(
                "notificationId" to notificationId,
                "userId" to userId,
                "readTime" to Timestamp.valueOf(LocalDateTime.now())
            )
        )
        if (updated == 0) {
            throw BizException("NOTIFICATION_NOT_FOUND", "通知不存在")
        }
        return NotificationActionResult(notificationId = notificationId, readFlag = true)
    }
}
