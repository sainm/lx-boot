package org.sainm.psy.export.service

import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@Service
class ExportJobStore(
    private val jdbcTemplate: NamedParameterJdbcTemplate? = null,
    private val exportArtifactStorage: ExportArtifactStorage? = null,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.export.jobs.max-in-memory-jobs:100}")
    private val maxInMemoryJobs: Int = 100,
    @Value("\${psy.export.jobs.max-in-memory-file-bytes:10485760}")
    private val maxInMemoryFileBytes: Int = 10 * 1024 * 1024,
    @Value("\${psy.export.jobs.file-storage-enabled:true}")
    private val fileStorageEnabled: Boolean = true,
    @Value("\${psy.export.jobs.storage-dir:}")
    private val storageDir: String = "",
    @Value("\${psy.export.jobs.processing-timeout-minutes:30}")
    private val processingTimeoutMinutes: Long = 30
) {

    private val jobs = ConcurrentHashMap<String, ExportJob>()
    private val fallbackArtifactStorage: ExportArtifactStorage by lazy { LocalPathExportArtifactStorage(storageDir) }

    fun create(
        id: String,
        reportId: Long? = null,
        resultId: Long? = null,
        exportFormat: String? = null,
        localeTag: String? = null,
        desensitized: Boolean = true,
        createdBy: Long? = null,
        tenantId: Long? = null
    ): ExportJob {
        cleanupExpired()
        if (jdbcTemplate != null) {
            val job = ExportJob(
                id = id,
                status = ExportJobStatus.PENDING,
                reportId = reportId,
                resultId = resultId,
                exportFormat = exportFormat,
                localeTag = localeTag,
                desensitized = desensitized,
                createdBy = createdBy,
                tenantId = tenantId
            )
            jdbcTemplate.update(
                """
                insert into psy_export_job (
                    id, tenant_id, created_by, status, report_id, result_id, export_format, locale_tag, desensitized_flag, created_at, updated_at
                ) values (
                    :id, :tenantId,
                    :createdBy, :status, :reportId, :resultId, :exportFormat, :localeTag, :desensitized, :createdAt, :updatedAt
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", job.id)
                    .addValue("tenantId", tenantId)
                    .addValue("createdBy", createdBy)
                    .addValue("status", job.status.name)
                    .addValue("reportId", job.reportId)
                    .addValue("resultId", job.resultId)
                    .addValue("exportFormat", job.exportFormat)
                    .addValue("localeTag", job.localeTag)
                    .addValue("desensitized", job.desensitized)
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
            localeTag = localeTag,
            desensitized = desensitized,
            createdBy = createdBy,
            tenantId = tenantId
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

    fun claimPending(id: String, now: Instant = Instant.now()): ExportJob? {
        if (jdbcTemplate != null) {
            val updated = jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    updated_at = :updatedAt
                where id = :id
                  and status = :pendingStatus
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.PROCESSING.name,
                    "pendingStatus" to ExportJobStatus.PENDING.name,
                    "updatedAt" to Timestamp.from(now)
                )
            )
            return if (updated > 0) find(id) else null
        }
        var claimed: ExportJob? = null
        jobs.computeIfPresent(id) { _, job ->
            if (job.status == ExportJobStatus.PENDING) {
                job.copy(status = ExportJobStatus.PROCESSING).also { claimed = it }
            } else {
                job
            }
        }
        return claimed
    }

    fun claimPendingJobs(limit: Int, now: Instant = Instant.now()): List<ExportJob> {
        val normalizedLimit = min(limit.coerceAtLeast(1), 200)
        if (jdbcTemplate != null) {
            val candidateIds = jdbcTemplate.query(
                """
                select id
                from psy_export_job
                where status = :status
                order by created_at asc, id asc
                limit :limit
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("status", ExportJobStatus.PENDING.name)
                    .addValue("limit", normalizedLimit)
            ) { rs, _ -> rs.getString("id") }
            return candidateIds.mapNotNull { claimPending(it, now) }
        }
        return jobs.values
            .asSequence()
            .filter { it.status == ExportJobStatus.PENDING }
            .sortedBy { it.createdAt }
            .take(normalizedLimit)
            .mapNotNull { claimPending(it.id, now) }
            .toList()
    }

    fun markDone(id: String, fileName: String, contentType: String, bytes: ByteArray) {
        val jdbc = jdbcTemplate
        if (jdbc != null) {
            val now = Instant.now()
            val exportFormat = findExportFormat(id)
            val storage = artifactStorageOrNull()
            val storedPath = storage?.store(id, fileName, bytes)
            val updated = runCatching {
                jdbc.update(
                    """
                    update psy_export_job
                    set status = :status,
                        file_name = :fileName,
                        content_type = :contentType,
                        file_path = :filePath,
                        file_size = :fileSize,
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
                        .addValue("filePath", storedPath)
                        .addValue("fileSize", bytes.size.toLong())
                        .addValue("fileBytes", if (storedPath == null) bytes else null)
                        .addValue("completedAt", Timestamp.from(now))
                        .addValue("updatedAt", Timestamp.from(now))
                )
            }.onFailure {
                storage?.delete(storedPath)
            }.getOrThrow()
            if (updated == 0) {
                storage?.delete(storedPath)
            } else {
                psyMetrics?.recordExportJobDone(exportFormat = exportFormat, fileBytes = bytes.size)
            }
            return
        }
        if (bytes.size > maxInMemoryFileBytes) {
            markFailed(id, "Export file is too large to keep in memory")
            return
        }
        jobs.computeIfPresent(id) { _, job ->
            psyMetrics?.recordExportJobDone(exportFormat = job.exportFormat, fileBytes = bytes.size)
            job.copy(
                status = ExportJobStatus.DONE,
                fileName = fileName,
                contentType = contentType,
                fileSize = bytes.size.toLong(),
                bytes = bytes,
                completedAt = Instant.now()
            )
        } ?: run {
            return
        }
    }

    fun markFailed(id: String, error: String) {
        if (jdbcTemplate != null) {
            val now = Instant.now()
            val exportFormat = findExportFormat(id)
            val updated = jdbcTemplate.update(
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
            if (updated > 0) {
                psyMetrics?.recordExportJobFailed(exportFormat = exportFormat)
            }
            return
        }
        jobs.computeIfPresent(id) { _, job ->
            psyMetrics?.recordExportJobFailed(exportFormat = job.exportFormat)
            job.copy(status = ExportJobStatus.FAILED, error = error, completedAt = Instant.now())
        }
    }

    fun find(id: String): ExportJob? {
        if (jdbcTemplate != null) {
            val sql = """
                select id, status, report_id, result_id, export_format, locale_tag, created_by, tenant_id,
                       desensitized_flag, file_name, content_type, file_path, file_size, file_bytes,
                       error_message, created_at, completed_at
                from psy_export_job
                where id = :id
            """.trimIndent()
            return jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
                val filePath = rs.getString("file_path")
                val dbBytes = rs.getBytes("file_bytes")
                val bytes = artifactStorageOrNull()?.read(filePath) ?: dbBytes
                ExportJob(
                    id = rs.getString("id"),
                    status = ExportJobStatus.valueOf(rs.getString("status")),
                    reportId = rs.getObject("report_id", java.lang.Long::class.java)?.toLong(),
                    resultId = rs.getObject("result_id", java.lang.Long::class.java)?.toLong(),
                    exportFormat = rs.getString("export_format"),
                    localeTag = rs.getString("locale_tag"),
                    desensitized = rs.getBoolean("desensitized_flag"),
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    filePath = filePath,
                    fileSize = rs.getObject("file_size", java.lang.Long::class.java)?.toLong(),
                    bytes = bytes,
                    error = rs.getString("error_message"),
                    createdBy = rs.getObject("created_by", java.lang.Long::class.java)?.toLong(),
                    tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong(),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant()
                )
            }.firstOrNull()
        }
        return jobs[id]
    }

    fun listRecent(limit: Int, status: ExportJobStatus? = null, tenantId: Long? = null): List<ExportJob> {
        val normalizedLimit = min(limit.coerceAtLeast(1), 100)
        if (jdbcTemplate != null) {
            val sql = buildString {
                append(
                    """
                    select id, status, report_id, result_id, export_format, locale_tag, created_by, tenant_id,
                           desensitized_flag, file_name, content_type, file_path, file_size, file_bytes,
                           error_message, created_at, completed_at
                    from psy_export_job
                    """.trimIndent()
                )
                val predicates = listOfNotNull(
                    status?.let { "status = :status" },
                    tenantId?.let { "tenant_id = :tenantId" }
                )
                if (predicates.isNotEmpty()) append("\nwhere ${predicates.joinToString(" and ")}")
                append("\norder by created_at desc, id desc")
                append("\nlimit :limit")
            }
            val params = MapSqlParameterSource()
                .addValue("limit", normalizedLimit)
            if (status != null) {
                params.addValue("status", status.name)
            }
            if (tenantId != null) params.addValue("tenantId", tenantId)
            return jdbcTemplate.query(sql, params) { rs, _ ->
                val filePath = rs.getString("file_path")
                val dbBytes = rs.getBytes("file_bytes")
                val bytes = artifactStorageOrNull()?.read(filePath) ?: dbBytes
                ExportJob(
                    id = rs.getString("id"),
                    status = ExportJobStatus.valueOf(rs.getString("status")),
                    reportId = rs.getObject("report_id", java.lang.Long::class.java)?.toLong(),
                    resultId = rs.getObject("result_id", java.lang.Long::class.java)?.toLong(),
                    exportFormat = rs.getString("export_format"),
                    localeTag = rs.getString("locale_tag"),
                    desensitized = rs.getBoolean("desensitized_flag"),
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    filePath = filePath,
                    fileSize = rs.getObject("file_size", java.lang.Long::class.java)?.toLong(),
                    bytes = bytes,
                    error = rs.getString("error_message"),
                    createdBy = rs.getObject("created_by", java.lang.Long::class.java)?.toLong(),
                    tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong(),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant()
                )
            }
        }
        return jobs.values
            .asSequence()
            .filter { status == null || it.status == status }
            .filter { tenantId == null || it.tenantId == tenantId }
            .sortedWith(compareByDescending<ExportJob> { it.createdAt }.thenByDescending { it.id })
            .take(normalizedLimit)
            .toList()
    }

    fun resetFailedForRetry(id: String): ExportJob? {
        if (jdbcTemplate != null) {
            val job = find(id) ?: return null
            if (job.status != ExportJobStatus.FAILED) {
                return job
            }
            artifactStorageOrNull()?.delete(job.filePath)
            jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    file_name = null,
                    content_type = null,
                    file_path = null,
                    file_size = null,
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
                    filePath = null,
                    fileSize = null,
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
        val lock = schedulerLockService ?: return cleanupUnlocked()
        val jobName = "export.job-cleanup"
        val result = lock.withLock("export:job-cleanup", Duration.ofMinutes(5)) {
            psyMetrics?.recordSchedulerRun(jobName) { cleanupUnlocked() } ?: cleanupUnlocked()
        }
        if (result == null) {
            psyMetrics?.recordSchedulerSkipped(jobName)
        }
    }

    private fun cleanupUnlocked() {
        cleanupExpired()
        recoverStaleProcessingJobs()
    }

    private fun cleanupExpired() {
        val cutoff = Instant.now().minusSeconds(900)
        if (jdbcTemplate != null) {
            val storage = artifactStorageOrNull()
            findCompletedJobsBefore(cutoff).forEach { storage?.delete(it.filePath) }
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

    fun recoverStaleProcessingJobs(now: Instant = Instant.now()): Int {
        if (jdbcTemplate == null) {
            return 0
        }
        val cutoff = now.minus(processingTimeoutMinutes.coerceAtLeast(1), ChronoUnit.MINUTES)
        return jdbcTemplate.update(
            """
            update psy_export_job
            set status = :failedStatus,
                error_message = :errorMessage,
                completed_at = :completedAt,
                updated_at = :updatedAt
            where status = :processingStatus
              and updated_at < :cutoff
            """.trimIndent(),
            mapOf(
                "failedStatus" to ExportJobStatus.FAILED.name,
                "processingStatus" to ExportJobStatus.PROCESSING.name,
                "errorMessage" to "Export job timed out while processing; reset it for retry.",
                "completedAt" to Timestamp.from(now),
                "updatedAt" to Timestamp.from(now),
                "cutoff" to Timestamp.from(cutoff)
            )
        )
    }

    private fun findCompletedJobsBefore(cutoff: Instant): List<ExportJob> {
        if (jdbcTemplate == null) {
            return emptyList()
        }
        return jdbcTemplate.query(
            """
            select id, status, file_path, created_at
            from psy_export_job
            where created_at < :cutoff
              and status in ('DONE', 'FAILED')
            """.trimIndent(),
            mapOf("cutoff" to Timestamp.from(cutoff))
        ) { rs, _ ->
            ExportJob(
                id = rs.getString("id"),
                status = ExportJobStatus.valueOf(rs.getString("status")),
                filePath = rs.getString("file_path"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }
    }

    private fun findExportFormat(id: String): String? {
        if (jdbcTemplate == null) {
            return jobs[id]?.exportFormat
        }
        return jdbcTemplate.query(
            "select export_format from psy_export_job where id = :id",
            mapOf("id" to id)
        ) { rs, _ -> rs.getString("export_format") }.firstOrNull()
    }

    private fun artifactStorageOrNull(): ExportArtifactStorage? =
        if (!fileStorageEnabled) {
            null
        } else {
            exportArtifactStorage ?: fallbackArtifactStorage
        }
}
