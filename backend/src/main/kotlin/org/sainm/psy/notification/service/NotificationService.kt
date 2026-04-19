package org.sainm.psy.notification.service

import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.domain.MyNotificationSummary
import org.sainm.psy.notification.domain.NotificationActionResult
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val currentUserFacade: CurrentUserFacade
) {

    fun findMyNotifications(): List<MyNotificationSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return notificationRepository.findMyNotifications(currentUser.userId)
    }

    @Transactional
    fun markAsRead(notificationId: Long): NotificationActionResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        return notificationRepository.markAsRead(notificationId, currentUser.userId)
    }

    @Transactional
    fun reportPushDeliveryReceived(deliveryId: Long, occurredAt: LocalDateTime?): NotificationDeliveryReceiptResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        return notificationRepository.markPushDeliveryDelivered(
            deliveryId = deliveryId,
            userId = currentUser.userId,
            occurredAt = occurredAt ?: LocalDateTime.now()
        )
    }

    @Transactional
    fun reportPushDeliveryClicked(deliveryId: Long, occurredAt: LocalDateTime?): NotificationDeliveryReceiptResult {
        val currentUser = currentUserFacade.requireCurrentUser()
        return notificationRepository.markPushDeliveryClicked(
            deliveryId = deliveryId,
            userId = currentUser.userId,
            occurredAt = occurredAt ?: LocalDateTime.now()
        )
    }
}
