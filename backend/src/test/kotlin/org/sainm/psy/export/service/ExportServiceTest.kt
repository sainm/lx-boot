package org.sainm.psy.export.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.privacy.DataMaskingService
import org.sainm.psy.export.api.ExportReportRequest
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.service.ReportService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class ExportServiceTest {

    @Mock private lateinit var reportService: ReportService
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var jobStore: ExportJobStore

    @Test
    fun `background export does not require current user audit context`() {
        `when`(reportService.findDetailForSystemExport(11L, null)).thenReturn(sampleReport())
        val service = exportService()

        val artifact = service.exportReportFile(
            ExportReportRequest(reportId = 11L, exportFormat = "TEXT"),
            requireCurrentUserAccess = false
        )

        assertEquals(11L, artifact.reportId)
        verify(reportService).findDetailForSystemExport(11L, null)
        verify(securityAuditService, never()).recordReportExported(
            reportId = 11L,
            resultId = 22L,
            reportType = "SYSTEM",
            riskLevel = "LOW",
            exportFormat = "TEXT",
            exportChannel = "DOWNLOAD"
        )
    }

    @Test
    fun `interactive download still records report export audit`() {
        `when`(reportService.findDetail(11L, audit = false)).thenReturn(sampleReport())
        val service = exportService()

        service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "TEXT"))

        verify(reportService).findDetail(11L, audit = false)
        verify(securityAuditService).recordReportExported(
            reportId = 11L,
            resultId = 22L,
            reportType = "SYSTEM",
            riskLevel = "LOW",
            exportFormat = "TEXT",
            exportChannel = "DOWNLOAD"
        )
        verifyNoInteractions(jobStore)
    }

    private fun exportService(): ExportService {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        return ExportService(
            reportService = reportService,
            securityAuditService = securityAuditService,
            jobStore = jobStore,
            messages = LocalizedMessages(messageSource),
            dataMaskingService = DataMaskingService()
        )
    }

    private fun sampleReport(): ReportDetail =
        ReportDetail(
            reportId = 11L,
            resultId = 22L,
            userId = 5L,
            reportType = "SYSTEM",
            totalScore = BigDecimal("12"),
            riskLevel = "LOW",
            content = "Sample report"
        )
}
