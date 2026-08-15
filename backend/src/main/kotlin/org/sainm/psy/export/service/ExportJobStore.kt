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
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
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
    private val processingTimeoutMinutes: Long = 30,
    @Value("\${psy.export.jobs.processing-timeout-seconds:0}")
    private val processingTimeoutSeconds: Long = 0,
    @Value("\${psy.export.jobs.max-attempts:3}")
    private val maxAttempts: Int = 3,
    @Value("\${psy.export.jobs.initial-retry-delay-seconds:30}")
    private val initialRetryDelaySeconds: Long = 30,
    @Value("\${psy.export.jobs.max-retry-delay-seconds:900}")
    private val maxRetryDelaySeconds: Long = 900,
    @Value("\${psy.export.jobs.cleanup-enabled:true}")
    private val cleanupEnabled: Boolean = true
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
        claimPending(id)
    }

    fun claimPending(id: String, now: Instant = Instant.now()): ExportJob? {
        val processingToken = UUID.randomUUID().toString()
        if (jdbcTemplate != null) {
            val updated = jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    processing_started_at = :updatedAt,
                    processing_token = :processingToken,
                    next_retry_at = null,
                    updated_at = :updatedAt
                where id = :id
                  and status = :pendingStatus
                  and (next_retry_at is null or next_retry_at <= :updatedAt)
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.PROCESSING.name,
                    "pendingStatus" to ExportJobStatus.PENDING.name,
                    "processingToken" to processingToken,
                    "updatedAt" to Timestamp.from(now)
                )
            )
            return if (updated > 0) find(id) else null
        }
        var claimed: ExportJob? = null
        jobs.computeIfPresent(id) { _, job ->
            if (job.status == ExportJobStatus.PENDING && (job.nextRetryAt == null || !job.nextRetryAt.isAfter(now))) {
                job.copy(
                    status = ExportJobStatus.PROCESSING,
                    processingStartedAt = now,
                    processingToken = processingToken,
                    nextRetryAt = null
                ).also { claimed = it }
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
                  and (next_retry_at is null or next_retry_at <= :now)
                order by created_at asc, id asc
                limit :limit
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("status", ExportJobStatus.PENDING.name)
                    .addValue("now", Timestamp.from(now))
                    .addValue("limit", normalizedLimit)
            ) { rs, _ -> rs.getString("id") }
            return candidateIds.mapNotNull { claimPending(it, now) }
        }
        return jobs.values
            .asSequence()
            .filter { it.status == ExportJobStatus.PENDING }
            .filter { it.nextRetryAt == null || !it.nextRetryAt.isAfter(now) }
            .sortedBy { it.createdAt }
            .take(normalizedLimit)
            .mapNotNull { claimPending(it.id, now) }
            .toList()
    }

    fun markDone(
        id: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        processingToken: String? = null
    ) {
        val jdbc = jdbcTemplate
        if (jdbc != null) {
            val now = Instant.now()
            val exportFormat = findExportFormat(id)
            val storage = artifactStorageOrNull()
            // A lease-specific object key prevents an expired worker from
            // overwriting or deleting the artifact produced by a newer lease.
            val storageJobId = processingToken?.let { "$id-$it" } ?: id
            val storedPath = storage?.store(storageJobId, fileName, bytes)
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
                        processing_started_at = null,
                        processing_token = null,
                        next_retry_at = null,
                        dead_letter_at = null,
                        completed_at = :completedAt,
                        updated_at = :updatedAt
                    where id = :id
                      and status = :processingStatus
                      and (:processingToken is null or processing_token = :processingToken)
                    """.trimIndent(),
                    MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", ExportJobStatus.DONE.name)
                        .addValue("fileName", fileName)
                        .addValue("contentType", contentType)
                        .addValue("filePath", storedPath)
                        .addValue("fileSize", bytes.size.toLong())
                        .addValue("fileBytes", if (storedPath == null) bytes else null)
                        .addValue("processingToken", processingToken)
                        .addValue("processingStatus", ExportJobStatus.PROCESSING.name)
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
            if (job.status != ExportJobStatus.PROCESSING ||
                (processingToken != null && job.processingToken != processingToken)
            ) {
                return@computeIfPresent job
            }
            psyMetrics?.recordExportJobDone(exportFormat = job.exportFormat, fileBytes = bytes.size)
            job.copy(
                status = ExportJobStatus.DONE,
                fileName = fileName,
                contentType = contentType,
                fileSize = bytes.size.toLong(),
                bytes = bytes,
                processingStartedAt = null,
                processingToken = null,
                nextRetryAt = null,
                deadLetterAt = null,
                completedAt = Instant.now()
            )
        } ?: run {
            return
        }
    }

    fun markFailed(id: String, error: String) {
        val safeError = sanitizeError(error)
        if (jdbcTemplate != null) {
            val now = Instant.now()
            val exportFormat = findExportFormat(id)
            val updated = jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    error_message = :error,
                    processing_started_at = null,
                    processing_token = null,
                    next_retry_at = null,
                    completed_at = :completedAt,
                    updated_at = :updatedAt
                where id = :id
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.FAILED.name,
                    "error" to safeError,
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
            job.copy(
                status = ExportJobStatus.FAILED,
                error = safeError,
                processingStartedAt = null,
                processingToken = null,
                nextRetryAt = null,
                completedAt = Instant.now()
            )
        }
    }

    fun markAttemptFailed(
        id: String,
        processingToken: String,
        error: String,
        now: Instant = Instant.now()
    ): ExportJobStatus? {
        val safeError = sanitizeError(error)
        val jdbc = jdbcTemplate
        if (jdbc != null) {
            val attempt = jdbc.query(
                """
                select retry_count, export_format
                from psy_export_job
                where id = :id
                  and status = :processingStatus
                  and processing_token = :processingToken
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "processingStatus" to ExportJobStatus.PROCESSING.name,
                    "processingToken" to processingToken
                )
            ) { rs, _ -> rs.getInt("retry_count") to rs.getString("export_format") }.firstOrNull() ?: return null
            val nextRetryCount = attempt.first + 1
            val deadLetter = nextRetryCount >= maxAttempts.coerceAtLeast(1)
            val targetStatus = if (deadLetter) ExportJobStatus.DEAD_LETTER else ExportJobStatus.PENDING
            val nextRetryAt = if (deadLetter) null else now.plusSeconds(retryDelaySeconds(attempt.first))
            val updated = jdbc.update(
                """
                update psy_export_job
                set status = :status,
                    retry_count = :retryCount,
                    next_retry_at = :nextRetryAt,
                    processing_started_at = null,
                    processing_token = null,
                    dead_letter_at = :deadLetterAt,
                    error_message = :error,
                    completed_at = :completedAt,
                    updated_at = :updatedAt
                where id = :id
                  and status = :processingStatus
                  and processing_token = :processingToken
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("status", targetStatus.name)
                    .addValue("retryCount", nextRetryCount)
                    .addValue("nextRetryAt", nextRetryAt?.let(Timestamp::from))
                    .addValue("deadLetterAt", now.takeIf { deadLetter }?.let(Timestamp::from))
                    .addValue("error", safeError)
                    .addValue("completedAt", now.takeIf { deadLetter }?.let(Timestamp::from))
                    .addValue("updatedAt", Timestamp.from(now))
                    .addValue("processingStatus", ExportJobStatus.PROCESSING.name)
                    .addValue("processingToken", processingToken)
            )
            if (updated > 0 && deadLetter) {
                psyMetrics?.recordExportJobFailed(exportFormat = attempt.second)
            }
            return targetStatus.takeIf { updated > 0 }
        }

        var result: ExportJobStatus? = null
        jobs.computeIfPresent(id) { _, job ->
            if (job.status != ExportJobStatus.PROCESSING || job.processingToken != processingToken) {
                return@computeIfPresent job
            }
            val nextRetryCount = job.retryCount + 1
            val deadLetter = nextRetryCount >= maxAttempts.coerceAtLeast(1)
            val targetStatus = if (deadLetter) ExportJobStatus.DEAD_LETTER else ExportJobStatus.PENDING
            result = targetStatus
            if (deadLetter) psyMetrics?.recordExportJobFailed(exportFormat = job.exportFormat)
            job.copy(
                status = targetStatus,
                retryCount = nextRetryCount,
                nextRetryAt = if (deadLetter) null else now.plusSeconds(retryDelaySeconds(job.retryCount)),
                processingStartedAt = null,
                processingToken = null,
                deadLetterAt = now.takeIf { deadLetter },
                error = safeError,
                completedAt = now.takeIf { deadLetter }
            )
        }
        return result
    }

    fun find(id: String, includeBytes: Boolean = true): ExportJob? {
        if (jdbcTemplate != null) {
            val fileBytesSelection = if (includeBytes) "file_bytes" else "null as file_bytes"
            val sql = """
                select id, status, report_id, result_id, export_format, locale_tag, created_by, tenant_id,
                       desensitized_flag, file_name, content_type, file_path, file_size, $fileBytesSelection,
                       error_message, retry_count, next_retry_at, processing_started_at, processing_token,
                       dead_letter_at, created_at, completed_at
                from psy_export_job
                where id = :id
            """.trimIndent()
            return jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
                val filePath = rs.getString("file_path")
                val dbBytes = rs.getBytes("file_bytes")
                val bytes = if (includeBytes) artifactStorageOrNull()?.read(filePath) ?: dbBytes else null
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
                    retryCount = rs.getInt("retry_count"),
                    nextRetryAt = rs.getTimestamp("next_retry_at")?.toInstant(),
                    processingStartedAt = rs.getTimestamp("processing_started_at")?.toInstant(),
                    processingToken = rs.getString("processing_token"),
                    deadLetterAt = rs.getTimestamp("dead_letter_at")?.toInstant(),
                    createdBy = rs.getObject("created_by", java.lang.Long::class.java)?.toLong(),
                    tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong(),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant()
                )
            }.firstOrNull()
        }
        return jobs[id]?.let { if (includeBytes) it else it.copy(bytes = null) }
    }

    fun listRecent(limit: Int, status: ExportJobStatus? = null, tenantId: Long? = null): List<ExportJob> {
        val normalizedLimit = min(limit.coerceAtLeast(1), 100)
        if (jdbcTemplate != null) {
            val sql = buildString {
                append(
                    """
                    select id, status, report_id, result_id, export_format, locale_tag, created_by, tenant_id,
                           desensitized_flag, file_name, content_type, file_path, file_size, null as file_bytes,
                           error_message, retry_count, next_retry_at, processing_started_at, processing_token,
                           dead_letter_at, created_at, completed_at
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
                    bytes = null,
                    error = rs.getString("error_message"),
                    retryCount = rs.getInt("retry_count"),
                    nextRetryAt = rs.getTimestamp("next_retry_at")?.toInstant(),
                    processingStartedAt = rs.getTimestamp("processing_started_at")?.toInstant(),
                    processingToken = rs.getString("processing_token"),
                    deadLetterAt = rs.getTimestamp("dead_letter_at")?.toInstant(),
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
            .map { it.copy(bytes = null) }
            .toList()
    }

    fun resetFailedForRetry(id: String, tenantId: Long? = null): ExportJob? {
        if (jdbcTemplate != null) {
            val job = find(id, includeBytes = false) ?: return null
            if (tenantId != null && job.tenantId != tenantId) {
                return null
            }
            val updated = jdbcTemplate.update(
                """
                update psy_export_job
                set status = :status,
                    file_name = null,
                    content_type = null,
                    file_path = null,
                    file_size = null,
                    file_bytes = null,
                    error_message = null,
                    retry_count = 0,
                    next_retry_at = null,
                    processing_started_at = null,
                    processing_token = null,
                    dead_letter_at = null,
                    completed_at = null,
                    updated_at = :updatedAt
                where id = :id
                  and status in (:failedStatus, :deadLetterStatus)
                  ${if (tenantId == null) "" else "and tenant_id = :tenantId"}
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "status" to ExportJobStatus.PENDING.name,
                    "failedStatus" to ExportJobStatus.FAILED.name,
                    "deadLetterStatus" to ExportJobStatus.DEAD_LETTER.name,
                    "tenantId" to tenantId,
                    "updatedAt" to Timestamp.from(Instant.now())
                )
            )
            return if (updated > 0) find(id, includeBytes = false) else null
        }
        var replayed: ExportJob? = null
        jobs.computeIfPresent(id) { _, job ->
            if ((tenantId == null || job.tenantId == tenantId) &&
                job.status in setOf(ExportJobStatus.FAILED, ExportJobStatus.DEAD_LETTER)
            ) {
                job.copy(
                    status = ExportJobStatus.PENDING,
                    fileName = null,
                    contentType = null,
                    filePath = null,
                    fileSize = null,
                    bytes = null,
                    error = null,
                    retryCount = 0,
                    nextRetryAt = null,
                    processingStartedAt = null,
                    processingToken = null,
                    deadLetterAt = null,
                    completedAt = null
                ).also { replayed = it }
            } else {
                job
            }
        }
        return replayed
    }

    fun deleteArtifact(location: String?) {
        if (location == null) return
        runCatching { artifactStorageOrNull()?.delete(location) }
            .onFailure { logger.warn("Failed to delete superseded export artifact") }
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
        if (!cleanupEnabled) return
        val cutoff = Instant.now().minusSeconds(900)
        if (jdbcTemplate != null) {
            val storage = artifactStorageOrNull()
            findCompletedJobsBefore(cutoff).forEach { storage?.delete(it.filePath) }
            jdbcTemplate.update(
                """
                delete from psy_export_job
                where coalesce(completed_at, updated_at, created_at) < :cutoff
                  and status in ('DONE', 'FAILED', 'DEAD_LETTER')
                """.trimIndent(),
                mapOf("cutoff" to Timestamp.from(cutoff))
            )
            return
        }
        jobs.entries.removeIf { (_, job) ->
            (job.completedAt ?: job.createdAt).isBefore(cutoff) && job.status in setOf(
                ExportJobStatus.DONE,
                ExportJobStatus.FAILED,
                ExportJobStatus.DEAD_LETTER
            )
        }
    }

    fun recoverStaleProcessingJobs(now: Instant = Instant.now()): Int {
        val jdbc = jdbcTemplate
        if (jdbc == null) {
            return 0
        }
        val cutoff = now.minus(processingTimeout())
        val staleJobs = jdbc.query(
            """
            select id, retry_count, export_format
            from psy_export_job
            where status = :processingStatus
              and coalesce(processing_started_at, updated_at, created_at) < :cutoff
            order by coalesce(processing_started_at, updated_at, created_at), id
            """.trimIndent(),
            mapOf(
                "processingStatus" to ExportJobStatus.PROCESSING.name,
                "cutoff" to Timestamp.from(cutoff)
            )
        ) { rs, _ ->
            Triple(rs.getString("id"), rs.getInt("retry_count"), rs.getString("export_format"))
        }
        return staleJobs.sumOf { (id, previousRetryCount, exportFormat) ->
            val nextRetryCount = previousRetryCount + 1
            val deadLetter = nextRetryCount >= maxAttempts.coerceAtLeast(1)
            val targetStatus = if (deadLetter) ExportJobStatus.DEAD_LETTER else ExportJobStatus.PENDING
            val nextRetryAt = if (deadLetter) null else now.plusSeconds(retryDelaySeconds(previousRetryCount))
            val updated = jdbc.update(
                """
                update psy_export_job
                set status = :status,
                    retry_count = :retryCount,
                    next_retry_at = :nextRetryAt,
                    processing_started_at = null,
                    processing_token = null,
                    dead_letter_at = :deadLetterAt,
                    error_message = :errorMessage,
                    completed_at = :completedAt,
                    updated_at = :updatedAt
                where id = :id
                  and status = :processingStatus
                  and coalesce(processing_started_at, updated_at, created_at) < :cutoff
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("status", targetStatus.name)
                    .addValue("retryCount", nextRetryCount)
                    .addValue("nextRetryAt", nextRetryAt?.let(Timestamp::from))
                    .addValue("deadLetterAt", now.takeIf { deadLetter }?.let(Timestamp::from))
                    .addValue("errorMessage", "PROCESSING_TIMEOUT")
                    .addValue("completedAt", now.takeIf { deadLetter }?.let(Timestamp::from))
                    .addValue("updatedAt", Timestamp.from(now))
                    .addValue("processingStatus", ExportJobStatus.PROCESSING.name)
                    .addValue("cutoff", Timestamp.from(cutoff))
            )
            if (updated > 0 && deadLetter) {
                psyMetrics?.recordExportJobFailed(exportFormat = exportFormat)
            }
            updated
        }
    }

    private fun findCompletedJobsBefore(cutoff: Instant): List<ExportJob> {
        if (jdbcTemplate == null) {
            return emptyList()
        }
        return jdbcTemplate.query(
            """
            select id, status, file_path, created_at, completed_at
            from psy_export_job
            where coalesce(completed_at, updated_at, created_at) < :cutoff
              and status in ('DONE', 'FAILED', 'DEAD_LETTER')
            """.trimIndent(),
            mapOf("cutoff" to Timestamp.from(cutoff))
        ) { rs, _ ->
            ExportJob(
                id = rs.getString("id"),
                status = ExportJobStatus.valueOf(rs.getString("status")),
                filePath = rs.getString("file_path"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                completedAt = rs.getTimestamp("completed_at")?.toInstant()
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

    private fun processingTimeout(): Duration =
        if (processingTimeoutSeconds > 0) {
            Duration.ofSeconds(processingTimeoutSeconds.coerceAtLeast(1))
        } else {
            Duration.ofMinutes(processingTimeoutMinutes.coerceAtLeast(1))
        }

    private fun retryDelaySeconds(previousRetryCount: Int): Long {
        val multiplier = 1L shl previousRetryCount.coerceIn(0, 20)
        return min(
            maxRetryDelaySeconds.coerceAtLeast(1),
            initialRetryDelaySeconds.coerceAtLeast(1) * multiplier
        )
    }

    private fun sanitizeError(error: String): String = error
        .replace(
            Regex("(?i)(bearer|token|password|secret|credential)\\s*[:=]?\\s*[^\\s,;]+"),
            "\$1=[REDACTED]"
        )
        .take(500)
        .ifBlank { "EXPORT_JOB_FAILED" }

    private fun artifactStorageOrNull(): ExportArtifactStorage? =
        if (!fileStorageEnabled) {
            null
        } else {
            exportArtifactStorage ?: fallbackArtifactStorage
        }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(ExportJobStore::class.java)
    }
}
