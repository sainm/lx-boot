package org.sainm.psy.notification.service

import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.notification.domain.AdminNotificationOpsItem
import org.sainm.psy.notification.domain.NotificationBatchRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NotificationOpsService(
    private val notificationRepository: NotificationRepository,
    private val currentUserFacade: CurrentUserFacade
) {

    fun findDeliveryOpsSummary(): NotificationDeliveryOpsSummary =
        notificationRepository.findDeliveryOpsSummary(currentTenantId())

    fun findAdminNotifications(
        notificationType: String?,
        bizType: String?,
        deliveryStatus: String?,
        limit: Int
    ): List<AdminNotificationOpsItem> =
        notificationRepository.findAdminNotifications(
            notificationType = notificationType,
            bizType = bizType,
            deliveryStatus = deliveryStatus,
            limit = limit,
            tenantId = currentTenantId()
        )

    fun findDeliveries(notificationId: Long): List<NotificationDeliverySummary> =
        notificationRepository.findDeliveries(notificationId, currentTenantId())

    @Transactional
    fun retryFailedDeliveries(notificationId: Long, deliveryChannel: String?): NotificationDeliveryRetryResult =
        notificationRepository.retryFailedDeliveries(notificationId, deliveryChannel, currentTenantId())

    @Transactional
    fun retryFailedDeliveriesBatch(notificationIds: List<Long>, deliveryChannel: String?): NotificationBatchRetryResult =
        notificationRepository.retryFailedDeliveriesBatch(notificationIds, deliveryChannel, currentTenantId())

    @Transactional
    fun applyPushDeliveryCallback(
        deliveryId: Long,
        deliveryStatus: String,
        providerName: String?,
        providerMessageId: String?,
        errorMessage: String?,
        callbackPayloadJson: String?,
        deliveredAt: LocalDateTime?,
        clickedAt: LocalDateTime?,
        readAt: LocalDateTime?
    ): NotificationDeliveryReceiptResult {
        val normalizedStatus = deliveryStatus.trim().uppercase()
        require(normalizedStatus in setOf("SENT", "DELIVERED", "FAILED", "CLICKED")) {
            "Unsupported delivery status: $deliveryStatus"
        }
        return notificationRepository.applyPushDeliveryCallback(
            deliveryId = deliveryId,
            deliveryStatus = normalizedStatus,
            providerName = providerName,
            providerMessageId = providerMessageId,
            errorMessage = errorMessage,
            callbackPayloadJson = callbackPayloadJson,
            deliveredAt = deliveredAt,
            clickedAt = clickedAt,
            readAt = readAt,
            tenantId = currentTenantId()
        )
    }

    private fun currentTenantId(): Long? = currentUserFacade.requireCurrentUser().tenantId
}
