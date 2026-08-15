package org.sainm.psy.export.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import org.sainm.psy.report.domain.ReportDimensionResult
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
            resultTitle = "K6 elevated distress screening result",
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
        assertTrue(text.contains(report.resultTitle!!))
        val pdfText = Loader.loadPDF(pdfBytes).use(PDFTextStripper()::getText)
        assertTrue(pdfText.contains(report.resultTitle!!))
        assertTrue(pdfText.contains(report.resultDescription!!))
        assertTrue(pdfText.contains(report.suggestionText!!))
        assertTrue(pdfText.contains(report.nonDiagnosticText!!))
        val wordText = XWPFDocument(ByteArrayInputStream(wordBytes)).use { document ->
            buildString {
                document.paragraphs.forEach { appendLine(it.text) }
                document.tables.flatMap { it.rows }.flatMap { it.tableCells }.forEach { appendLine(it.text) }
            }
        }
        assertTrue(wordText.contains(report.resultTitle!!))
        assertTrue(wordText.contains(report.resultDescription!!))
        assertTrue(wordText.contains(report.suggestionText!!))
        assertTrue(wordText.contains(report.nonDiagnosticText!!))
    }

    @ParameterizedTest(name = "{0} keeps report semantics in every export renderer")
    @ValueSource(strings = ["SINGLE_SCORE", "RISK_TRIAGE", "NORMATIVE_PROFILE", "DIMENSION_PROFILE"])
    fun `all controlled report templates preserve key semantics across text pdf and word`(template: String) {
        val report = templateReport(template)
        `when`(reportService.findDetail(11L, audit = false)).thenReturn(report)
        val service = exportService()

        val text = service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "TEXT"))
            .bytes.toString(Charsets.UTF_8)
        val pdfText = Loader.loadPDF(
            service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "PDF")).bytes
        ).use(PDFTextStripper()::getText)
        val wordText = extractWordText(
            service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "WORD")).bytes
        )

        listOf(
            report.resultTitle,
            report.resultDescription,
            report.suggestionText,
            report.nonDiagnosticText
        ).forEach { semanticText ->
            requireNotNull(semanticText)
            assertTrue(text.contains(semanticText), "TEXT is missing '$semanticText' for $template")
            assertTrue(pdfText.contains(semanticText), "PDF is missing '$semanticText' for $template")
            assertTrue(wordText.contains(semanticText), "Word is missing '$semanticText' for $template")
        }
        if (template != "SINGLE_SCORE") {
            report.dimensionResults.forEach { dimension ->
                assertTrue(text.contains(dimension.dimensionName), "TEXT is missing ${dimension.dimensionName}")
                assertTrue(pdfText.contains(dimension.dimensionName), "PDF is missing ${dimension.dimensionName}")
                assertTrue(wordText.contains(dimension.dimensionName), "Word is missing ${dimension.dimensionName}")
            }
        }
        if (template == "RISK_TRIAGE") {
            val highRiskCode = requireNotNull(report.highRiskRuleCode)
            assertTrue(text.contains(highRiskCode), "TEXT is missing high-risk rule $highRiskCode")
            assertTrue(pdfText.contains(highRiskCode), "PDF is missing high-risk rule $highRiskCode")
            assertTrue(wordText.contains(highRiskCode), "Word is missing high-risk rule $highRiskCode")
        }
    }

    @Test
    fun `structured explanation is rendered consistently and long pdf reports paginate`() {
        val description = (1..140).joinToString("\n") { index ->
            "structured-description-line-$index"
        }
        val suggestion = "structured-suggestion-not-in-legacy-content"
        val notice = "structured-non-diagnostic-notice"
        val report = sampleReport().copy(
            scaleName = "Synthetic technical fixture",
            resultTitle = "Synthetic result",
            reportTemplate = "SINGLE_SCORE",
            resultDescription = description,
            suggestionText = suggestion,
            nonDiagnosticText = notice,
            // The immutable legacy body can be missing the newly structured
            // fields.  Renderers must use the same presentation model rather
            // than silently dropping those fields in one format.
            content = "legacy summary only"
        )
        `when`(reportService.findDetail(11L, audit = false)).thenReturn(report)
        val service = exportService()

        val text = service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "TEXT"))
            .bytes.toString(Charsets.UTF_8)
        val pdfBytes = service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "PDF")).bytes
        val pdfText = Loader.loadPDF(pdfBytes).use { document ->
            assertTrue(document.numberOfPages > 1, "long report must continue onto another PDF page")
            PDFTextStripper().getText(document)
        }
        val wordText = extractWordText(
            service.exportReportFile(ExportReportRequest(reportId = 11L, exportFormat = "WORD")).bytes
        )

        listOf(description.substringAfterLast('\n'), suggestion, notice).forEach { semanticText ->
            assertTrue(text.contains(semanticText), "TEXT is missing '$semanticText'")
            assertTrue(pdfText.contains(semanticText), "PDF is missing '$semanticText'")
            assertTrue(wordText.contains(semanticText), "Word is missing '$semanticText'")
        }
    }

    private fun templateReport(template: String): ReportDetail {
        val scaleCode = when (template) {
            "SINGLE_SCORE" -> "K6"
            "RISK_TRIAGE" -> "PHQ9"
            "NORMATIVE_PROFILE" -> "SCL90"
            "DIMENSION_PROFILE" -> "SCS-SF"
            else -> error("Unsupported test template $template")
        }
        val dimensions = if (template == "SINGLE_SCORE" || template == "RISK_TRIAGE") {
            emptyList()
        } else {
            listOf(
                ReportDimensionResult(
                    dimensionCode = "DIMENSION_A",
                    dimensionName = "$scaleCode dimension A",
                    score = BigDecimal("2.50"),
                    resultTitle = "$scaleCode dimension A profile"
                ),
                ReportDimensionResult(
                    dimensionCode = "DIMENSION_B",
                    dimensionName = "$scaleCode dimension B",
                    score = BigDecimal("3.25"),
                    resultTitle = "$scaleCode dimension B profile"
                )
            )
        }
        val highRisk = template == "RISK_TRIAGE"
        val description = "$scaleCode result description for $template"
        val suggestion = "$scaleCode follow-up suggestion for $template"
        val notice = "$scaleCode non-diagnostic notice for $template"
        return sampleReport().copy(
            scaleCode = scaleCode,
            scaleVersionNo = "technical-template-v1",
            scaleName = "$scaleCode controlled report fixture",
            resultTitle = "$scaleCode result title for $template",
            reportTemplate = template,
            totalScore = BigDecimal("13.50"),
            riskLevel = if (highRisk) "HIGH" else "ATTENTION",
            resultDescription = description,
            suggestionText = suggestion,
            nonDiagnosticText = notice,
            content = "$description\n$suggestion\n$notice",
            highRiskFlag = highRisk,
            highRiskRuleCode = if (highRisk) "${scaleCode}_HIGH_RISK" else null,
            dimensionResults = dimensions
        )
    }

    private fun extractWordText(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            buildString {
                document.paragraphs.forEach { appendLine(it.text) }
                document.tables.flatMap { it.rows }.flatMap { it.tableCells }.forEach { appendLine(it.text) }
            }
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
