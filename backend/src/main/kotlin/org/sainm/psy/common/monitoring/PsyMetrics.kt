package org.sainm.psy.common.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class PsyMetrics(
    private val meterRegistryProvider: ObjectProvider<MeterRegistry>
) {

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
