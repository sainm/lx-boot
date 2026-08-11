package org.sainm.psy.common.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class PsyMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val provider = mock<ObjectProvider<MeterRegistry>>().also {
        whenever(it.getIfAvailable()).thenReturn(registry)
    }
    private val metrics = PsyMetrics(provider)

    @Test
    fun `submission tags are bounded and unknown values cannot create high cardinality series`() {
        metrics.recordAssessmentSubmission("success", "manual", false, "HIGH", 1_000)
        metrics.recordAssessmentSubmission("tenant-12345", "custom-mode", null, "scale-specific-risk", 2_000)

        assertEquals(
            1.0,
            registry.get("psy.assessment.submissions")
                .tags("outcome", "SUCCESS", "mode", "MANUAL", "identity", "identified", "risk", "HIGH")
                .counter().count()
        )
        assertEquals(
            1.0,
            registry.get("psy.assessment.submissions")
                .tags("outcome", "UNKNOWN", "mode", "UNKNOWN", "identity", "unknown", "risk", "UNKNOWN")
                .counter().count()
        )
    }

    @Test
    fun `submission wrapper records failures and preserves the business exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            metrics.recordAssessmentSubmissionRun(
                mode = "AUTO",
                anonymous = { false },
                riskLevel = { "NORMAL" }
            ) { throw IllegalArgumentException("invalid answer") }
        }

        assertEquals(
            1.0,
            registry.get("psy.assessment.submissions")
                .tags("outcome", "FAILURE", "mode", "AUTO", "identity", "unknown", "risk", "UNKNOWN")
                .counter().count()
        )
    }

    @Test
    fun `scoring records both success and failure without swallowing the error`() {
        assertEquals("ok", metrics.recordScoringRun("SIMPLE_SUM") { "ok" })
        assertThrows(IllegalStateException::class.java) {
            metrics.recordScoringRun("CUSTOM_TENANT_FORMULA") { throw IllegalStateException("failed") }
        }

        assertEquals(
            1.0,
            registry.get("psy.scoring.runs").tags("method", "SIMPLE_SUM", "outcome", "success").counter().count()
        )
        assertEquals(
            1.0,
            registry.get("psy.scoring.runs").tags("method", "UNKNOWN", "outcome", "failure").counter().count()
        )
    }

    @Test
    fun `warning action rejects identifiers as tags`() {
        metrics.recordWarningAction("warning-991-secret")

        assertEquals(
            1.0,
            registry.get("psy.warning.actions").tag("action", "UNKNOWN").counter().count()
        )
    }

    @Test
    fun `warning queue gauges are updated without registering duplicate meters`() {
        metrics.recordWarningQueueState(open = 4, overdue = 1, oldestOpenSeconds = 90)
        metrics.recordWarningQueueState(open = 3, overdue = 0, oldestOpenSeconds = 30)

        assertEquals(3.0, registry.get("psy.warning.queue.size").tag("state", "open").gauge().value())
        assertEquals(0.0, registry.get("psy.warning.queue.size").tag("state", "overdue").gauge().value())
        assertEquals(30.0, registry.get("psy.warning.queue.oldest.open.seconds").gauge().value())
    }

    @Test
    fun `Prometheus scrape names match the alert rule contract`() {
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val prometheusProvider = mock<ObjectProvider<MeterRegistry>>().also {
            whenever(it.getIfAvailable()).thenReturn(prometheus)
        }
        val prometheusMetrics = PsyMetrics(prometheusProvider)
        prometheusMetrics.recordAssessmentSubmission("SUCCESS", "MANUAL", false, "NORMAL", 1_000)
        prometheusMetrics.recordScoringRun("SIMPLE_SUM") { Unit }
        prometheusMetrics.recordWarningAction("CREATED")
        prometheusMetrics.recordWarningQueueState(1, 1, 60)
        prometheusMetrics.recordNotificationQueueState(1, 0, 1, 60)
        prometheusMetrics.recordSchedulerSkipped("warning.escalation")
        prometheusMetrics.recordExportJobFailed("PDF")

        val scrape = prometheus.scrape()
        setOf(
            "psy_assessment_submissions_total",
            "psy_scoring_runs_total",
            "psy_warning_actions_total",
            "psy_warning_queue_size",
            "psy_notification_queue_size",
            "psy_notification_queue_oldest_pending_seconds",
            "psy_scheduler_runs_total",
            "psy_export_jobs_total"
        ).forEach { metricName -> assertTrue(scrape.contains(metricName), "missing Prometheus metric $metricName") }
    }

    @Test
    fun `metrics backend failure cannot change a business result`() {
        val failingProvider = mock<ObjectProvider<MeterRegistry>>().also {
            whenever(it.getIfAvailable()).thenThrow(IllegalStateException("registry unavailable"))
        }
        val failingMetrics = PsyMetrics(failingProvider)

        assertEquals("scored", failingMetrics.recordScoringRun("SIMPLE_SUM") { "scored" })
        assertEquals(
            "submitted",
            failingMetrics.recordAssessmentSubmissionRun(
                mode = "MANUAL",
                anonymous = { false },
                riskLevel = { "NORMAL" }
            ) { "submitted" }
        )
        assertDoesNotThrow { failingMetrics.recordWarningAction("CREATED") }
    }
}
