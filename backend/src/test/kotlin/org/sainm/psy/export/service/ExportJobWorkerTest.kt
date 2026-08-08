package org.sainm.psy.export.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.statistics.service.GroupReportExportJobProcessor
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class ExportJobWorkerTest {

    @Mock private lateinit var exportJobStore: ExportJobStore
    @Mock private lateinit var exportService: ExportService
    @Mock private lateinit var groupReportExportJobProcessor: GroupReportExportJobProcessor

    private lateinit var exportJobWorker: ExportJobWorker

    @BeforeEach
    fun setUp() {
        exportJobWorker = ExportJobWorker(
            exportJobStore = exportJobStore,
            exportService = exportService,
            groupReportExportJobProcessor = groupReportExportJobProcessor,
            pendingBatchSize = 20
        )
    }

    @Test
    fun `processPendingJobs claims pending jobs and dispatches them`() {
        val jobs = listOf(
            ExportJob(id = "job-1", status = ExportJobStatus.PROCESSING, reportId = 10L, exportFormat = "TEXT"),
            ExportJob(id = "job-2", status = ExportJobStatus.PROCESSING, reportId = 11L, exportFormat = "PDF")
        )
        `when`(exportJobStore.claimPendingJobs(org.mockito.ArgumentMatchers.eq(20), anyInstant()))
            .thenReturn(jobs)

        val processed = exportJobWorker.processPendingJobs()

        assertEquals(2, processed)
        verify(exportService).processClaimedExportJob(jobs[0])
        verify(exportService).processClaimedExportJob(jobs[1])
    }

    @Test
    fun `processPendingJobs returns zero when there are no pending jobs`() {
        `when`(exportJobStore.claimPendingJobs(org.mockito.ArgumentMatchers.eq(20), anyInstant()))
            .thenReturn(emptyList())

        val processed = exportJobWorker.processPendingJobs()

        assertEquals(0, processed)
        verifyNoInteractions(exportService)
    }

    @Test
    fun `processPendingJobs dispatches group report request from persisted context`() {
        val job = ExportJob(
            id = "group-job",
            status = ExportJobStatus.PROCESSING,
            sourceType = "GROUP_REPORT",
            requestJson = "{}",
            exportFormat = "CSV"
        )
        `when`(exportJobStore.claimPendingJobs(org.mockito.ArgumentMatchers.eq(20), anyInstant())).thenReturn(listOf(job))

        assertEquals(1, exportJobWorker.processPendingJobs())

        verify(groupReportExportJobProcessor).processClaimed(job)
        verifyNoInteractions(exportService)
    }
}

private fun anyInstant(): Instant {
    org.mockito.ArgumentMatchers.any(Instant::class.java)
    return Instant.EPOCH
}
