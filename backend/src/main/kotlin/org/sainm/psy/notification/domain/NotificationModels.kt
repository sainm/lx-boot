package org.sainm.psy.notification.domain

import java.time.LocalDateTime

data class MyNotificationSummary(
    val id: Long,
    val notificationType: String,
    val title: String,
    val content: String,
    val bizType: String?,
    val bizId: Long?,
    val targetPath: String?,
    val readFlag: Boolean,
    val readTime: LocalDateTime?,
    val createdAt: LocalDateTime
)

data class NotificationActionResult(
    val notificationId: Long,
    val readFlag: Boolean
)

data class UserDeviceSummary(
    val id: Long,
    val deviceType: String,
    val deviceId: String,
    val pushTokenMasked: String?,
    val appVersion: String?,
    val activeFlag: Boolean,
    val lastActiveAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class NotificationDeliverySummary(
    val id: Long,
    val notificationId: Long,
    val receiverUserId: Long,
    val deliveryChannel: String,
    val deliveryStatus: String,
    val readFlag: Boolean,
    val readTime: LocalDateTime?,
    val deviceId: Long?,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class NotificationDeliveryRetryResult(
    val notificationId: Long,
    val deliveryChannel: String?,
    val retriedCount: Int
)

data class PendingPushDelivery(
    val id: Long,
    val notificationId: Long,
    val receiverUserId: Long,
    val deviceId: Long?,
    val pushTokenSnapshot: String?,
    val title: String,
    val content: String,
    val deepLink: String?,
    val payloadJson: String?
)

data class NotificationDeliveryOpsBucket(
    val deliveryChannel: String,
    val deliveryStatus: String,
    val count: Long
)

data class NotificationDeliveryOpsSummary(
    val totalPending: Long,
    val totalProcessing: Long,
    val totalFailed: Long,
    val oldestPendingCreatedAt: LocalDateTime?,
    val buckets: List<NotificationDeliveryOpsBucket>
)

data class AdminNotificationOpsItem(
    val id: Long,
    val notificationType: String,
    val title: String,
    val bizType: String?,
    val bizId: Long?,
    val targetPath: String?,
    val createdAt: LocalDateTime,
    val totalDeliveries: Long,
    val pendingDeliveries: Long,
    val processingDeliveries: Long,
    val failedDeliveries: Long,
    val sentDeliveries: Long,
    val latestErrorMessage: String?
)

data class NotificationBatchRetryResult(
    val notificationIds: List<Long>,
    val deliveryChannel: String?,
    val retriedCount: Int
)

data class NotificationPolicy(
    val id: Long,
    val notificationType: String,
    val inAppEnabled: Boolean,
    val pushEnabled: Boolean,
    val cooldownMinutes: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class NotificationPolicySnapshot(
    val notificationType: String,
    val inAppEnabled: Boolean,
    val pushEnabled: Boolean,
    val cooldownMinutes: Int
)
