package org.sainm.psy.statistics.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mockingDetails
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.export.service.ExportJob
import org.sainm.psy.export.service.ExportJobStatus
import org.sainm.psy.export.service.ExportJobStore
import org.sainm.psy.statistics.api.GroupReportExportJobRequest

@ExtendWith(MockitoExtension::class)
class GroupReportExportJobProcessorTest {

    @Mock private lateinit var exportJobStore: ExportJobStore
    @Mock private lateinit var statisticsService: StatisticsService

    @Test
    fun `process claims and exports group report using persisted tenant`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val request = GroupReportExportJobRequest(taskId = 10L, groupId = 20L, format = "CSV")
        val job = ExportJob(
            id = "group-job",
            status = ExportJobStatus.PROCESSING,
            tenantId = 30L,
            sourceType = "GROUP_REPORT",
            requestJson = mapper.writeValueAsString(request),
            exportFormat = "CSV"
        )
        val artifact = GroupReportExportArtifact("group.csv", "text/csv;charset=UTF-8", "a,b".toByteArray())
        `when`(statisticsService.exportGroupReportsForTenant(request.toQuery(), "CSV", 30L)).thenReturn(artifact)
        val processor = GroupReportExportJobProcessor(exportJobStore, statisticsService, mapper)

        processor.processClaimed(job)

        verify(statisticsService).exportGroupReportsForTenant(request.toQuery(), "CSV", 30L)
        verify(exportJobStore).markDone("group-job", artifact.fileName, artifact.contentType, artifact.bytes)
    }

    @Test
    fun `processClaimed marks malformed request failed`() {
        val processor = GroupReportExportJobProcessor(
            exportJobStore,
            statisticsService,
            jacksonObjectMapper().findAndRegisterModules()
        )
        val job = ExportJob(
            id = "bad-job",
            status = ExportJobStatus.PROCESSING,
            sourceType = "GROUP_REPORT",
            requestJson = "not-json"
        )

        processor.processClaimed(job)

        val failureCall = mockingDetails(exportJobStore).invocations.single { it.method.name == "markFailed" }
        kotlin.test.assertEquals("bad-job", failureCall.arguments[0])
        kotlin.test.assertTrue((failureCall.arguments[1] as String).isNotBlank())
    }
}
