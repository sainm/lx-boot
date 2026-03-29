package org.sainm.psy.export.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class ExportJobStore {

    private val jobs = ConcurrentHashMap<String, ExportJob>()

    fun create(id: String): ExportJob {
        val job = ExportJob(id = id, status = ExportJobStatus.PENDING)
        jobs[id] = job
        return job
    }

    fun markProcessing(id: String) {
        jobs.computeIfPresent(id) { _, job -> job.copy(status = ExportJobStatus.PROCESSING) }
    }

    fun markDone(id: String, fileName: String, contentType: String, bytes: ByteArray) {
        jobs.computeIfPresent(id) { _, job ->
            job.copy(
                status = ExportJobStatus.DONE,
                fileName = fileName,
                contentType = contentType,
                bytes = bytes,
                completedAt = Instant.now()
            )
        }
    }

    fun markFailed(id: String, error: String) {
        jobs.computeIfPresent(id) { _, job ->
            job.copy(status = ExportJobStatus.FAILED, error = error, completedAt = Instant.now())
        }
    }

    fun find(id: String): ExportJob? = jobs[id]

    // Remove jobs older than 15 minutes every 5 minutes
    @Scheduled(fixedDelay = 300_000)
    fun cleanup() {
        val cutoff = Instant.now().minusSeconds(900)
        jobs.entries.removeIf { (_, job) -> job.createdAt.isBefore(cutoff) }
    }
}
