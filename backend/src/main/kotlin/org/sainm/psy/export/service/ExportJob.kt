package org.sainm.psy.export.service

import java.time.Instant

enum class ExportJobStatus { PENDING, PROCESSING, DONE, FAILED }

data class ExportJob(
    val id: String,
    val status: ExportJobStatus,
    val reportId: Long? = null,
    val resultId: Long? = null,
    val exportFormat: String? = null,
    val localeTag: String? = null,
    val desensitized: Boolean = true,
    val fileName: String? = null,
    val contentType: String? = null,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val bytes: ByteArray? = null,
    val error: String? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)
