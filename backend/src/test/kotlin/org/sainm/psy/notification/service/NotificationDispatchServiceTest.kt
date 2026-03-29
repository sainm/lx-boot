package org.sainm.psy.notification.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.notification.repository.NotificationRepository

@ExtendWith(MockitoExtension::class)
class NotificationDispatchServiceTest {

    @Mock private lateinit var notificationRepository: NotificationRepository

    @InjectMocks
    private lateinit var notificationDispatchService: NotificationDispatchService

    // ── notifyUsers ───────────────────────────────────────────────────────────

    @Test
    fun `notifyUsers does nothing when receiverUserIds is empty`() {
        notificationDispatchService.notifyUsers(
            notificationType = "TEST",
            title = "title",
            content = "content",
            bizType = "T",
            bizId = 1L,
            targetPath = null,
            payloadJson = null,
            receiverUserIds = emptyList()
        )

        verify(notificationRepository, never()).createNotification(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `notifyUsers does nothing when all receiverUserIds are null`() {
        notificationDispatchService.notifyUsers(
            notificationType = "TEST",
            title = "title",
            content = "content",
            bizType = "T",
            bizId = 1L,
            targetPath = null,
            payloadJson = null,
            receiverUserIds = listOf(null, null)
        )

        verify(notificationRepository, never()).createNotification(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `notifyUsers deduplicates receiver ids before calling repository`() {
        notificationDispatchService.notifyUsers(
            notificationType = "WARNING_CLAIMED",
            title = "预警已接单",
            content = "content",
            bizType = "WARNING",
            bizId = 1L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(10L, 10L, 10L)
        )

        verify(notificationRepository).createNotification(
            notificationType = "WARNING_CLAIMED",
            title = "预警已接单",
            content = "content",
            bizType = "WARNING",
            bizId = 1L,
            targetPath = "/warnings",
            receiverUserIds = listOf(10L)
        )
    }

    @Test
    fun `notifyUsers filters out nulls and calls repository with remaining ids`() {
        notificationDispatchService.notifyUsers(
            notificationType = "TEST",
            title = "title",
            content = "content",
            bizType = "T",
            bizId = null,
            targetPath = null,
            payloadJson = null,
            receiverUserIds = listOf(null, 5L, null, 20L)
        )

        verify(notificationRepository).createNotification(
            notificationType = "TEST",
            title = "title",
            content = "content",
            bizType = "T",
            bizId = null,
            targetPath = null,
            receiverUserIds = listOf(5L, 20L)
        )
    }

    @Test
    fun `notifyUsers calls repository with all distinct non-null ids`() {
        notificationDispatchService.notifyUsers(
            notificationType = "INTERVENTION_CREATED",
            title = "新的干预记录已创建",
            content = "content",
            bizType = "INTERVENTION",
            bizId = 42L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(1L, 2L, 3L)
        )

        verify(notificationRepository).createNotification(
            notificationType = "INTERVENTION_CREATED",
            title = "新的干预记录已创建",
            content = "content",
            bizType = "INTERVENTION",
            bizId = 42L,
            targetPath = "/warnings",
            receiverUserIds = listOf(1L, 2L, 3L)
        )
    }
}
