package org.sainm.psy.export.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.io.ByteArrayInputStream
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

    @Test
    fun `single score result and recommendation are preserved in text pdf and word renderers`() {
        val report = sampleReport().copy(
            scaleName = "Kessler Psychological Distress Scale (K6)",
            reportTemplate = "SINGLE_SCORE",
            totalScore = BigDecimal("13"),
            riskLevel = "ATTENTION",
            resultDescription = "The K6 total is 13 and requires population-scoped follow-up assessment.",
            suggestionText = "A qualified professional should conduct further assessment.",
            nonDiagnosticText = "The K6 is a screening tool and does not establish a clinical diagnosis.",
            content = "The K6 total is 13 and requires population-scoped follow-up assessment.\n" +
                "A qualified professional should conduct further assessment.\n" +
                "The K6 is a screening tool and does not establish a clinical diagnosis."
        )
        `when`(reportService.findDetail(11L, audit = false)).thenReturn(report)
        val service = exportService()

        val text = service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "TEXT"))
            .bytes.toString(Charsets.UTF_8)
        val pdfBytes = service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "PDF")).bytes
        val wordBytes = service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "WORD")).bytes

        assertTrue(text.contains(report.resultDescription!!))
        assertTrue(text.contains(report.suggestionText!!))
        assertTrue(text.contains(report.nonDiagnosticText!!))
        val pdfText = Loader.loadPDF(pdfBytes).use(PDFTextStripper()::getText)
        assertTrue(pdfText.contains(report.resultDescription!!))
        assertTrue(pdfText.contains(report.suggestionText!!))
        assertTrue(pdfText.contains(report.nonDiagnosticText!!))
        val wordText = XWPFDocument(ByteArrayInputStream(wordBytes)).use { document ->
            buildString {
                document.paragraphs.forEach { appendLine(it.text) }
                document.tables.flatMap { it.rows }.flatMap { it.tableCells }.forEach { appendLine(it.text) }
            }
        }
        assertTrue(wordText.contains(report.resultDescription!!))
        assertTrue(wordText.contains(report.suggestionText!!))
        assertTrue(wordText.contains(report.nonDiagnosticText!!))
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
