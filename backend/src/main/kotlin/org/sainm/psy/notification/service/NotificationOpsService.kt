package org.sainm.psy.notification.service

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

    fun findDeliveries(notificationId: Long): List<NotificationDeliverySummary> =
        notificationRepository.findDeliveries(notificationId)

    @Transactional
    fun retryFailedDeliveries(notificationId: Long, deliveryChannel: String?): NotificationDeliveryRetryResult =
        notificationRepository.retryFailedDeliveries(notificationId, deliveryChannel)
}
