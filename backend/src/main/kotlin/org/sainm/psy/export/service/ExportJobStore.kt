package org.sainm.psy.export.service

import org.sainm.psy.common.exception.BizException
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class ExportJobStore(
    private val jdbcTemplate: NamedParameterJdbcTemplate? = null,
    @Value("\${psy.export.jobs.max-in-memory-jobs:100}")
    private val maxInMemoryJobs: Int = 100,
    @Value("\${psy.export.jobs.max-in-memory-file-bytes:10485760}")
    private val maxInMemoryFileBytes: Int = 10 * 1024 * 1024
) {

    private val jobs = ConcurrentHashMap<String, ExportJob>()

    fun create(
        id: String,
        reportId: Long? = null,
        resultId: Long? = null,
        exportFormat: String? = null,
        localeTag: String? = null
    ): ExportJob {
        cleanupExpired()
        if (jdbcTemplate != null) {
            val job = ExportJob(
                id = id,
                status = ExportJobStatus.PENDING,
                reportId = reportId,
                resultId = resultId,
                exportFormat = exportFormat,
                localeTag = localeTag
            )
            jdbcTemplate.update(
                """
                insert into psy_export_job (
                    id, status, report_id, result_id, export_format, locale_tag, created_at, updated_at
                ) values (
                    :id, :status, :reportId, :resultId, :exportFormat, :localeTag, :createdAt, :updatedAt
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", job.id)
                    .addValue("status", job.status.name)
                    .addValue("reportId", job.reportId)
                    .addValue("resultId", job.resultId)
                    .addValue("exportFormat", job.exportFormat)
                    .addValue("localeTag", job.localeTag)
                    .addValue("createdAt", Timestamp.from(job.createdAt))
                    .addValue("updatedAt", Timestamp.from(job.createdAt))
            )
            return job
        }
        if (jobs.size >= maxInMemoryJobs) {
            throw BizException("EXPORT_JOB_LIMIT_EXCEEDED", "Too many export jobs are waiting in memory")
        }
        val job = ExportJob(
            id = id,
            status = ExportJobStatus.PENDING,
            reportId = reportId,
            resultId = resultId,
            exportFormat = exportFormat,
            localeTag = localeTag
        )
        jobs[id] = job
        return job
    }

    fun markProcessing(id: String) {
        if (jdbcTemplate != null) {
            jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    updated_at = :updatedAt
                where id = :id
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.PROCESSING.name,
                    "updatedAt" to Timestamp.from(Instant.now())
                )
            )
            return
        }
        jobs.computeIfPresent(id) { _, job -> job.copy(status = ExportJobStatus.PROCESSING) }
    }

    fun markDone(id: String, fileName: String, contentType: String, bytes: ByteArray) {
        if (bytes.size > maxInMemoryFileBytes) {
            markFailed(id, "Export file is too large to keep in memory")
            return
        }
        if (jdbcTemplate != null) {
            val now = Instant.now()
            jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    file_name = :fileName,
                    content_type = :contentType,
                    file_bytes = :fileBytes,
                    error_message = null,
                    completed_at = :completedAt,
                    updated_at = :updatedAt
                where id = :id
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("status", ExportJobStatus.DONE.name)
                    .addValue("fileName", fileName)
                    .addValue("contentType", contentType)
                    .addValue("fileBytes", bytes)
                    .addValue("completedAt", Timestamp.from(now))
                    .addValue("updatedAt", Timestamp.from(now))
            )
            return
        }
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
        if (jdbcTemplate != null) {
            val now = Instant.now()
            jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    error_message = :error,
                    completed_at = :completedAt,
                    updated_at = :updatedAt
                where id = :id
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.FAILED.name,
                    "error" to error,
                    "completedAt" to Timestamp.from(now),
                    "updatedAt" to Timestamp.from(now)
                )
            )
            return
        }
        jobs.computeIfPresent(id) { _, job ->
            job.copy(status = ExportJobStatus.FAILED, error = error, completedAt = Instant.now())
        }
    }

    fun find(id: String): ExportJob? {
        if (jdbcTemplate != null) {
            val sql = """
                select id, status, report_id, result_id, export_format, locale_tag,
                       file_name, content_type, file_bytes, error_message, created_at, completed_at
                from psy_export_job
                where id = :id
            """.trimIndent()
            return jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
                ExportJob(
                    id = rs.getString("id"),
                    status = ExportJobStatus.valueOf(rs.getString("status")),
                    reportId = rs.getObject("report_id", java.lang.Long::class.java)?.toLong(),
                    resultId = rs.getObject("result_id", java.lang.Long::class.java)?.toLong(),
                    exportFormat = rs.getString("export_format"),
                    localeTag = rs.getString("locale_tag"),
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    bytes = rs.getBytes("file_bytes"),
                    error = rs.getString("error_message"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant()
                )
            }.firstOrNull()
        }
        return jobs[id]
    }

    fun resetFailedForRetry(id: String): ExportJob? {
        if (jdbcTemplate != null) {
            val job = find(id) ?: return null
            if (job.status != ExportJobStatus.FAILED) {
                return job
            }
            jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    file_name = null,
                    content_type = null,
                    file_bytes = null,
                    error_message = null,
                    completed_at = null,
                    updated_at = :updatedAt
                where id = :id
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.PENDING.name,
                    "updatedAt" to Timestamp.from(Instant.now())
                )
            )
            return find(id)
        }
        return jobs.computeIfPresent(id) { _, job ->
            if (job.status != ExportJobStatus.FAILED) {
                job
            } else {
                job.copy(
                    status = ExportJobStatus.PENDING,
                    fileName = null,
                    contentType = null,
                    bytes = null,
                    error = null,
                    completedAt = null
                )
            }
        }
    }

    // Remove jobs older than 15 minutes every 5 minutes
    @Scheduled(fixedDelay = 300_000)
    fun cleanup() {
        cleanupExpired()
    }

    private fun cleanupExpired() {
        val cutoff = Instant.now().minusSeconds(900)
        if (jdbcTemplate != null) {
            jdbcTemplate.update(
                """
                delete from psy_export_job
                where created_at < :cutoff
                  and status in ('DONE', 'FAILED')
                """.trimIndent(),
                mapOf("cutoff" to Timestamp.from(cutoff))
            )
            return
        }
        jobs.entries.removeIf { (_, job) -> job.createdAt.isBefore(cutoff) }
    }
}
