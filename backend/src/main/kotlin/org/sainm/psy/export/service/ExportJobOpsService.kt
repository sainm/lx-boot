package org.sainm.psy.export.service

import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.export.api.ExportReportRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class ExportJobOpsService(
    private val exportService: ExportService,
    private val exportJobStore: ExportJobStore,
    private val currentUserFacade: CurrentUserFacade,
    private val tenantAccessPolicy: TenantAccessPolicy,
    private val securityAuditService: SecurityAuditService
) {

    @Transactional
    fun submitJob(request: ExportReportRequest, localeTag: String): ExportJob {
        val targetTenantId = exportService.validateExportRequest(request)
        val currentUser = currentUserFacade.requireCurrentUser()
        val job = exportJobStore.create(
            id = UUID.randomUUID().toString(),
            reportId = request.reportId,
            resultId = request.resultId,
            exportFormat = request.exportFormat,
            localeTag = localeTag,
            desensitized = request.desensitized,
            createdBy = currentUser.userId,
            tenantId = targetTenantId
        )
        securityAuditService.recordExportJobSubmitted(
            jobId = job.id,
            reportId = job.reportId,
            resultId = job.resultId,
            exportFormat = job.exportFormat,
            desensitized = job.desensitized
        )
        return job
    }

    @Transactional
    fun replayJob(jobId: String): ExportJob {
        val previous = requireAccessibleJob(jobId, includeBytes = false, action = "REPLAY")
        if (previous.status !in setOf(ExportJobStatus.FAILED, ExportJobStatus.DEAD_LETTER)) {
            throw BizException("JOB_NOT_RETRYABLE", "Export job is not retryable (status: ${previous.status})")
        }
        if (previous.reportId == null && previous.resultId == null) {
            throw BizException("JOB_RETRY_CONTEXT_MISSING", "Export job has no retry context")
        }
        val replayed = exportJobStore.resetFailedForRetry(jobId, previous.tenantId)
            ?: throw BizException("JOB_NOT_RETRYABLE", "Export job state changed before replay")
        securityAuditService.recordExportJobReplayed(
            jobId = replayed.id,
            reportId = replayed.reportId,
            resultId = replayed.resultId,
            previousStatus = previous.status.name,
            previousRetryCount = previous.retryCount
        )
        deleteArtifactAfterCommit(previous.filePath)
        return replayed
    }

    fun requireDownloadableJob(jobId: String): ExportJob {
        val job = requireAccessibleJob(jobId, includeBytes = true, action = "DOWNLOAD")
        if (job.status != ExportJobStatus.DONE) {
            throw BizException("JOB_NOT_READY", "Export job is not ready (status: ${job.status})")
        }
        if (job.bytes == null) {
            throw BizException("JOB_NO_BYTES", "Export job has no content")
        }
        securityAuditService.recordExportJobDownloaded(
            jobId = job.id,
            reportId = job.reportId,
            resultId = job.resultId,
            exportFormat = job.exportFormat,
            fileSize = job.fileSize
        )
        return job
    }

    fun requireAccessibleJob(jobId: String, includeBytes: Boolean = false, action: String = "READ"): ExportJob {
        val job = exportJobStore.find(jobId, includeBytes)
            ?: throw BizException("JOB_NOT_FOUND", "Export job not found: $jobId")
        if (!tenantAccessPolicy.canAccess(job.tenantId, "EXPORT_JOB", job.id, action)) {
            throw BizException("JOB_NOT_FOUND", "Export job not found: $jobId")
        }
        return job
    }

    private fun deleteArtifactAfterCommit(location: String?) {
        if (location == null) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        exportJobStore.deleteArtifact(location)
                    }
                }
            )
        } else {
            exportJobStore.deleteArtifact(location)
        }
    }
}
