package org.sainm.psy.export.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.export.api.ExportFormat
import org.sainm.psy.export.service.ExportService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/exports")
class ExportController(
    private val exportService: ExportService
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
            ExportReportRequest(
                reportId = reportId,
                resultId = resultId,
                exportFormat = exportFormat
            )
        )
        val resource = ByteArrayResource(download.bytes)
        val contentDisposition = ContentDisposition.attachment()
            .filename(download.fileName)
            .build()

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
}
