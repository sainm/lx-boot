package org.sainm.psy.notification.service

import org.sainm.psy.notification.domain.AdminNotificationOpsItem
import org.sainm.psy.notification.domain.NotificationBatchRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationOpsService(
    private val notificationRepository: NotificationRepository
) {

    fun findDeliveryOpsSummary(): NotificationDeliveryOpsSummary =
        notificationRepository.findDeliveryOpsSummary()

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
            limit = limit
        )

    fun findDeliveries(notificationId: Long): List<NotificationDeliverySummary> =
        notificationRepository.findDeliveries(notificationId)

    @Transactional
    fun retryFailedDeliveries(notificationId: Long, deliveryChannel: String?): NotificationDeliveryRetryResult =
        notificationRepository.retryFailedDeliveries(notificationId, deliveryChannel)

    @Transactional
    fun retryFailedDeliveriesBatch(notificationIds: List<Long>, deliveryChannel: String?): NotificationBatchRetryResult =
        notificationRepository.retryFailedDeliveriesBatch(notificationIds, deliveryChannel)
}
