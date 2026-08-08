package org.sainm.psy.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class ExternalRegistrationRateLimitFilterTest {

    private val redisProvider = StaticListableBeanFactory().getBeanProvider(StringRedisTemplate::class.java)

    @Test
    fun `blocks repeated registration by email and preserves request body before the limit`() {
        val filter = ExternalRegistrationRateLimitFilter(ObjectMapper(), redisProvider, 10, 1, 600)
        val body = """{"username":"user01","email":"user@example.com","password":"password"}"""

        val firstRequest = request(body, "127.0.0.1")
        val firstResponse = MockHttpServletResponse()
        val firstChain = MockFilterChain()
        filter.doFilter(firstRequest, firstResponse, firstChain)

        assertEquals(200, firstResponse.status)
        assertEquals(body, firstChain.request?.reader?.readText())

        val secondResponse = MockHttpServletResponse()
        filter.doFilter(request(body, "127.0.0.2"), secondResponse, MockFilterChain())

        assertEquals(429, secondResponse.status)
        assertTrue(secondResponse.contentAsString.contains("RATE_LIMITED"))
    }

    @Test
    fun `blocks repeated registration by source ip`() {
        val filter = ExternalRegistrationRateLimitFilter(ObjectMapper(), redisProvider, 1, 10, 600)

        filter.doFilter(request("""{"email":"one@example.com"}""", "127.0.0.1"), MockHttpServletResponse(), MockFilterChain())
        val blocked = MockHttpServletResponse()
        filter.doFilter(request("""{"email":"two@example.com"}""", "127.0.0.1"), blocked, MockFilterChain())

        assertEquals(429, blocked.status)
    }

    private fun request(body: String, remoteAddress: String) = MockHttpServletRequest().apply {
        method = "POST"
        requestURI = "/auth/external-register"
        contentType = "application/json"
        setContent(body.toByteArray())
        remoteAddr = remoteAddress
    }
}
