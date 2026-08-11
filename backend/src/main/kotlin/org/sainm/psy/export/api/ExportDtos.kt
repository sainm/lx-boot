package org.sainm.psy.export.api

import jakarta.validation.constraints.Min

enum class ExportFormat(
    val extension: String,
    val contentType: String
) {
    TEXT("txt", "text/plain; charset=utf-8"),
    PDF("pdf", "application/pdf"),
    WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}

data class ExportReportRequest(
    @field:Min(1, message = "reportId must be greater than 0")
    val reportId: Long? = null,

    @field:Min(1, message = "resultId must be greater than 0")
    val resultId: Long? = null,

    val exportFormat: String = "WORD",

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
    val storageLocation: String?,
    val fileSize: Long?,
    val error: String?,
    val retryCount: Int,
    val nextRetryAt: String?,
    val processingStartedAt: String?,
    val deadLetterAt: String?,
    val createdAt: String,
    val completedAt: String?
)

data class ExportArtifactStorageInfoResponse(
    val mode: String,
    val fileStorageEnabled: Boolean,
    val baseDir: String?,
    val keyPrefix: String?,
    val bucket: String?,
    val endpointUrl: String?,
    val pendingScanDelayMs: Long,
    val pendingBatchSize: Int
)
