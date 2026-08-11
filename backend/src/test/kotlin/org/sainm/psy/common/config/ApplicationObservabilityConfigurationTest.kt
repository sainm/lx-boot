package org.sainm.psy.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

class ApplicationObservabilityConfigurationTest {

    private val propertySources = YamlPropertySourceLoader()
        .load("application", ClassPathResource("application.yml"))

    @Test
    fun `Prometheus is exposed with safe health details and request histograms`() {
        assertTrue(property("management.endpoints.web.exposure.include").toString().split(',').contains("prometheus"))
        assertEquals(true, property("management.prometheus.metrics.export.enabled"))
        assertEquals("never", property("management.endpoint.health.show-details"))
        assertEquals(true, property("management.metrics.distribution.percentiles-histogram.http.server.requests"))
        assertEquals("${'$'}{spring.application.name}", property("management.metrics.tags.application"))
    }

    @Test
    fun `console uses structured JSON and renames the validated MDC correlation field`() {
        assertEquals("${'$'}{PSY_LOG_CONSOLE_FORMAT:logstash}", property("logging.structured.format.console"))
        assertEquals("psy-backend", property("logging.structured.json.add.service"))
        assertEquals("correlation_id", property("logging.structured.json.rename.correlationId"))
        assertEquals("trace_id", property("logging.structured.json.rename.traceId"))
        assertEquals("span_id", property("logging.structured.json.rename.spanId"))
    }

    @Test
    fun `tracing uses W3C propagation and an explicit bounded sampling setting`() {
        assertEquals("${'$'}{PSY_TRACING_ENABLED:true}", property("management.tracing.enabled"))
        assertEquals(
            "${'$'}{PSY_TRACING_SAMPLING_PROBABILITY:0.1}",
            property("management.tracing.sampling.probability")
        )
        assertEquals("W3C", property("management.tracing.propagation.type"))
    }

    private fun property(name: String): Any? = propertySources.firstNotNullOfOrNull { it.getProperty(name) }
}
