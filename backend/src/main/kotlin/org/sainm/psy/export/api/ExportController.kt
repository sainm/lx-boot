package org.sainm.psy.export.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.export.service.ExportJobStatus
import org.sainm.psy.export.service.ExportJobStore
import org.sainm.psy.export.service.ExportService
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
    private val exportJobStore: ExportJobStore
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
        @RequestParam(defaultValue = "TEXT") exportFormat: String
    ): ResponseEntity<ByteArrayResource> {
        val download = exportService.exportReportFile(
            ExportReportRequest(reportId = reportId, resultId = resultId, exportFormat = exportFormat)
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
            localeTag = localeTag
        )
        exportService.processExportJob(jobId, request, localeTag)
        return ApiResponse.ok(ExportJobSubmitResponse(jobId = jobId, status = ExportJobStatus.PENDING.name))
    }

    @GetMapping("/reports/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun getExportJobStatus(@PathVariable jobId: String): ApiResponse<ExportJobStatusResponse> {
        val job = exportJobStore.find(jobId)
            ?: throw BizException("JOB_NOT_FOUND", "Export job not found: $jobId")
        return ApiResponse.ok(
            ExportJobStatusResponse(
                jobId = job.id,
                status = job.status.name,
                reportId = job.reportId,
                resultId = job.resultId,
                exportFormat = job.exportFormat,
                localeTag = job.localeTag,
                fileName = job.fileName,
                contentType = job.contentType,
                error = job.error,
                createdAt = job.createdAt.toString(),
                completedAt = job.completedAt?.toString()
            )
        )
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
            exportFormat = job.exportFormat ?: ExportFormat.TEXT.name
        )
        if (request.reportId == null && request.resultId == null) {
            throw BizException("JOB_RETRY_CONTEXT_MISSING", "Export job has no retry context")
        }
        exportService.processExportJob(jobId, request, job.localeTag ?: LocaleContextHolder.getLocale().toLanguageTag())
        return ApiResponse.ok(ExportJobSubmitResponse(jobId = jobId, status = ExportJobStatus.PENDING.name))
    }
}
