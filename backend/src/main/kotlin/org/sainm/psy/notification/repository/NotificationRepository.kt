package org.sainm.psy.notification.repository

import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.domain.MyNotificationSummary
import org.sainm.psy.notification.domain.NotificationActionResult
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryOpsBucket
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class NotificationRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val messages: LocalizedMessages
) {

    fun createNotification(
        notificationType: String,
        title: String,
        content: String,
        bizType: String,
        bizId: Long?,
        targetPath: String?,
        payloadJson: String?,
        receiverUserIds: List<Long>,
        deliveryChannels: Set<String> = setOf("IN_APP", "PUSH")
    ): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """
                insert into psy_notification (
                    notification_type, title, content, biz_type, biz_id, target_path,
                    target_type, target_id, deep_link, payload_json, created_at
                ) values (
                    :notificationType, :title, :content, :bizType, :bizId, :targetPath,
                    :targetType, :targetId, :deepLink, :payloadJson, :createdAt
                )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("notificationType", notificationType)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("bizType", bizType)
                .addValue("bizId", bizId)
                .addValue("targetPath", targetPath)
                .addValue("targetType", bizType)
                .addValue("targetId", bizId)
                .addValue("deepLink", targetPath)
                .addValue("payloadJson", payloadJson)
                .addValue("createdAt", now),
            keyHolder,
            arrayOf("id")
        )
        val notificationId = keyHolder.key?.toLong() ?: error("failed to create notification")
        if ("IN_APP" in deliveryChannels) {
            receiverUserIds.forEach { receiverUserId ->
                jdbcTemplate.update(
                    """
                        insert into psy_notification_delivery (
                            notification_id, receiver_user_id, read_flag, delivery_channel, delivery_status, created_at, updated_at
                        ) values (
                            :notificationId, :receiverUserId, false, 'IN_APP', 'SENT', :createdAt, :createdAt
                        )
                    """.trimIndent(),
                    mapOf(
                        "notificationId" to notificationId,
                        "receiverUserId" to receiverUserId,
                        "createdAt" to now
                    )
                )
            }
        }
        if ("PUSH" in deliveryChannels) {
            createPushDeliveries(notificationId, receiverUserIds.distinct(), now)
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
              and d.delivery_channel = 'IN_APP'
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
                  and delivery_channel = 'IN_APP'
            """.trimIndent(),
            mapOf(
                "notificationId" to notificationId,
                "userId" to userId,
                "readTime" to Timestamp.valueOf(LocalDateTime.now())
            )
        )
        if (updated == 0) {
            throw BizException("NOTIFICATION_NOT_FOUND", messages.get("notification.not_found"))
        }
        return NotificationActionResult(notificationId = notificationId, readFlag = true)
    }

    fun findDeliveries(notificationId: Long): List<NotificationDeliverySummary> {
        val sql = """
            select id,
                   notification_id,
                   receiver_user_id,
                   delivery_channel,
                   delivery_status,
                   read_flag,
                   read_time,
                   device_id,
                   error_message,
                   created_at,
                   updated_at
            from psy_notification_delivery
            where notification_id = :notificationId
            order by created_at asc, id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("notificationId" to notificationId)) { rs, _ ->
            NotificationDeliverySummary(
                id = rs.getLong("id"),
                notificationId = rs.getLong("notification_id"),
                receiverUserId = rs.getLong("receiver_user_id"),
                deliveryChannel = rs.getString("delivery_channel"),
                deliveryStatus = rs.getString("delivery_status"),
                readFlag = rs.getBoolean("read_flag"),
                readTime = rs.getTimestamp("read_time")?.toLocalDateTime(),
                deviceId = rs.getObject("device_id", java.lang.Long::class.java)?.toLong(),
                errorMessage = rs.getString("error_message"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }
    }

    fun retryFailedDeliveries(notificationId: Long, deliveryChannel: String?): NotificationDeliveryRetryResult {
        val channelClause = if (deliveryChannel.isNullOrBlank()) "" else "and delivery_channel = :deliveryChannel"
        val updated = jdbcTemplate.update(
            """
            update psy_notification_delivery
            set delivery_status = 'PENDING',
                error_message = null,
                updated_at = :updatedAt
            where notification_id = :notificationId
              and delivery_status in ('FAILED', 'SKIPPED')
              $channelClause
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("deliveryChannel", deliveryChannel?.trim()?.uppercase())
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
        return NotificationDeliveryRetryResult(
            notificationId = notificationId,
            deliveryChannel = deliveryChannel?.trim()?.uppercase(),
            retriedCount = updated
        )
    }

    fun findDeliveryOpsSummary(): NotificationDeliveryOpsSummary {
        val buckets = jdbcTemplate.query(
            """
            select delivery_channel, delivery_status, count(*) as total_count
            from psy_notification_delivery
            group by delivery_channel, delivery_status
            order by delivery_channel asc, delivery_status asc
            """.trimIndent(),
            emptyMap<String, Any>()
        ) { rs, _ ->
            NotificationDeliveryOpsBucket(
                deliveryChannel = rs.getString("delivery_channel"),
                deliveryStatus = rs.getString("delivery_status"),
                count = rs.getLong("total_count")
            )
        }
        val oldestPendingCreatedAt = jdbcTemplate.query(
            """
            select min(created_at) as oldest_pending_created_at
            from psy_notification_delivery
            where delivery_status = 'PENDING'
            """.trimIndent(),
            emptyMap<String, Any>()
        ) { rs, _ -> rs.getTimestamp("oldest_pending_created_at")?.toLocalDateTime() }
            .firstOrNull()

        return NotificationDeliveryOpsSummary(
            totalPending = buckets.filter { it.deliveryStatus == "PENDING" }.sumOf { it.count },
            totalProcessing = buckets.filter { it.deliveryStatus == "PROCESSING" }.sumOf { it.count },
            totalFailed = buckets.filter { it.deliveryStatus == "FAILED" }.sumOf { it.count },
            oldestPendingCreatedAt = oldestPendingCreatedAt,
            buckets = buckets
        )
    }

    fun findPendingPushDeliveries(limit: Int): List<PendingPushDelivery> {
        val sql = """
            select d.id,
                   d.notification_id,
                   d.receiver_user_id,
                   d.device_id,
                   d.push_token_snapshot,
                   n.title,
                   n.content,
                   n.deep_link,
                   n.payload_json
            from psy_notification_delivery d
            join psy_notification n on n.id = d.notification_id
            where d.delivery_channel = 'PUSH'
              and d.delivery_status = 'PENDING'
            order by d.created_at asc, d.id asc
            limit :limit
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("limit" to limit)) { rs, _ ->
            PendingPushDelivery(
                id = rs.getLong("id"),
                notificationId = rs.getLong("notification_id"),
                receiverUserId = rs.getLong("receiver_user_id"),
                deviceId = rs.getObject("device_id", java.lang.Long::class.java)?.toLong(),
                pushTokenSnapshot = rs.getString("push_token_snapshot"),
                title = rs.getString("title"),
                content = rs.getString("content"),
                deepLink = rs.getString("deep_link"),
                payloadJson = rs.getString("payload_json")
            )
        }
    }

    fun markDeliveryProcessing(deliveryId: Long): Boolean =
        jdbcTemplate.update(
            """
            update psy_notification_delivery
            set delivery_status = 'PROCESSING',
                error_message = null,
                updated_at = :updatedAt
            where id = :deliveryId
              and delivery_channel = 'PUSH'
              and delivery_status = 'PENDING'
            """.trimIndent(),
            mapOf(
                "deliveryId" to deliveryId,
                "updatedAt" to Timestamp.valueOf(LocalDateTime.now())
            )
        ) > 0

    fun markDeliverySent(deliveryId: Long) {
        jdbcTemplate.update(
            """
            update psy_notification_delivery
            set delivery_status = 'SENT',
                error_message = null,
                updated_at = :updatedAt
            where id = :deliveryId
            """.trimIndent(),
            mapOf(
                "deliveryId" to deliveryId,
                "updatedAt" to Timestamp.valueOf(LocalDateTime.now())
            )
        )
    }

    fun markDeliveryFailed(deliveryId: Long, errorMessage: String) {
        jdbcTemplate.update(
            """
            update psy_notification_delivery
            set delivery_status = 'FAILED',
                error_message = :errorMessage,
                updated_at = :updatedAt
            where id = :deliveryId
            """.trimIndent(),
            mapOf(
                "deliveryId" to deliveryId,
                "errorMessage" to errorMessage.take(2000),
                "updatedAt" to Timestamp.valueOf(LocalDateTime.now())
            )
        )
    }

    fun findUsersWithRecentNotifications(
        notificationType: String,
        receiverUserIds: Collection<Long>,
        since: LocalDateTime
    ): Set<Long> {
        if (receiverUserIds.isEmpty()) {
            return emptySet()
        }
        val sql = """
            select distinct d.receiver_user_id
            from psy_notification_delivery d
            join psy_notification n on n.id = d.notification_id
            where d.receiver_user_id in (:receiverUserIds)
              and n.notification_type = :notificationType
              and n.created_at >= :since
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "receiverUserIds" to receiverUserIds,
                "notificationType" to notificationType,
                "since" to Timestamp.valueOf(since)
            )
        ) { rs, _ -> rs.getLong("receiver_user_id") }.toSet()
    }

    private fun createPushDeliveries(notificationId: Long, receiverUserIds: List<Long>, now: Timestamp) {
        if (receiverUserIds.isEmpty()) {
            return
        }
        jdbcTemplate.update(
            """
            insert into psy_notification_delivery (
                notification_id,
                receiver_user_id,
                read_flag,
                delivery_channel,
                delivery_status,
                device_id,
                push_token_snapshot,
                created_at,
                updated_at
            )
            select :notificationId,
                   d.user_id,
                   false,
                   'PUSH',
                   'PENDING',
                   d.id,
                   d.push_token,
                   :createdAt,
                   :createdAt
            from psy_user_device d
            where d.user_id in (:receiverUserIds)
              and d.active_flag = true
              and d.push_token is not null
              and d.push_token <> ''
            """.trimIndent(),
            mapOf(
                "notificationId" to notificationId,
                "receiverUserIds" to receiverUserIds,
                "createdAt" to now
            )
        )
    }
}
