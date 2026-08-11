package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.sainm.psy.notification.repository.NotificationRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class NotificationDeliveryWorkerTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var pushDeliveryGateway: PushDeliveryGateway

    private lateinit var notificationDeliveryWorker: NotificationDeliveryWorker
    private val now = LocalDateTime.of(2026, 8, 8, 12, 0)

    @BeforeEach
    fun setUp() {
        notificationDeliveryWorker = NotificationDeliveryWorker(
            notificationRepository = notificationRepository,
            pushDeliveryGateway = pushDeliveryGateway,
            deliveryBatchSize = 100,
            maxAttempts = 3,
            initialRetryDelaySeconds = 60,
            maxRetryDelaySeconds = 3600,
            processingTimeoutMinutes = 10,
            clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        )
    }

    @Test
    fun `processPendingPushDeliveries marks successful delivery as sent`() {
        val delivery = sampleDelivery(id = 11L)
        `when`(notificationRepository.findPendingPushDeliveries(100, now)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(11L, now)).thenReturn("lease-11")
        `when`(pushDeliveryGateway.send(delivery)).thenReturn(PushDeliveryAttemptResult(success = true))

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(1, processed)
        verify(notificationRepository).markDeliverySent(11L, "lease-11", null, null)
    }

    @Test
    fun `processPendingPushDeliveries marks failed delivery as failed`() {
        val delivery = sampleDelivery(id = 12L)
        `when`(notificationRepository.findPendingPushDeliveries(100, now)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(12L, now)).thenReturn("lease-12")
        `when`(pushDeliveryGateway.send(delivery)).thenReturn(
            PushDeliveryAttemptResult(success = false, errorMessage = "VENDOR_UNAVAILABLE")
        )

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(1, processed)
        verify(notificationRepository).markDeliveryAttemptFailed(
            deliveryId = 12L,
            processingToken = "lease-12",
            previousRetryCount = 0,
            maxAttempts = 3,
            nextRetryAt = now.plusSeconds(60),
            errorMessage = "VENDOR_UNAVAILABLE",
            now = now
        )
        verify(notificationRepository, never()).markDeliverySent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `processPendingPushDeliveries skips delivery when processing lock fails`() {
        val delivery = sampleDelivery(id = 13L)
        `when`(notificationRepository.findPendingPushDeliveries(100, now)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(13L, now)).thenReturn(null)

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(0, processed)
        verify(pushDeliveryGateway, never()).send(delivery)
        verify(notificationRepository, never()).markDeliverySent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `processPendingPushDeliveries sends exhausted attempt to dead letter`() {
        val delivery = sampleDelivery(id = 14L).copy(retryCount = 2)
        `when`(notificationRepository.findPendingPushDeliveries(100, now)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(14L, now)).thenReturn("lease-14")
        `when`(pushDeliveryGateway.send(delivery)).thenReturn(
            PushDeliveryAttemptResult(success = false, errorMessage = "token=secret-value vendor unavailable")
        )

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(1, processed)
        verify(notificationRepository).markDeliveryAttemptFailed(
            deliveryId = 14L,
            processingToken = "lease-14",
            previousRetryCount = 2,
            maxAttempts = 3,
            nextRetryAt = null,
            errorMessage = "token=[REDACTED] vendor unavailable",
            now = now
        )
    }

    private fun sampleDelivery(id: Long) = PendingPushDelivery(
        id = id,
        notificationId = 1001L,
        receiverUserId = 2001L,
        deviceId = 3001L,
        pushTokenSnapshot = "token-demo",
        title = "title",
        content = "content",
        deepLink = "/my/tasks/1",
        payloadJson = """{"taskId":1}"""
    )
}
