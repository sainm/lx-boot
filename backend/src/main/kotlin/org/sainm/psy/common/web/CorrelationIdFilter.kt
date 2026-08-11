package org.sainm.psy.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val previous = MDC.get(MDC_KEY)
        val correlationId = acceptedCorrelationId(request.getHeader(HEADER_NAME)) ?: UUID.randomUUID().toString()
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId)
        response.setHeader(HEADER_NAME, correlationId)
        MDC.put(MDC_KEY, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY)
            } else {
                MDC.put(MDC_KEY, previous)
            }
        }
    }

    private fun acceptedCorrelationId(value: String?): String? =
        value?.trim()?.takeIf { it.matches(CORRELATION_ID_PATTERN) }

    companion object {
        const val HEADER_NAME = "X-Correlation-Id"
        const val REQUEST_ATTRIBUTE = "psy.correlationId"
        const val MDC_KEY = "correlationId"

        private val CORRELATION_ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}
