package org.sainm.psy.export.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.export.api.ExportFormat
import org.sainm.psy.export.api.ExportReportRequest
import org.sainm.psy.export.api.ExportReportResponse
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.service.ReportService
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

@Service
class ExportService(
    private val reportService: ReportService,
    private val securityAuditService: SecurityAuditService
) {

    fun exportReport(request: ExportReportRequest): ExportReportResponse {
        val report = resolveReport(request)
        val exportFormat = resolveExportFormat(request.exportFormat)
        val generatedAt = timestamp(withDateTime = true)
        val exportId = UUID.randomUUID().toString()
        val fileName = "psy-report-${report.reportId}-${timestamp()}.${exportFormat.extension}"

        val payload = when (exportFormat) {
            ExportFormat.TEXT -> buildTextPayload(report, generatedAt)
            ExportFormat.PDF -> buildPdfPayload(report, generatedAt)
        }

        securityAuditService.recordReportExported(
            reportId = report.reportId,
            resultId = report.resultId,
            reportType = report.reportType,
            riskLevel = report.riskLevel,
            exportFormat = exportFormat.name,
            exportChannel = "INLINE"
        )

        return ExportReportResponse(
            exportId = exportId,
            fileName = fileName,
            exportFormat = exportFormat.name,
            downloadExtension = exportFormat.extension,
            contentType = payload.contentType,
            contentEncoding = payload.contentEncoding,
            generatedAt = generatedAt,
            reportId = report.reportId,
            resultId = report.resultId,
            content = payload.content
        )
    }

    fun exportReportFile(request: ExportReportRequest): ExportDownloadArtifact {
        val report = resolveReport(request)
        val exportFormat = resolveExportFormat(request.exportFormat)
        val generatedAt = timestamp(withDateTime = true)
        val exportId = UUID.randomUUID().toString()
        val fileName = "psy-report-${report.reportId}-${timestamp()}.${exportFormat.extension}"
        val bytes = when (exportFormat) {
            ExportFormat.TEXT -> buildStructuredText(report, generatedAt).toByteArray(Charsets.UTF_8)
            ExportFormat.PDF -> buildPdfBytes(report, generatedAt)
        }

        securityAuditService.recordReportExported(
            reportId = report.reportId,
            resultId = report.resultId,
            reportType = report.reportType,
            riskLevel = report.riskLevel,
            exportFormat = exportFormat.name,
            exportChannel = "DOWNLOAD"
        )

        return ExportDownloadArtifact(
            exportId = exportId,
            fileName = fileName,
            exportFormat = exportFormat.name,
            downloadExtension = exportFormat.extension,
            contentType = exportFormat.contentType,
            generatedAt = generatedAt,
            reportId = report.reportId,
            resultId = report.resultId,
            bytes = bytes
        )
    }

    private fun resolveExportFormat(rawFormat: String?): ExportFormat {
        val normalized = rawFormat?.trim()?.uppercase().orEmpty().ifBlank { ExportFormat.TEXT.name }
        return runCatching { ExportFormat.valueOf(normalized) }
            .getOrElse {
                throw BizException("EXPORT_FORMAT_INVALID", "Unsupported export format: $normalized")
            }
    }

    private fun resolveReport(request: ExportReportRequest): ReportDetail {
        val reportId = request.reportId
        val resultId = request.resultId
        return when {
            reportId != null && resultId != null -> {
                val detailByReportId = reportService.findDetail(reportId, audit = false)
                if (detailByReportId.resultId != resultId) {
                    throw BizException("EXPORT_REPORT_MISMATCH", "reportId does not match resultId")
                }
                detailByReportId
            }
            reportId != null -> reportService.findDetail(reportId, audit = false)
            resultId != null -> reportService.findDetailByResultId(resultId, audit = false)
            else -> throw BizException("EXPORT_PARAM_REQUIRED", "reportId or resultId is required")
        }
    }

    private fun buildTextPayload(report: ReportDetail, generatedAt: String): ExportPayload =
        ExportPayload(
            content = buildStructuredText(report, generatedAt),
            contentType = "text/plain; charset=utf-8",
            contentEncoding = "PLAIN"
        )

    private fun buildPdfPayload(report: ReportDetail, generatedAt: String): ExportPayload {
        val document = PDDocument()
        return document.use { pdf ->
            val font = loadCjkFont(pdf)
            val page = PDPage(PDRectangle.A4)
            pdf.addPage(page)

            PDPageContentStream(pdf, page, AppendMode.APPEND, true, true).use { stream ->
                writePdf(stream, font, page.mediaBox, report, generatedAt)
            }

            ByteArrayOutputStream().use { output ->
                pdf.save(output)
                ExportPayload(
                    content = Base64.getEncoder().encodeToString(output.toByteArray()),
                    contentType = ExportFormat.PDF.contentType,
                    contentEncoding = "BASE64"
                )
            }
        }
    }

    private fun buildPdfBytes(report: ReportDetail, generatedAt: String): ByteArray {
        return Base64.getDecoder().decode(buildPdfPayload(report, generatedAt).content)
    }

    private fun writePdf(
        stream: PDPageContentStream,
        font: PDFont,
        box: PDRectangle,
        report: ReportDetail,
        generatedAt: String
    ) {
        val marginLeft = 48f
        val marginTop = 48f
        val marginBottom = 48f
        val pageWidth = box.width
        val pageHeight = box.height
        val contentWidth = pageWidth - marginLeft * 2
        val titleSize = 18f
        val bodySize = 11f
        val lineGap = 15f

        fun beginLine(y: Float, size: Float) {
            stream.beginText()
            stream.setFont(font, size)
            stream.newLineAtOffset(marginLeft, y)
        }

        var cursorY = pageHeight - marginTop
        beginLine(cursorY, titleSize)
        stream.showText("Psychological Assessment Report")
        stream.endText()

        cursorY -= 28f
        val sections = listOf(
            "Generated At: $generatedAt",
            "Report ID: ${report.reportId}",
            "Result ID: ${report.resultId}",
            "Report Type: ${report.reportType}",
            "Total Score: ${report.totalScore}",
            "Risk Level: ${report.riskLevel}",
            "",
            "Content:"
        )

        for (section in sections) {
            if (cursorY <= marginBottom) {
                break
            }
            if (section.isBlank()) {
                cursorY -= lineGap
                continue
            }
            cursorY = drawWrappedText(
                stream = stream,
                font = font,
                text = section,
                size = bodySize,
                startY = cursorY,
                maxWidth = contentWidth,
                marginLeft = marginLeft,
                marginBottom = marginBottom,
                lineGap = lineGap
            )
        }

        cursorY -= 4f
        drawWrappedText(
            stream = stream,
            font = font,
            text = report.content,
            size = bodySize,
            startY = cursorY,
            maxWidth = contentWidth,
            marginLeft = marginLeft,
            marginBottom = marginBottom,
            lineGap = lineGap
        )
    }

    private fun drawWrappedText(
        stream: PDPageContentStream,
        font: PDFont,
        text: String,
        size: Float,
        startY: Float,
        maxWidth: Float,
        marginLeft: Float,
        marginBottom: Float,
        lineGap: Float
    ): Float {
        var cursorY = startY
        val lines = wrapText(text, font, size, maxWidth)
        lines.forEach { line ->
            if (cursorY <= marginBottom) {
                return@forEach
            }
            stream.beginText()
            stream.setFont(font, size)
            stream.newLineAtOffset(marginLeft, cursorY)
            stream.showText(line)
            stream.endText()
            cursorY -= lineGap
        }
        return cursorY
    }

    private fun wrapText(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.lineSequence().forEach { paragraph ->
            if (paragraph.isEmpty()) {
                result += ""
                return@forEach
            }
            var current = StringBuilder()
            paragraph.forEach { ch ->
                val candidate = current.toString() + ch
                val width = font.getStringWidth(candidate) / 1000f * size
                if (width > maxWidth && current.isNotEmpty()) {
                    result += current.toString()
                    current = StringBuilder().append(ch)
                } else {
                    current.append(ch)
                }
            }
            if (current.isNotEmpty()) {
                result += current.toString()
            }
        }
        return result
    }

    private fun loadCjkFont(document: PDDocument): PDFont {
        val candidates = listOf(
            File("C:/Windows/Fonts/simhei.ttf"),
            File("C:/Windows/Fonts/msyh.ttc"),
            File("C:/Windows/Fonts/simsun.ttc")
        )
        for (fontFile in candidates) {
            if (!fontFile.exists()) {
                continue
            }
            return try {
                PDType0Font.load(document, fontFile)
            } catch (_: Exception) {
                continue
            }
        }
        return PDType1Font(Standard14Fonts.FontName.valueOf("HELVETICA"))
    }

    private fun buildStructuredText(report: ReportDetail, generatedAt: String): String =
        buildString {
            appendLine("Text export content")
            appendLine("Generated At: $generatedAt")
            appendLine("Report ID: ${report.reportId}")
            appendLine("Result ID: ${report.resultId}")
            appendLine("Report Type: ${report.reportType}")
            appendLine("Total Score: ${report.totalScore}")
            appendLine("Risk Level: ${report.riskLevel}")
            appendLine()
            appendLine("Content:")
            appendLine(report.content)
        }

    private fun timestamp(withDateTime: Boolean = false): String {
        val pattern = if (withDateTime) "yyyyMMddHHmmss" else "yyyyMMdd"
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern))
    }

    private data class ExportPayload(
        val content: String,
        val contentType: String,
        val contentEncoding: String
    )

    data class ExportDownloadArtifact(
        val exportId: String,
        val fileName: String,
        val exportFormat: String,
        val downloadExtension: String,
        val contentType: String,
        val generatedAt: String,
        val reportId: Long,
        val resultId: Long,
        val bytes: ByteArray
    )
}
