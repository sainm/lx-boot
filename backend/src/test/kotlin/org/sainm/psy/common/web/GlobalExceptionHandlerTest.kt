package org.sainm.psy.common.web

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.sainm.auth.core.exception.InvalidCredentialsException
import org.sainm.auth.core.exception.PasswordValidationException
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.exception.NotFoundBizException
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
        addMessage("auth.invalidCredentials", Locale.US, "Invalid username or password")
        addMessage("auth.password.validation", Locale.US, "Password does not meet policy")
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

        assertEquals(400, response.statusCode.value())
        assertEquals("REPORT_FORBIDDEN", response.body?.code)
        assertEquals("You are not allowed to access this report", response.body?.message)
    }

    @Test
    fun `handleBizException resolves Chinese message by locale`() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE)

        val response = handler.handleBizException(
            BizException("REPORT_FORBIDDEN", "fallback message"),
            request
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("REPORT_FORBIDDEN", response.body?.code)
        assertEquals("\u65E0\u6743\u8BBF\u95EE\u8BE5\u62A5\u544A", response.body?.message)
    }

    @Test
    fun `handleBizException resolves Japanese message by locale`() {
        LocaleContextHolder.setLocale(Locale.JAPAN)

        val response = handler.handleBizException(
            BizException("REPORT_FORBIDDEN", "fallback message"),
            request
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("REPORT_FORBIDDEN", response.body?.code)
        assertEquals("\u3053\u306E\u30EC\u30DD\u30FC\u30C8\u3092\u8868\u793A\u3059\u308B\u6A29\u9650\u304C\u3042\u308A\u307E\u305B\u3093", response.body?.message)
    }

    @Test
    fun `handleBizException maps stable not found codes to HTTP 404`() {
        val response = handler.handleBizException(
            BizException("TASK_NOT_FOUND", "Assessment task not found"),
            request
        )

        assertEquals(404, response.statusCode.value())
        assertEquals("TASK_NOT_FOUND", response.body?.code)
        assertEquals("Assessment task not found", response.body?.message)
    }

    @Test
    fun `handleNotFoundException preserves the stable business code`() {
        val response = handler.handleNotFoundException(
            NotFoundBizException("SCALE_NOT_FOUND", "Scale not found"),
            request
        )

        assertEquals("SCALE_NOT_FOUND", response.code)
        assertEquals("Scale not found", response.message)
    }

    @Test
    fun `handleAuthException returns stable unauthorized response`() {
        LocaleContextHolder.setLocale(Locale.US)

        val response = handler.handleAuthException(InvalidCredentialsException(), request)

        assertEquals(401, response.statusCode.value())
        assertEquals("AUTH_401001", response.body?.code)
        assertEquals("Invalid username or password", response.body?.message)
    }

    @Test
    fun `handleAuthException preserves non-401 auth status families`() {
        LocaleContextHolder.setLocale(Locale.US)

        val response = handler.handleAuthException(PasswordValidationException(), request)

        assertEquals(400, response.statusCode.value())
        assertEquals("AUTH_400003", response.body?.code)
        assertEquals("Password does not meet policy", response.body?.message)
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
