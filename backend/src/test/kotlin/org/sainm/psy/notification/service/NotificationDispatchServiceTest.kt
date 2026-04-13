package org.sainm.psy.notification.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.repository.NotificationPolicyRepository
import org.sainm.psy.notification.repository.NotificationRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource

@ExtendWith(MockitoExtension::class)
class NotificationDispatchServiceTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var notificationPolicyRepository: NotificationPolicyRepository

    private lateinit var notificationDispatchService: NotificationDispatchService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        notificationDispatchService = NotificationDispatchService(
            notificationRepository = notificationRepository,
            notificationPolicyService = NotificationPolicyService(notificationPolicyRepository),
            messages = LocalizedMessages(messageSource)
        )
    }

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

        verifyNoInteractions(notificationRepository)
    }

    @Test
    fun `notifyUsers deduplicates receiver ids before calling repository`() {
        notificationDispatchService.notifyUsers(
            notificationType = "WARNING_CLAIMED",
            title = "title",
            content = "content",
            bizType = "WARNING",
            bizId = 1L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(10L, 10L, 10L)
        )

        verify(notificationRepository).createNotification(
            notificationType = "WARNING_CLAIMED",
            title = "title",
            content = "content",
            bizType = "WARNING",
            bizId = 1L,
            targetPath = "/warnings",
            payloadJson = null,
            receiverUserIds = listOf(10L)
        )
    }

    @Test
    fun `notifyTaskAssigned uses centralized strategy`() {
        notificationDispatchService.notifyTaskAssigned(
            taskId = 12L,
            taskName = "Spring Survey",
            scaleId = 2L,
            endTime = "2026-04-12T12:00:00",
            status = "DRAFT",
            receiverUserIds = listOf(5L)
        )

        verify(notificationRepository).createNotification(
            notificationType = "TASK_ASSIGNED",
            title = "新的测评任务已分配",
            content = "任务《Spring Survey》已分配给你，请在截止时间前完成。",
            bizType = "TASK",
            bizId = 12L,
            targetPath = "/my/tasks/12",
            payloadJson = """{"taskId":12,"taskName":"Spring Survey","scaleId":2,"endTime":"2026-04-12T12:00:00","status":"DRAFT"}""",
            receiverUserIds = listOf(5L)
        )
    }

    @Test
    fun `notifyReportGenerated uses auto submit strategy when requested`() {
        notificationDispatchService.notifyReportGenerated(
            reportId = 301L,
            resultId = 201L,
            taskId = 101L,
            riskLevel = "HIGH",
            autoSubmitted = true,
            receiverUserIds = listOf(5L)
        )

        verify(notificationRepository).createNotification(
            notificationType = "REPORT_AUTO_SUBMITTED",
            title = "超时后系统已自动提交",
            content = "任务已过截止时间，系统已使用你已保存的答题自动提交。现在可以查看系统报告。",
            bizType = "REPORT",
            bizId = 301L,
            targetPath = "/reports/301?resultId=201&taskId=101&notificationSource=REPORT_AUTO_SUBMITTED",
            payloadJson = """{"reportId":301,"resultId":201,"taskId":101,"riskLevel":"HIGH","notificationSource":"REPORT_AUTO_SUBMITTED"}""",
            receiverUserIds = listOf(5L)
        )
    }

    @Test
    fun `notifyWarningReminder uses centralized strategy`() {
        notificationDispatchService.notifyWarningReminder(7L, listOf(20L))

        verify(notificationRepository).createNotification(
            notificationType = "WARNING_REMINDER",
            title = "预警跟进催办",
            content = "预警 #7 已有一段时间未结案，请继续跟进或完成干预闭环。",
            bizType = "WARNING",
            bizId = 7L,
            targetPath = "/warnings",
            payloadJson = """{"warningId":7,"reminder":true}""",
            receiverUserIds = listOf(20L)
        )
    }
}
