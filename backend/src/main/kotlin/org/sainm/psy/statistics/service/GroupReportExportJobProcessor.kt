package org.sainm.psy.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.sainm.psy.export.service.ExportJob
import org.sainm.psy.export.service.ExportJobStore
import org.sainm.psy.statistics.api.GroupReportExportJobRequest
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class GroupReportExportJobProcessor(
    private val exportJobStore: ExportJobStore,
    private val statisticsService: StatisticsService,
    private val objectMapper: ObjectMapper
) {

    @Async
    fun process(jobId: String) {
        exportJobStore.claimPending(jobId)?.let(::processClaimed)
    }

    fun processClaimed(job: ExportJob) {
        val previousLocale = LocaleContextHolder.getLocale()
        try {
            job.localeTag?.takeIf(String::isNotBlank)?.let {
                LocaleContextHolder.setLocale(Locale.forLanguageTag(it))
            }
            val request = objectMapper.readValue(job.requestJson, GroupReportExportJobRequest::class.java)
            val artifact = statisticsService.exportGroupReportsForTenant(
                request.toQuery(),
                job.exportFormat ?: request.format,
                job.tenantId
            )
            exportJobStore.markDone(job.id, artifact.fileName, artifact.contentType, artifact.bytes)
        } catch (e: Exception) {
            exportJobStore.markFailed(job.id, e.message ?: "GROUP_REPORT_EXPORT_FAILED")
        } finally {
            LocaleContextHolder.setLocale(previousLocale)
        }
    }
}
