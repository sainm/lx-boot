package org.sainm.psy.common.web

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.sainm.psy.common.exception.BizException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import java.util.Locale

class GlobalExceptionHandlerTest {
    private val request = MockHttpServletRequest().apply {
        method = "POST"
        requestURI = "/api/v1/answer-sheets/submit"
    }

    private val messageSource = StaticMessageSource().apply {
        addMessage("REPORT_FORBIDDEN", Locale.US, "You are not allowed to access this report")
        addMessage("REPORT_FORBIDDEN", Locale.SIMPLIFIED_CHINESE, "\u65E0\u6743\u8BBF\u95EE\u8BE5\u62A5\u544A")
        addMessage("REPORT_FORBIDDEN", Locale.JAPAN, "\u3053\u306E\u30EC\u30DD\u30FC\u30C8\u3092\u8868\u793A\u3059\u308B\u6A29\u9650\u304C\u3042\u308A\u307E\u305B\u3093")
        addMessage("INTERNAL_ERROR", Locale.US, "Internal server error")
    }
    private val handler = GlobalExceptionHandler(messageSource)

    @AfterEach
    fun resetLocale() {
        LocaleContextHolder.resetLocaleContext()
    }

    @Test
    fun `handleBizException resolves English message by locale`() {
        LocaleContextHolder.setLocale(Locale.US)

        val response = handler.handleBizException(
            BizException("REPORT_FORBIDDEN", "fallback message"),
            request
        )

        assertEquals("REPORT_FORBIDDEN", response.code)
        assertEquals("You are not allowed to access this report", response.message)
    }

    @Test
    fun `handleBizException resolves Chinese message by locale`() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE)

        val response = handler.handleBizException(
            BizException("REPORT_FORBIDDEN", "fallback message"),
            request
        )

        assertEquals("REPORT_FORBIDDEN", response.code)
        assertEquals("\u65E0\u6743\u8BBF\u95EE\u8BE5\u62A5\u544A", response.message)
    }

    @Test
    fun `handleBizException resolves Japanese message by locale`() {
        LocaleContextHolder.setLocale(Locale.JAPAN)

        val response = handler.handleBizException(
            BizException("REPORT_FORBIDDEN", "fallback message"),
            request
        )

        assertEquals("REPORT_FORBIDDEN", response.code)
        assertEquals("\u3053\u306E\u30EC\u30DD\u30FC\u30C8\u3092\u8868\u793A\u3059\u308B\u6A29\u9650\u304C\u3042\u308A\u307E\u305B\u3093", response.message)
    }

    @Test
    fun `handleOtherException does not expose raw exception message`() {
        LocaleContextHolder.setLocale(Locale.US)

        val response = handler.handleOtherException(
            RuntimeException("database password leaked"),
            request
        )

        assertEquals("INTERNAL_ERROR", response.code)
        assertEquals("Internal server error", response.message)
    }
}
