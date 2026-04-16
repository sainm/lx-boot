package org.sainm.psy.notification.api

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.sainm.psy.notification.domain.AdminNotificationOpsItem
import org.sainm.psy.notification.domain.NotificationBatchRetryResult

data class NotificationOpsListQuery(
    val notificationType: String? = null,
    val bizType: String? = null,
    val deliveryStatus: String? = null,
    val limit: Int = 20
)

data class AdminNotificationOpsItemResponse(
    val id: Long,
    val notificationType: String,
    val title: String,
    val bizType: String?,
    val bizId: Long?,
    val targetPath: String?,
    val createdAt: String,
    val totalDeliveries: Long,
    val pendingDeliveries: Long,
    val processingDeliveries: Long,
    val failedDeliveries: Long,
    val sentDeliveries: Long,
    val latestErrorMessage: String?
) {
    companion object {
        fun from(item: AdminNotificationOpsItem) = AdminNotificationOpsItemResponse(
            id = item.id,
            notificationType = item.notificationType,
            title = item.title,
            bizType = item.bizType,
            bizId = item.bizId,
            targetPath = item.targetPath,
            createdAt = item.createdAt.toString(),
            totalDeliveries = item.totalDeliveries,
            pendingDeliveries = item.pendingDeliveries,
            processingDeliveries = item.processingDeliveries,
            failedDeliveries = item.failedDeliveries,
            sentDeliveries = item.sentDeliveries,
            latestErrorMessage = item.latestErrorMessage
        )
    }
}

data class BatchRetryNotificationDeliveriesRequest(
    @field:NotEmpty(message = "notification.ops.notification_ids_required")
    val notificationIds: List<Long>,

    @field:Size(max = 16, message = "notification.ops.delivery_channel_too_long")
    val deliveryChannel: String? = null
)

data class NotificationBatchRetryResultResponse(
    val notificationIds: List<Long>,
    val deliveryChannel: String?,
    val retriedCount: Int
) {
    companion object {
        fun from(result: NotificationBatchRetryResult) = NotificationBatchRetryResultResponse(
            notificationIds = result.notificationIds,
            deliveryChannel = result.deliveryChannel,
            retriedCount = result.retriedCount
        )
    }
}
