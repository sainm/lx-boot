package org.sainm.psy.notification.service

import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.security.SensitiveTextSanitizer
import org.sainm.psy.common.security.TenantAccessPolicy
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
    private val tenantAccessPolicy: TenantAccessPolicy,
    private val securityAuditService: SecurityAuditService
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
    fun retryFailedDeliveries(notificationId: Long, deliveryChannel: String?): NotificationDeliveryRetryResult {
        val result = notificationRepository.retryFailedDeliveries(notificationId, deliveryChannel, currentTenantId())
        securityAuditService.recordNotificationDeliveriesRetried(
            notificationIds = listOf(notificationId),
            deliveryChannel = result.deliveryChannel,
            retriedCount = result.retriedCount
        )
        return result
    }

    @Transactional
    fun retryFailedDeliveriesBatch(notificationIds: List<Long>, deliveryChannel: String?): NotificationBatchRetryResult {
        val result = notificationRepository.retryFailedDeliveriesBatch(notificationIds, deliveryChannel, currentTenantId())
        securityAuditService.recordNotificationDeliveriesRetried(
            notificationIds = result.notificationIds,
            deliveryChannel = result.deliveryChannel,
            retriedCount = result.retriedCount
        )
        return result
    }

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
        val result = notificationRepository.applyPushDeliveryCallback(
            deliveryId = deliveryId,
            deliveryStatus = normalizedStatus,
            providerName = providerName,
            providerMessageId = providerMessageId,
            errorMessage = SensitiveTextSanitizer.redact(errorMessage, 2000),
            callbackPayloadJson = SensitiveTextSanitizer.redact(callbackPayloadJson, 20000),
            deliveredAt = deliveredAt,
            clickedAt = clickedAt,
            readAt = readAt,
            tenantId = currentTenantId()
        )
        securityAuditService.recordNotificationDeliveryCallbackApplied(
            deliveryId = result.deliveryId,
            notificationId = result.notificationId,
            deliveryStatus = result.deliveryStatus,
            providerName = providerName?.trim()?.ifEmpty { null }
        )
        return result
    }

    private fun currentTenantId(): Long? = tenantAccessPolicy.currentTenantFilter("NOTIFICATION", "OPERATIONS")
}
