package org.sainm.psy.export.service

import org.sainm.psy.common.monitoring.PsyMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ExportJobWorker(
    private val exportJobStore: ExportJobStore,
    private val exportService: ExportService,
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.export.jobs.pending-batch-size:20}")
    private val pendingBatchSize: Int = 20
) {

    @Scheduled(fixedDelayString = "\${psy.export.jobs.pending-scan-delay-ms:60000}")
    fun processPendingJobs(): Int {
        val batchSize = pendingBatchSize.coerceIn(1, 200)
        val jobName = "export.job-dispatch"
        return psyMetrics?.recordSchedulerRun(jobName) {
            processPendingJobsUnlocked(batchSize)
        } ?: processPendingJobsUnlocked(batchSize)
    }

    private fun processPendingJobsUnlocked(batchSize: Int): Int {
        val jobs = exportJobStore.claimPendingJobs(batchSize)
        jobs.forEach(exportService::processClaimedExportJob)
        return jobs.size
    }
}
