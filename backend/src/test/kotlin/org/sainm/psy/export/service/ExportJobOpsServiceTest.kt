package org.sainm.psy.export.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.export.api.ExportReportRequest

@ExtendWith(MockitoExtension::class)
class ExportJobOpsServiceTest {
    @Mock private lateinit var exportService: ExportService
    @Mock private lateinit var exportJobStore: ExportJobStore
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var tenantAccessPolicy: TenantAccessPolicy
    @Mock private lateinit var securityAuditService: SecurityAuditService

    private val user = UserPrincipal(
        userId = 42L,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 7L,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )

    @Test
    fun `submit validates tenant creates job and requires audit`() {
        whenever(currentUserFacade.requireCurrentUser()).thenReturn(user)
        whenever(exportService.validateExportRequest(any())).thenReturn(7L)
        val created = ExportJob("job-1", ExportJobStatus.PENDING, reportId = 10L, tenantId = 7L)
        whenever(exportJobStore.create(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(created)
        val service = service()

        val result = service.submitJob(ExportReportRequest(reportId = 10L, exportFormat = "TEXT"), "zh-CN")

        assertEquals(created, result)
        verify(securityAuditService).recordExportJobSubmitted("job-1", 10L, null, null, true)
    }

    @Test
    fun `replay requires tenant ownership and audits previous retry state`() {
        val previous = ExportJob(
            "job-1", ExportJobStatus.DEAD_LETTER, reportId = 10L, tenantId = 7L,
            filePath = "memory://old", retryCount = 3
        )
        val replayed = previous.copy(status = ExportJobStatus.PENDING, retryCount = 0, filePath = null)
        whenever(exportJobStore.find("job-1", false)).thenReturn(previous)
        whenever(tenantAccessPolicy.canAccess(7L, "EXPORT_JOB", "job-1", "REPLAY")).thenReturn(true)
        whenever(exportJobStore.resetFailedForRetry("job-1", 7L)).thenReturn(replayed)
        val service = service()

        assertEquals(replayed, service.replayJob("job-1"))
        verify(securityAuditService).recordExportJobReplayed("job-1", 10L, null, "DEAD_LETTER", 3)
        verify(exportJobStore).deleteArtifact("memory://old")
    }

    @Test
    fun `replay rejects cross tenant job before mutation`() {
        val previous = ExportJob("job-1", ExportJobStatus.DEAD_LETTER, reportId = 10L, tenantId = 8L)
        whenever(exportJobStore.find("job-1", false)).thenReturn(previous)
        whenever(tenantAccessPolicy.canAccess(8L, "EXPORT_JOB", "job-1", "REPLAY")).thenReturn(false)
        val error = assertThrows<BizException> { service().replayJob("job-1") }

        assertEquals("JOB_NOT_FOUND", error.code)
        verify(exportJobStore, org.mockito.kotlin.never()).resetFailedForRetry(any(), anyOrNull())
    }

    @Test
    fun `download checks done bytes then requires audit`() {
        val done = ExportJob(
            "job-1", ExportJobStatus.DONE, reportId = 10L, tenantId = 7L,
            exportFormat = "TEXT", fileSize = 3L, bytes = byteArrayOf(1, 2, 3)
        )
        whenever(exportJobStore.find("job-1", true)).thenReturn(done)
        whenever(tenantAccessPolicy.canAccess(7L, "EXPORT_JOB", "job-1", "DOWNLOAD")).thenReturn(true)
        val result = service().requireDownloadableJob("job-1")

        assertEquals(3L, result.fileSize)
        verify(securityAuditService).recordExportJobDownloaded("job-1", 10L, null, "TEXT", 3L)
    }

    private fun service() = ExportJobOpsService(
        exportService, exportJobStore, currentUserFacade, tenantAccessPolicy, securityAuditService
    )
}
