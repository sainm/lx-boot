package org.sainm.psy.export.api

import jakarta.validation.constraints.Min

enum class ExportFormat(
    val extension: String,
    val contentType: String
) {
    TEXT("txt", "text/plain; charset=utf-8"),
    PDF("pdf", "application/pdf")
}

data class ExportReportRequest(
    @field:Min(1, message = "reportId must be greater than 0")
    val reportId: Long? = null,

    @field:Min(1, message = "resultId must be greater than 0")
    val resultId: Long? = null,

    val exportFormat: String = "TEXT",

    val desensitized: Boolean = true
)

data class ExportReportResponse(
    val exportId: String,
    val fileName: String,
    val exportFormat: String,
    val downloadExtension: String,
    val contentType: String,
    val contentEncoding: String,
    val generatedAt: String,
    val reportId: Long,
    val resultId: Long,
    val desensitized: Boolean,
    val content: String
)

data class ExportJobSubmitResponse(
    val jobId: String,
    val status: String
)

data class ExportJobStatusResponse(
    val jobId: String,
    val status: String,
    val reportId: Long?,
    val resultId: Long?,
    val exportFormat: String?,
    val localeTag: String?,
    val desensitized: Boolean,
    val fileName: String?,
    val contentType: String?,
    val fileSize: Long?,
    val error: String?,
    val createdAt: String,
    val completedAt: String?
)
