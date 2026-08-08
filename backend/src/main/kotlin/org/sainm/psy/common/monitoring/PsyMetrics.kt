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

    fun <T> recordSchedulerRun(jobName: String, block: () -> T): T {
        val registry = meterRegistryProvider.getIfAvailable() ?: return block()
        val start = System.nanoTime()
        return try {
            val result = block()
            recordScheduler(registry, jobName, "success", System.nanoTime() - start)
            result
        } catch (e: Exception) {
            recordScheduler(registry, jobName, "failure", System.nanoTime() - start)
            throw e
        }
    }

    fun recordSchedulerSkipped(jobName: String) {
        meterRegistryProvider.getIfAvailable()?.let { registry ->
            Counter.builder("psy.scheduler.runs")
                .description("Scheduler executions grouped by job and outcome")
                .tag("job", jobName)
                .tag("outcome", "skipped")
                .register(registry)
                .increment()
        }
    }

    fun recordExportJobDone(exportFormat: String?, fileBytes: Int) {
        meterRegistryProvider.getIfAvailable()?.let { registry ->
            recordExportJob(registry, "done", exportFormat)
            DistributionSummary.builder("psy.export.job.file.bytes")
                .description("Exported report file size in bytes")
                .tag("format", exportFormat.orEmpty().ifBlank { "UNKNOWN" })
                .register(registry)
                .record(fileBytes.toDouble())
        }
    }

    fun recordExportJobFailed(exportFormat: String?) {
        meterRegistryProvider.getIfAvailable()?.let { registry ->
            recordExportJob(registry, "failed", exportFormat)
        }
    }

    fun recordNotificationDeliveryAttempt(outcome: String) {
        meterRegistryProvider.getIfAvailable()?.let { registry ->
            Counter.builder("psy.notification.delivery.attempts")
                .description("Push delivery attempts grouped by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment()
        }
    }

    fun recordRecoveredNotificationDeliveries(count: Int) {
        if (count <= 0) return
        meterRegistryProvider.getIfAvailable()?.let { registry ->
            Counter.builder("psy.notification.delivery.recovered")
                .description("Stale push deliveries recovered after a processing timeout")
                .register(registry)
                .increment(count.toDouble())
        }
    }

    fun recordNotificationQueueState(pending: Long, processing: Long, failed: Long, oldestPendingSeconds: Long) {
        val registry = meterRegistryProvider.getIfAvailable() ?: return
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
}
