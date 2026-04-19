package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.notification.domain.NotificationDeliveryReceiptResult
import org.sainm.psy.notification.repository.NotificationRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {

    @Mock private lateinit var notificationRepository: NotificationRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade

    @Test
    fun `reportPushDeliveryReceived delegates current user to repository`() {
        val service = NotificationService(notificationRepository, currentUserFacade)
        val occurredAt = LocalDateTime.of(2026, 4, 17, 10, 30)
        val currentUser = sampleUserPrincipal()
        val receipt = sampleReceipt(deliveryStatus = "DELIVERED", deliveredTime = occurredAt)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser)
        `when`(notificationRepository.markPushDeliveryDelivered(11L, 9L, occurredAt)).thenReturn(receipt)

        val result = service.reportPushDeliveryReceived(11L, occurredAt)

        assertEquals(receipt, result)
        verify(notificationRepository).markPushDeliveryDelivered(11L, 9L, occurredAt)
    }

    @Test
    fun `reportPushDeliveryClicked delegates current user to repository`() {
        val service = NotificationService(notificationRepository, currentUserFacade)
        val occurredAt = LocalDateTime.of(2026, 4, 17, 10, 45)
        val currentUser = sampleUserPrincipal()
        val receipt = sampleReceipt(deliveryStatus = "CLICKED", deliveredTime = occurredAt, clickedTime = occurredAt)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser)
        `when`(notificationRepository.markPushDeliveryClicked(11L, 9L, occurredAt)).thenReturn(receipt)

        val result = service.reportPushDeliveryClicked(11L, occurredAt)

        assertEquals(receipt, result)
        verify(notificationRepository).markPushDeliveryClicked(11L, 9L, occurredAt)
    }

    private fun sampleUserPrincipal() = UserPrincipal(
        userId = 9L,
        username = "demo",
        displayName = "Demo",
        status = UserStatus.ENABLED,
        tenantId = null,
        groupId = null,
        roles = setOf("USER"),
        permissions = emptySet()
    )

    private fun sampleReceipt(
        deliveryStatus: String,
        deliveredTime: LocalDateTime?,
        clickedTime: LocalDateTime? = null
    ) = NotificationDeliveryReceiptResult(
        deliveryId = 11L,
        notificationId = 1001L,
        deliveryStatus = deliveryStatus,
        readFlag = clickedTime != null,
        readTime = clickedTime,
        deliveredTime = deliveredTime,
        clickedTime = clickedTime
    )
}


