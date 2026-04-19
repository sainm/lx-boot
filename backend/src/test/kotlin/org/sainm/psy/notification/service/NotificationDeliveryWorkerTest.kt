package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.isNull
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.notification.domain.PendingPushDelivery
import org.sainm.psy.notification.repository.NotificationRepository

@ExtendWith(MockitoExtension::class)
class NotificationDeliveryWorkerTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var pushDeliveryGateway: PushDeliveryGateway

    private lateinit var notificationDeliveryWorker: NotificationDeliveryWorker

    @BeforeEach
    fun setUp() {
        notificationDeliveryWorker = NotificationDeliveryWorker(
            notificationRepository = notificationRepository,
            pushDeliveryGateway = pushDeliveryGateway,
            deliveryBatchSize = 100
        )
    }

    @Test
    fun `processPendingPushDeliveries marks successful delivery as sent`() {
        val delivery = sampleDelivery(id = 11L)
        `when`(notificationRepository.findPendingPushDeliveries(100)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(11L)).thenReturn(true)
        `when`(pushDeliveryGateway.send(delivery)).thenReturn(PushDeliveryAttemptResult(success = true))

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(1, processed)
        verify(notificationRepository).markDeliverySent(11L, null, null)
        verify(notificationRepository, never()).markDeliveryFailed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun `processPendingPushDeliveries marks failed delivery as failed`() {
        val delivery = sampleDelivery(id = 12L)
        `when`(notificationRepository.findPendingPushDeliveries(100)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(12L)).thenReturn(true)
        `when`(pushDeliveryGateway.send(delivery)).thenReturn(
            PushDeliveryAttemptResult(success = false, errorMessage = "VENDOR_UNAVAILABLE")
        )

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(1, processed)
        verify(notificationRepository).markDeliveryFailed(12L, "VENDOR_UNAVAILABLE")
        verify(notificationRepository, never()).markDeliverySent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `processPendingPushDeliveries skips delivery when processing lock fails`() {
        val delivery = sampleDelivery(id = 13L)
        `when`(notificationRepository.findPendingPushDeliveries(100)).thenReturn(listOf(delivery))
        `when`(notificationRepository.markDeliveryProcessing(13L)).thenReturn(false)

        val processed = notificationDeliveryWorker.processPendingPushDeliveries()

        assertEquals(0, processed)
        verify(pushDeliveryGateway, never()).send(delivery)
        verify(notificationRepository, never()).markDeliverySent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
        verify(notificationRepository, never()).markDeliveryFailed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString())
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
