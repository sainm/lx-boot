package org.sainm.psy.notification.service

import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.stereotype.Service

@Service
class NotificationDispatchService(
    private val notificationRepository: NotificationRepository
) {

    fun notifyUsers(
        notificationType: String,
        title: String,
        content: String,
        bizType: String,
        bizId: Long?,
        targetPath: String?,
        payloadJson: String?,
        receiverUserIds: Collection<Long?>
    ) {
        val normalizedReceiverIds = receiverUserIds.mapNotNull { it }.distinct()
        if (normalizedReceiverIds.isEmpty()) {
            return
        }
        notificationRepository.createNotification(
            notificationType = notificationType,
            title = title,
            content = content,
            bizType = bizType,
            bizId = bizId,
            targetPath = targetPath,
            payloadJson = payloadJson,
            receiverUserIds = normalizedReceiverIds
        )
    }
}
