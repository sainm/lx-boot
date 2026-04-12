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
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.export.api.ExportFormat
import org.sainm.psy.export.api.ExportReportRequest
import org.sainm.psy.export.api.ExportReportResponse
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.service.ReportService
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class ExportService(
    private val reportService: ReportService,
    private val securityAuditService: SecurityAuditService,
    private val jobStore: ExportJobStore,
    private val messages: LocalizedMessages
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

    @Async
    fun processExportJob(jobId: String, request: ExportReportRequest, localeTag: String? = null) {
        withLocale(localeTag) {
            jobStore.markProcessing(jobId)
            try {
                val artifact = exportReportFile(request)
                jobStore.markDone(jobId, artifact.fileName, artifact.contentType, artifact.bytes)
            } catch (e: Exception) {
                jobStore.markFailed(jobId, e.message ?: messages.get("export.job_failed"))
            }
        }
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
                throw BizException("EXPORT_FORMAT_INVALID", messages.get("export.format_invalid", normalized))
            }
    }

    private fun resolveReport(request: ExportReportRequest): ReportDetail {
        val reportId = request.reportId
        val resultId = request.resultId
        return when {
            reportId != null && resultId != null -> {
                val detailByReportId = reportService.findDetail(reportId, audit = false)
                if (detailByReportId.resultId != resultId) {
                    throw BizException("EXPORT_REPORT_MISMATCH", messages.get("export.report_mismatch"))
                }
                detailByReportId
            }
            reportId != null -> reportService.findDetail(reportId, audit = false)
            resultId != null -> reportService.findDetailByResultId(resultId, audit = false)
            else -> throw BizException("EXPORT_PARAM_REQUIRED", messages.get("export.param_required"))
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
        stream.showText(messages.get("export.pdf.title"))
        stream.endText()

        cursorY -= 28f
        val sections = listOf(
            messages.get("export.generated_at", generatedAt),
            messages.get("export.report_id", report.reportId),
            messages.get("export.result_id", report.resultId),
            messages.get("export.report_type", report.reportType),
            messages.get("export.total_score", report.totalScore),
            messages.get("export.risk_level", report.riskLevel),
            "",
            messages.get("export.content_heading")
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
        // Prefer classpath-embedded font (place NotoSansCJK-Regular.ttf under src/main/resources/fonts/)
        try {
            val stream = javaClass.getResourceAsStream("/fonts/NotoSansCJK-Regular.ttf")
            if (stream != null) {
                return PDType0Font.load(document, stream)
            }
        } catch (_: Exception) { }

        val candidates = listOf(
            // Windows
            File("C:/Windows/Fonts/simhei.ttf"),
            File("C:/Windows/Fonts/msyh.ttc"),
            File("C:/Windows/Fonts/simsun.ttc"),
            // Linux — WQY / Noto CJK (common distributions)
            File("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"),
            File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
            File("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"),
            File("/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc"),
            File("/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc"),
            // macOS
            File("/System/Library/Fonts/STHeiti Medium.ttc"),
            File("/System/Library/Fonts/Hiragino Sans GB.ttc"),
            File("/Library/Fonts/Arial Unicode MS.ttf")
        )
        for (fontFile in candidates) {
            if (!fontFile.exists()) continue
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
            appendLine(messages.get("export.text.heading"))
            appendLine(messages.get("export.generated_at", generatedAt))
            appendLine(messages.get("export.report_id", report.reportId))
            appendLine(messages.get("export.result_id", report.resultId))
            appendLine(messages.get("export.report_type", report.reportType))
            appendLine(messages.get("export.total_score", report.totalScore))
            appendLine(messages.get("export.risk_level", report.riskLevel))
            appendLine()
            appendLine(messages.get("export.content_heading"))
            appendLine(report.content)
        }

    private fun <T> withLocale(localeTag: String?, block: () -> T): T {
        val previousLocale = LocaleContextHolder.getLocale()
        val nextLocale = localeTag
            ?.takeIf { it.isNotBlank() }
            ?.let(Locale::forLanguageTag)
            ?: previousLocale
        return try {
            LocaleContextHolder.setLocale(nextLocale)
            block()
        } finally {
            LocaleContextHolder.setLocale(previousLocale)
        }
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
