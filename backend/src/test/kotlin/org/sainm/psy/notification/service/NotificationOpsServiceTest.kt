package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.notification.domain.NotificationDeliveryOpsBucket
import org.sainm.psy.notification.domain.NotificationDeliveryOpsSummary
import org.sainm.psy.notification.domain.NotificationDeliveryRetryResult
import org.sainm.psy.notification.domain.NotificationDeliverySummary
import org.sainm.psy.notification.repository.NotificationRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class NotificationOpsServiceTest {

    @Mock private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `findDeliveryOpsSummary delegates to repository`() {
        val service = NotificationOpsService(notificationRepository)
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
        `when`(notificationRepository.findDeliveryOpsSummary()).thenReturn(summary)

        val result = service.findDeliveryOpsSummary()

        assertEquals(summary, result)
        verify(notificationRepository).findDeliveryOpsSummary()
    }

    @Test
    fun `findDeliveries delegates to repository`() {
        val service = NotificationOpsService(notificationRepository)
        val delivery = deliverySummary()
        `when`(notificationRepository.findDeliveries(10L)).thenReturn(listOf(delivery))

        val result = service.findDeliveries(10L)

        assertEquals(listOf(delivery), result)
        verify(notificationRepository).findDeliveries(10L)
    }

    @Test
    fun `retryFailedDeliveries delegates channel filter to repository`() {
        val service = NotificationOpsService(notificationRepository)
        val retryResult = NotificationDeliveryRetryResult(notificationId = 10L, deliveryChannel = "PUSH", retriedCount = 2)
        `when`(notificationRepository.retryFailedDeliveries(10L, "PUSH")).thenReturn(retryResult)

        val result = service.retryFailedDeliveries(10L, "PUSH")

        assertEquals(retryResult, result)
        verify(notificationRepository).retryFailedDeliveries(10L, "PUSH")
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
        errorMessage = "Push provider not configured",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
}
