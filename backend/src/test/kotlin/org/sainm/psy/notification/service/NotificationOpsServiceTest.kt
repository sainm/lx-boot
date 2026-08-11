package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.notification.domain.NotificationBatchRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryOpsBucket
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.repository.NotificationRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class NotificationOpsServiceTest {

    @Mock private lateinit var notificationRepository: NotificationRepository
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy
    @Mock private lateinit var securityAuditService: SecurityAuditService

    private fun service(): NotificationOpsService {
        `when`(tenantAccessPolicy.currentTenantFilter("NOTIFICATION", "OPERATIONS")).thenReturn(7L)
        return NotificationOpsService(notificationRepository, tenantAccessPolicy, securityAuditService)
    }

    @Test
    fun `findDeliveryOpsSummary delegates to repository`() {
        val service = service()
        val summary = NotificationDeliveryOpsSummary(
            totalPending = 3,
            totalProcessing = 1,
            totalFailed = 2,
            oldestPendingCreatedAt = LocalDateTime.now(),
            buckets = listOf(
                NotificationDeliveryOpsBucket("PUSH", "PENDING", 3),
                NotificationDeliveryOpsBucket("PUSH", "FAILED", 2)
            )
        )
        `when`(notificationRepository.findDeliveryOpsSummary(7L)).thenReturn(summary)

        val result = service.findDeliveryOpsSummary()

        assertEquals(summary, result)
        verify(notificationRepository).findDeliveryOpsSummary(7L)
    }

    @Test
    fun `findDeliveries delegates to repository`() {
        val service = service()
        val delivery = deliverySummary()
        `when`(notificationRepository.findDeliveries(10L, 7L)).thenReturn(listOf(delivery))

        val result = service.findDeliveries(10L)

        assertEquals(listOf(delivery), result)
        verify(notificationRepository).findDeliveries(10L, 7L)
    }

    @Test
    fun `retryFailedDeliveries delegates channel filter to repository`() {
        val service = service()
        val retryResult = NotificationDeliveryRetryResult(notificationId = 10L, deliveryChannel = "PUSH", retriedCount = 2)
        `when`(notificationRepository.retryFailedDeliveries(10L, "PUSH", 7L)).thenReturn(retryResult)

        val result = service.retryFailedDeliveries(10L, "PUSH")

        assertEquals(retryResult, result)
        verify(notificationRepository).retryFailedDeliveries(10L, "PUSH", 7L)
        verify(securityAuditService).recordNotificationDeliveriesRetried(listOf(10L), "PUSH", 2)
    }

    @Test
    fun `retryFailedDeliveriesBatch audits normalized repository result`() {
        val service = service()
        val retryResult = NotificationBatchRetryResult(listOf(10L, 11L), "PUSH", 3)
        `when`(notificationRepository.retryFailedDeliveriesBatch(listOf(10L, 11L), "push", 7L)).thenReturn(retryResult)

        val result = service.retryFailedDeliveriesBatch(listOf(10L, 11L), "push")

        assertEquals(retryResult, result)
        verify(securityAuditService).recordNotificationDeliveriesRetried(listOf(10L, 11L), "PUSH", 3)
    }

    @Test
    fun `applyPushDeliveryCallback delegates normalized status to repository`() {
        val service = service()
        val occurredAt = LocalDateTime.of(2026, 4, 17, 11, 0)
        val receipt = NotificationDeliveryReceiptResult(
            deliveryId = 1L,
            notificationId = 10L,
            deliveryStatus = "CLICKED",
            readFlag = true,
            readTime = occurredAt,
            deliveredTime = occurredAt,
            clickedTime = occurredAt
        )
        `when`(
            notificationRepository.applyPushDeliveryCallback(
                1L,
                "CLICKED",
                "fcm",
                "msg-1",
                null,
                """{"source":"app"}""",
                occurredAt,
                occurredAt,
                occurredAt,
                7L
            )
        ).thenReturn(receipt)

        val result = service.applyPushDeliveryCallback(
            deliveryId = 1L,
            deliveryStatus = "clicked",
            providerName = "fcm",
            providerMessageId = "msg-1",
            errorMessage = null,
            callbackPayloadJson = """{"source":"app"}""",
            deliveredAt = occurredAt,
            clickedAt = occurredAt,
            readAt = occurredAt
        )

        assertEquals(receipt, result)
        verify(notificationRepository).applyPushDeliveryCallback(
            1L,
            "CLICKED",
            "fcm",
            "msg-1",
            null,
            """{"source":"app"}""",
            occurredAt,
            occurredAt,
            occurredAt,
            7L
        )
        verify(securityAuditService).recordNotificationDeliveryCallbackApplied(1L, 10L, "CLICKED", "fcm")
    }

    @Test
    fun `applyPushDeliveryCallback redacts callback credentials before persistence`() {
        val service = service()
        val receipt = NotificationDeliveryReceiptResult(1L, 10L, "FAILED", false, null, null, null)
        `when`(
            notificationRepository.applyPushDeliveryCallback(
                1L,
                "FAILED",
                "proxy",
                null,
                "Bearer [REDACTED] token=[REDACTED]",
                """{"token":"[REDACTED]","authorization":"[REDACTED]"}""",
                null,
                null,
                null,
                7L
            )
        ).thenReturn(receipt)

        service.applyPushDeliveryCallback(
            deliveryId = 1L,
            deliveryStatus = "FAILED",
            providerName = "proxy",
            providerMessageId = null,
            errorMessage = "Bearer abc.def token=secret-token",
            callbackPayloadJson = """{"token":"secret-token","authorization":"Bearer abc.def"}""",
            deliveredAt = null,
            clickedAt = null,
            readAt = null
        )

        verify(notificationRepository).applyPushDeliveryCallback(
            1L,
            "FAILED",
            "proxy",
            null,
            "Bearer [REDACTED] token=[REDACTED]",
            """{"token":"[REDACTED]","authorization":"[REDACTED]"}""",
            null,
            null,
            null,
            7L
        )
    }

    private fun deliverySummary() = NotificationDeliverySummary(
        id = 1L,
        notificationId = 10L,
        receiverUserId = 5L,
        deliveryChannel = "PUSH",
        deliveryStatus = "FAILED",
        readFlag = false,
        readTime = null,
        deviceId = 20L,
        providerName = "fcm",
        providerMessageId = "msg-1",
        deliveredTime = null,
        clickedTime = null,
        errorMessage = "Push provider not configured",
        callbackPayloadJson = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
}
