package org.sainm.psy.common.web

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter()

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `valid caller correlation id is propagated to response request and MDC`() {
        val request = MockHttpServletRequest().apply {
            addHeader(CorrelationIdFilter.HEADER_NAME, "case-2026_08.11")
        }
        val response = MockHttpServletResponse()
        var observedMdc: String? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            observedMdc = MDC.get(CorrelationIdFilter.MDC_KEY)
        })

        assertEquals("case-2026_08.11", response.getHeader(CorrelationIdFilter.HEADER_NAME))
        assertEquals("case-2026_08.11", request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE))
        assertEquals("case-2026_08.11", observedMdc)
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY))
    }

    @Test
    fun `unsafe caller value is replaced and cannot inject log content`() {
        val request = MockHttpServletRequest().apply {
            addHeader(CorrelationIdFilter.HEADER_NAME, "unsafe\nvalue")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        val generated = response.getHeader(CorrelationIdFilter.HEADER_NAME)
        assertNotEquals("unsafe\nvalue", generated)
        assertTrue(generated!!.matches(Regex("^[0-9a-f-]{36}$")))
    }

    @Test
    fun `preexisting MDC value is restored for a reused worker thread`() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-request")
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ ->
            assertNotEquals("outer-request", MDC.get(CorrelationIdFilter.MDC_KEY))
        })

        assertEquals("outer-request", MDC.get(CorrelationIdFilter.MDC_KEY))
    }
}
