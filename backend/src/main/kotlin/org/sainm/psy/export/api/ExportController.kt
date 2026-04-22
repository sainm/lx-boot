package org.sainm.psy.export.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.export.service.ExportArtifactStorageProperties
import org.sainm.psy.export.service.ExportJobStatus
import org.sainm.psy.export.service.ExportJobStore
import org.sainm.psy.export.service.ExportService
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/exports")
class ExportController(
    private val exportService: ExportService,
    private val exportJobStore: ExportJobStore,
    private val exportArtifactStorageProperties: ExportArtifactStorageProperties,
    @Value("\${psy.export.jobs.file-storage-enabled:true}")
    private val fileStorageEnabled: Boolean = true,
    @Value("\${psy.export.jobs.pending-scan-delay-ms:60000}")
    private val pendingScanDelayMs: Long = 60000,
    @Value("\${psy.export.jobs.pending-batch-size:20}")
    private val pendingBatchSize: Int = 20
) {

    @PostMapping("/reports")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun exportReport(@Valid @RequestBody request: ExportReportRequest): ApiResponse<ExportReportResponse> =
        ApiResponse.ok(exportService.exportReport(request))

    @GetMapping("/reports/download")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun downloadReport(
        @RequestParam(required = false) reportId: Long?,
        @RequestParam(required = false) resultId: Long?,
        @RequestParam(defaultValue = "TEXT") exportFormat: String,
        @RequestParam(defaultValue = "true") desensitized: Boolean
    ): ResponseEntity<ByteArrayResource> {
        val download = exportService.exportReportFile(
            ExportReportRequest(
                reportId = reportId,
                resultId = resultId,
                exportFormat = exportFormat,
                desensitized = desensitized
            )
        )
        val resource = ByteArrayResource(download.bytes)
        val contentDisposition = ContentDisposition.attachment().filename(download.fileName).build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .header("X-Export-Id", download.exportId)
            .header("X-Export-Format", download.exportFormat)
            .header("X-Download-Extension", download.downloadExtension)
            .header("X-Generated-At", download.generatedAt)
            .header("X-Report-Id", download.reportId.toString())
            .header("X-Result-Id", download.resultId.toString())
            .header("X-Desensitized", download.desensitized.toString())
            .contentType(MediaType.parseMediaType(download.contentType))
            .contentLength(download.bytes.size.toLong())
            .body(resource)
    }

    // ── Async job endpoints ─────────────────────────────────────────────────

    @PostMapping("/reports/jobs")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun submitExportJob(@Valid @RequestBody request: ExportReportRequest): ApiResponse<ExportJobSubmitResponse> {
        val jobId = UUID.randomUUID().toString()
        val localeTag = LocaleContextHolder.getLocale().toLanguageTag()
        exportJobStore.create(
            id = jobId,
            reportId = request.reportId,
            resultId = request.resultId,
            exportFormat = request.exportFormat,
            localeTag = localeTag,
            desensitized = request.desensitized
        )
        exportService.processExportJob(jobId, request, localeTag)
        return ApiResponse.ok(ExportJobSubmitResponse(jobId = jobId, status = ExportJobStatus.PENDING.name))
    }

    @GetMapping("/reports/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun getExportJobStatus(@PathVariable jobId: String): ApiResponse<ExportJobStatusResponse> {
        val job = exportJobStore.find(jobId)
            ?: throw BizException("JOB_NOT_FOUND", "Export job not found: $jobId")
        return ApiResponse.ok(job.toStatusResponse())
    }

    @GetMapping("/reports/storage")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun getExportArtifactStorageInfo(): ApiResponse<ExportArtifactStorageInfoResponse> =
        ApiResponse.ok(
            ExportArtifactStorageInfoResponse(
                mode = exportArtifactStorageProperties.mode.name,
                fileStorageEnabled = fileStorageEnabled,
                baseDir = exportArtifactStorageProperties.baseDir.takeIf { it.isNotBlank() },
                keyPrefix = exportArtifactStorageProperties.keyPrefix.takeIf { it.isNotBlank() },
                bucket = exportArtifactStorageProperties.bucket.takeIf { it.isNotBlank() },
                endpointUrl = exportArtifactStorageProperties.endpointUrl.takeIf { it.isNotBlank() },
                pendingScanDelayMs = pendingScanDelayMs,
                pendingBatchSize = pendingBatchSize
            )
        )

    @GetMapping("/reports/jobs")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun listRecentExportJobs(
        @RequestParam(defaultValue = "12") limit: Int,
        @RequestParam(required = false) status: String?
    ): ApiResponse<List<ExportJobStatusResponse>> {
        val normalizedStatus = status
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching { ExportJobStatus.valueOf(it.uppercase()) }
                    .getOrElse { throw BizException("JOB_STATUS_INVALID", "Unsupported export job status: $status") }
            }
        val jobs = exportJobStore.listRecent(limit = limit, status = normalizedStatus)
        return ApiResponse.ok(jobs.map { it.toStatusResponse() })
    }

    @GetMapping("/reports/jobs/{jobId}/download")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun downloadExportJob(@PathVariable jobId: String): ResponseEntity<ByteArrayResource> {
        val job = exportJobStore.find(jobId)
            ?: throw BizException("JOB_NOT_FOUND", "Export job not found: $jobId")
        if (job.status != ExportJobStatus.DONE)
            throw BizException("JOB_NOT_READY", "Export job is not ready (status: ${job.status})")
        val bytes = job.bytes
            ?: throw BizException("JOB_NO_BYTES", "Export job has no content")
        val resource = ByteArrayResource(bytes)
        val contentDisposition = ContentDisposition.attachment()
            .filename(job.fileName ?: "export")
            .build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(job.contentType ?: "application/octet-stream"))
            .contentLength(bytes.size.toLong())
            .body(resource)
    }

    @PostMapping("/reports/jobs/{jobId}/retry")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun retryExportJob(@PathVariable jobId: String): ApiResponse<ExportJobSubmitResponse> {
        val job = exportJobStore.resetFailedForRetry(jobId)
            ?: throw BizException("JOB_NOT_FOUND", "Export job not found: $jobId")
        if (job.status != ExportJobStatus.PENDING) {
            throw BizException("JOB_NOT_RETRYABLE", "Export job is not retryable (status: ${job.status})")
        }
        val request = ExportReportRequest(
            reportId = job.reportId,
            resultId = job.resultId,
            exportFormat = job.exportFormat ?: ExportFormat.TEXT.name,
            desensitized = job.desensitized
        )
        if (request.reportId == null && request.resultId == null) {
            throw BizException("JOB_RETRY_CONTEXT_MISSING", "Export job has no retry context")
        }
        exportService.processExportJob(jobId, request, job.localeTag ?: LocaleContextHolder.getLocale().toLanguageTag())
        return ApiResponse.ok(ExportJobSubmitResponse(jobId = jobId, status = ExportJobStatus.PENDING.name))
    }

    private fun org.sainm.psy.export.service.ExportJob.toStatusResponse() = ExportJobStatusResponse(
        jobId = id,
        status = status.name,
        reportId = reportId,
        resultId = resultId,
        exportFormat = exportFormat,
        localeTag = localeTag,
        desensitized = desensitized,
        fileName = fileName,
        contentType = contentType,
        storageLocation = null,
        fileSize = fileSize,
        error = error,
        createdAt = createdAt.toString(),
        completedAt = completedAt?.toString()
    )
}
