package org.sainm.psy.common.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Service
class PsyMetrics(
    private val meterRegistryProvider: ObjectProvider<MeterRegistry>
) {

    private val notificationGaugesRegistered = AtomicBoolean(false)
    private val pendingNotifications = AtomicLong(0)
    private val processingNotifications = AtomicLong(0)
    private val failedNotifications = AtomicLong(0)
    private val oldestPendingAgeSeconds = AtomicLong(0)
    private val warningGaugesRegistered = AtomicBoolean(false)
    private val openWarnings = AtomicLong(0)
    private val overdueWarnings = AtomicLong(0)
    private val oldestOpenWarningAgeSeconds = AtomicLong(0)

    fun <T> recordSchedulerRun(jobName: String, block: () -> T): T {
        val registry = availableRegistry() ?: return block()
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (e: Exception) {
            safelyRecord { recordScheduler(registry, jobName, "failure", System.nanoTime() - start) }
            throw e
        }
        safelyRecord { recordScheduler(registry, jobName, "success", System.nanoTime() - start) }
        return result
    }

    fun recordSchedulerSkipped(jobName: String) {
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                Counter.builder("psy.scheduler.runs")
                    .description("Scheduler executions grouped by job and outcome")
                    .tag("job", jobName)
                    .tag("outcome", "skipped")
                    .register(registry)
                    .increment()
            }
        }
    }

    fun recordExportJobDone(exportFormat: String?, fileBytes: Int) {
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                recordExportJob(registry, "done", exportFormat)
                DistributionSummary.builder("psy.export.job.file.bytes")
                    .description("Exported report file size in bytes")
                    .tag("format", exportFormat.orEmpty().ifBlank { "UNKNOWN" })
                    .register(registry)
                    .record(fileBytes.toDouble())
            }
        }
    }

    fun recordExportJobFailed(exportFormat: String?) {
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                recordExportJob(registry, "failed", exportFormat)
            }
        }
    }

    fun recordNotificationDeliveryAttempt(outcome: String) {
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                Counter.builder("psy.notification.delivery.attempts")
                    .description("Push delivery attempts grouped by outcome")
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment()
            }
        }
    }

    fun recordRecoveredNotificationDeliveries(count: Int) {
        if (count <= 0) return
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                Counter.builder("psy.notification.delivery.recovered")
                    .description("Stale push deliveries recovered after a processing timeout")
                    .register(registry)
                    .increment(count.toDouble())
            }
        }
    }

    fun recordNotificationQueueState(pending: Long, processing: Long, failed: Long, oldestPendingSeconds: Long) {
        safelyRecord {
            val registry = meterRegistryProvider.getIfAvailable() ?: return@safelyRecord
            pendingNotifications.set(pending.coerceAtLeast(0))
            processingNotifications.set(processing.coerceAtLeast(0))
            failedNotifications.set(failed.coerceAtLeast(0))
            oldestPendingAgeSeconds.set(oldestPendingSeconds.coerceAtLeast(0))
            if (notificationGaugesRegistered.compareAndSet(false, true)) {
                io.micrometer.core.instrument.Gauge.builder("psy.notification.queue.size", pendingNotifications) { it.get().toDouble() }
                    .tag("status", "pending").register(registry)
                io.micrometer.core.instrument.Gauge.builder("psy.notification.queue.size", processingNotifications) { it.get().toDouble() }
                    .tag("status", "processing").register(registry)
                io.micrometer.core.instrument.Gauge.builder("psy.notification.queue.size", failedNotifications) { it.get().toDouble() }
                    .tag("status", "failed_or_dead_letter").register(registry)
                io.micrometer.core.instrument.Gauge.builder("psy.notification.queue.oldest.pending.seconds", oldestPendingAgeSeconds) { it.get().toDouble() }
                    .register(registry)
            }
        }
    }

    fun recordAssessmentSubmission(
        outcome: String,
        mode: String,
        anonymous: Boolean?,
        riskLevel: String?,
        nanos: Long
    ) {
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                val tags = arrayOf(
                    "outcome", normalize(outcome, SUBMISSION_OUTCOMES),
                    "mode", normalize(mode, SUBMISSION_MODES),
                    "identity", when (anonymous) {
                        true -> "anonymous"
                        false -> "identified"
                        null -> "unknown"
                    },
                    "risk", normalize(riskLevel, RISK_LEVELS)
                )
                Counter.builder("psy.assessment.submissions")
                    .description("Assessment submission requests grouped by bounded business outcome")
                    .tags(*tags)
                    .register(registry)
                    .increment()
                Timer.builder("psy.assessment.submission.duration")
                    .description("Assessment submission request duration")
                    .tags(*tags)
                    .register(registry)
                    .record(nanos.coerceAtLeast(0), TimeUnit.NANOSECONDS)
            }
        }
    }

    fun <T> recordAssessmentSubmissionRun(
        mode: String,
        anonymous: (T) -> Boolean?,
        riskLevel: (T) -> String?,
        block: () -> T
    ): T {
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (error: Exception) {
            safelyRecord {
                recordAssessmentSubmission(
                    outcome = "FAILURE",
                    mode = mode,
                    anonymous = null,
                    riskLevel = null,
                    nanos = System.nanoTime() - start
                )
            }
            throw error
        }
        safelyRecord {
            recordAssessmentSubmission(
                outcome = "SUCCESS",
                mode = mode,
                anonymous = anonymous(result),
                riskLevel = riskLevel(result),
                nanos = System.nanoTime() - start
            )
        }
        return result
    }

    fun <T> recordScoringRun(scoreMethod: String?, block: () -> T): T {
        val registry = availableRegistry() ?: return block()
        val method = normalize(scoreMethod, SCORE_METHODS)
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (error: Exception) {
            safelyRecord { recordScoring(registry, method, "failure", System.nanoTime() - start) }
            throw error
        }
        safelyRecord { recordScoring(registry, method, "success", System.nanoTime() - start) }
        return result
    }

    fun recordWarningAction(action: String, count: Int = 1) {
        if (count <= 0) return
        safelyRecord {
            meterRegistryProvider.getIfAvailable()?.let { registry ->
                Counter.builder("psy.warning.actions")
                    .description("Warning lifecycle actions grouped by bounded action")
                    .tag("action", normalize(action, WARNING_ACTIONS))
                    .register(registry)
                    .increment(count.toDouble())
            }
        }
    }

    fun recordWarningQueueState(open: Long, overdue: Long, oldestOpenSeconds: Long) {
        safelyRecord {
            val registry = meterRegistryProvider.getIfAvailable() ?: return@safelyRecord
            openWarnings.set(open.coerceAtLeast(0))
            overdueWarnings.set(overdue.coerceAtLeast(0))
            oldestOpenWarningAgeSeconds.set(oldestOpenSeconds.coerceAtLeast(0))
            if (warningGaugesRegistered.compareAndSet(false, true)) {
                io.micrometer.core.instrument.Gauge.builder("psy.warning.queue.size", openWarnings) { it.get().toDouble() }
                    .tag("state", "open").register(registry)
                io.micrometer.core.instrument.Gauge.builder("psy.warning.queue.size", overdueWarnings) { it.get().toDouble() }
                    .tag("state", "overdue").register(registry)
                io.micrometer.core.instrument.Gauge.builder(
                    "psy.warning.queue.oldest.open.seconds",
                    oldestOpenWarningAgeSeconds
                ) { it.get().toDouble() }.register(registry)
            }
        }
    }

    private fun recordScheduler(registry: MeterRegistry, jobName: String, outcome: String, nanos: Long) {
        Counter.builder("psy.scheduler.runs")
            .description("Scheduler executions grouped by job and outcome")
            .tag("job", jobName)
            .tag("outcome", outcome)
            .register(registry)
            .increment()
        Timer.builder("psy.scheduler.duration")
            .description("Scheduler execution duration")
            .tag("job", jobName)
            .tag("outcome", outcome)
            .register(registry)
            .record(nanos, TimeUnit.NANOSECONDS)
    }

    private fun recordExportJob(registry: MeterRegistry, status: String, exportFormat: String?) {
        Counter.builder("psy.export.jobs")
            .description("Async export jobs grouped by status and format")
            .tag("status", status)
            .tag("format", exportFormat.orEmpty().ifBlank { "UNKNOWN" })
            .register(registry)
            .increment()
    }

    private fun recordScoring(registry: MeterRegistry, method: String, outcome: String, nanos: Long) {
        Counter.builder("psy.scoring.runs")
            .description("Scoring runs grouped by supported method and outcome")
            .tag("method", method)
            .tag("outcome", outcome)
            .register(registry)
            .increment()
        Timer.builder("psy.scoring.duration")
            .description("Scoring duration grouped by supported method and outcome")
            .tag("method", method)
            .tag("outcome", outcome)
            .register(registry)
            .record(nanos.coerceAtLeast(0), TimeUnit.NANOSECONDS)
    }

    private fun normalize(value: String?, allowed: Set<String>): String {
        val normalized = value?.trim()?.uppercase()?.replace('-', '_')
        return normalized?.takeIf(allowed::contains) ?: "UNKNOWN"
    }

    private fun safelyRecord(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // Observability must never change a business result or transaction outcome.
        }
    }

    private fun availableRegistry(): MeterRegistry? = try {
        meterRegistryProvider.getIfAvailable()
    } catch (_: Exception) {
        null
    }

    companion object {
        private val SUBMISSION_OUTCOMES = setOf("SUCCESS", "FAILURE")
        private val SUBMISSION_MODES = setOf("MANUAL", "AUTO")
        private val RISK_LEVELS = setOf(
            "NORMAL", "LOW", "ATTENTION", "MEDIUM", "HIGH", "CRITICAL", "P0", "P1", "P2", "P3"
        )
        private val SCORE_METHODS = setOf(
            "SIMPLE_SUM", "REVERSE_SUM", "WEIGHTED_SUM", "AVERAGE", "WEIGHTED_AVERAGE"
        )
        private val WARNING_ACTIONS = setOf(
            "CREATED", "IDEMPOTENT_REPLAY", "CLAIMED", "ASSIGNED", "ESCALATED", "REMINDED", "CLOSED"
        )
    }
}
