package org.sainm.psy.notification.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.domain.MyNotificationSummary
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.time.LocalDateTime
import java.util.Locale

class NotificationLocalizerTest {

    private lateinit var localizer: NotificationLocalizer

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
            // Mirror I18nConfig: never fall back to the JVM default locale, so
            // English resolves from messages.properties instead of the system
            // bundle (zh on this machine).
            setFallbackToSystemLocale(false)
            setUseCodeAsDefaultMessage(true)
        }
        localizer = NotificationLocalizer(LocalizedMessages(messageSource))
    }

    @AfterEach
    fun tearDown() {
        LocaleContextHolder.resetLocaleContext()
    }

    private fun notification(
        type: String,
        payload: String?,
        title: String = "任务《MT 任务》已分配给你，请在截止时间前完成。",
        content: String = "任务《MT 任务》已分配给你，请在截止时间前完成。",
        bizId: Long? = 1L
    ) = MyNotificationSummary(
        id = 1L,
        notificationType = type,
        title = title,
        content = content,
        bizType = "TASK",
        bizId = bizId,
        targetPath = "/my/tasks/1",
        readFlag = false,
        readTime = null,
        createdAt = LocalDateTime.now(),
        payloadJson = payload
    )

    @Test
    fun `renders japanese text for a notification created in chinese`() {
        LocaleContextHolder.setLocale(Locale.JAPANESE)
        val localized = localizer.localize(
            notification("TASK_ASSIGNED", """{"taskId":1,"taskName":"MT 任务"}""")
        )
        assertEquals("新しいアセスメントタスク", localized.title)
        assertEquals("タスク「MT 任务」が割り当てられました。期限までに完了してください。", localized.content)
    }

    @Test
    fun `renders english text for parameterless notifications`() {
        LocaleContextHolder.setLocale(Locale.US)
        val localized = localizer.localize(notification("REPORT_GENERATED", null))
        assertEquals("System report generated", localized.title)
    }

    @Test
    fun `keeps stored text when the payload lacks the parameter`() {
        LocaleContextHolder.setLocale(Locale.JAPANESE)
        val original = notification("TASK_ASSIGNED", null, title = "任务《MT 任务》已分配给你。", content = "任务《MT 任务》已分配给你。")
        assertEquals(original, localizer.localize(original))
    }

    @Test
    fun `keeps stored text for unknown notification types`() {
        LocaleContextHolder.setLocale(Locale.JAPANESE)
        val original = notification("CUSTOM_NOTICE", null)
        assertEquals(original, localizer.localize(original))
    }

    @Test
    fun `localizes warning reminders using the business id`() {
        LocaleContextHolder.setLocale(Locale.US)
        val localized = localizer.localize(
            notification("WARNING_REMINDER", null, title = "预警跟进催办", content = "预警 #3 已有一段时间未结案。", bizId = 3L)
        )
        assertEquals("Warning follow-up reminder", localized.title)
        assertEquals(
            "Warning #3 has not been closed for a while. Please continue follow-up or close the intervention loop.",
            localized.content
        )
    }
}
