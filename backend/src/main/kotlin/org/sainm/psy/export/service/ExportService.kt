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
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.privacy.DataMaskingService
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
import java.math.BigDecimal
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
    private val messages: LocalizedMessages,
    private val dataMaskingService: DataMaskingService
) {

    fun exportReport(request: ExportReportRequest): ExportReportResponse {
        val report = sanitizeReport(resolveReport(request), request.desensitized)
        val exportFormat = resolveExportFormat(request.exportFormat)
        val generatedAt = timestamp(withDateTime = true)
        val exportId = UUID.randomUUID().toString()
        val fileName = buildReportFileName(report, exportFormat)

        val payload = when (exportFormat) {
            ExportFormat.TEXT -> buildTextPayload(report, generatedAt)
            ExportFormat.PDF -> buildPdfPayload(report, generatedAt)
            ExportFormat.WORD -> buildWordPayload(report, generatedAt)
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
            desensitized = request.desensitized,
            content = payload.content
        )
    }

    @Async
    fun processExportJob(jobId: String, request: ExportReportRequest, localeTag: String? = null) {
        val claimed = jobStore.claimPending(jobId) ?: return
        withLocale(localeTag) {
            try {
                val artifact = exportReportFile(request, requireCurrentUserAccess = false)
                jobStore.markDone(
                    claimed.id,
                    artifact.fileName,
                    artifact.contentType,
                    artifact.bytes,
                    claimed.processingToken
                )
            } catch (e: Exception) {
                val error = e.message ?: messages.get("export.job_failed")
                if (claimed.processingToken != null) {
                    jobStore.markAttemptFailed(claimed.id, claimed.processingToken, error)
                } else {
                    jobStore.markFailed(claimed.id, error)
                }
            }
        }
    }

    fun processClaimedExportJob(job: ExportJob) {
        val request = ExportReportRequest(
            reportId = job.reportId,
            resultId = job.resultId,
            exportFormat = job.exportFormat ?: ExportFormat.WORD.name,
            desensitized = job.desensitized
        )
        withLocale(job.localeTag) {
            try {
                val artifact = exportReportFile(request, requireCurrentUserAccess = false)
                jobStore.markDone(job.id, artifact.fileName, artifact.contentType, artifact.bytes, job.processingToken)
            } catch (e: Exception) {
                val error = e.message ?: messages.get("export.job_failed")
                if (job.processingToken != null) {
                    jobStore.markAttemptFailed(job.id, job.processingToken, error)
                } else {
                    jobStore.markFailed(job.id, error)
                }
            }
        }
    }

    fun exportReportFile(
        request: ExportReportRequest,
        requireCurrentUserAccess: Boolean = true
    ): ExportDownloadArtifact {
        val report = sanitizeReport(resolveReport(request, requireCurrentUserAccess), request.desensitized)
        val exportFormat = resolveExportFormat(request.exportFormat)
        val generatedAt = timestamp(withDateTime = true)
        val exportId = UUID.randomUUID().toString()
        val fileName = buildReportFileName(report, exportFormat)
        val bytes = when (exportFormat) {
            ExportFormat.TEXT -> buildStructuredText(report, generatedAt).toByteArray(Charsets.UTF_8)
            ExportFormat.PDF -> buildPdfBytes(report, generatedAt)
            ExportFormat.WORD -> buildWordBytes(report, generatedAt)
        }

        if (requireCurrentUserAccess) {
            securityAuditService.recordReportExported(
                reportId = report.reportId,
                resultId = report.resultId,
                reportType = report.reportType,
                riskLevel = report.riskLevel,
                exportFormat = exportFormat.name,
                exportChannel = "DOWNLOAD"
            )
        }

        return ExportDownloadArtifact(
            exportId = exportId,
            fileName = fileName,
            exportFormat = exportFormat.name,
            downloadExtension = exportFormat.extension,
            contentType = exportFormat.contentType,
            generatedAt = generatedAt,
            reportId = report.reportId,
            resultId = report.resultId,
            desensitized = request.desensitized,
            bytes = bytes
        )
    }

    fun validateExportRequest(request: ExportReportRequest): Long {
        val report = resolveReport(request, requireCurrentUserAccess = true)
        resolveExportFormat(request.exportFormat)
        return report.tenantId
            ?: throw BizException("REPORT_TENANT_REQUIRED", messages.get("export.report_tenant_required"))
    }

    private fun sanitizeReport(report: ReportDetail, desensitized: Boolean): ReportDetail =
        if (!desensitized) {
            report
        } else {
            report.copy(content = dataMaskingService.maskText(report.content))
        }

    private fun resolveExportFormat(rawFormat: String?): ExportFormat {
        val normalized = rawFormat?.trim()?.uppercase().orEmpty().ifBlank { ExportFormat.WORD.name }
        if (normalized == "DOCX") {
            return ExportFormat.WORD
        }
        return runCatching { ExportFormat.valueOf(normalized) }
            .getOrElse {
                throw BizException("EXPORT_FORMAT_INVALID", messages.get("export.format_invalid", normalized))
            }
    }

    private fun buildReportFileName(report: ReportDetail, exportFormat: ExportFormat): String {
        val scaleName = report.scaleName?.takeIf { it.isNotBlank() } ?: messages.get("export.personal.default_scale_name")
        val respondentName = report.displayName?.takeIf { it.isNotBlank() } ?: report.username?.takeIf { it.isNotBlank() }
        val baseName = if (respondentName.isNullOrBlank()) {
            messages.get("export.personal.dynamic_file_name_without_name", scaleName)
        } else {
            messages.get("export.personal.dynamic_file_name", respondentName, scaleName)
        }
        return "${sanitizeFileName(baseName)}.${exportFormat.extension}"
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().ifBlank { "report" }

    private fun resolveReport(
        request: ExportReportRequest,
        requireCurrentUserAccess: Boolean = true
    ): ReportDetail {
        if (!requireCurrentUserAccess) {
            return reportService.findDetailForSystemExport(request.reportId, request.resultId)
        }
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

    private fun buildWordPayload(report: ReportDetail, generatedAt: String): ExportPayload =
        ExportPayload(
            content = Base64.getEncoder().encodeToString(buildWordBytes(report, generatedAt)),
            contentType = ExportFormat.WORD.contentType,
            contentEncoding = "BASE64"
        )

    private fun buildWordBytes(report: ReportDetail, generatedAt: String): ByteArray {
        val model = buildPersonalReportModel(report, generatedAt)
        val document = XWPFDocument()
        document.use { doc ->
            doc.addHeading(personalReportTitle(report), 18, ParagraphAlignment.CENTER)
            doc.addHeading(messages.get("export.personal.section.basic"), 13)
            doc.addText(messages.get("export.personal.respondent_name", model.respondentName))
            doc.addText(messages.get("export.personal.assessment_date", model.assessmentDate))
            doc.addText(messages.get("export.personal.purpose"))

            doc.addHeading(messages.get("export.personal.section.overall"), 13)
            doc.addTable(
                listOf(
                    messages.get("export.personal.metric"),
                    messages.get("export.personal.value"),
                    messages.get("export.personal.reference_range"),
                    messages.get("export.personal.interpretation")
                ),
                model.overallRows.map {
                    listOf(it.metric, it.value, it.referenceRange, it.interpretation)
                }
            )

            doc.addHeading(messages.get("export.personal.section.dimensions"), 13)
            doc.addTable(
                listOf(
                    messages.get("export.personal.dimension_factor"),
                    messages.get("export.personal.average_score"),
                    messages.get("export.personal.critical_value"),
                    messages.get("export.personal.description")
                ),
                model.dimensionRows.map {
                    listOf(it.dimensionName, it.score, it.referenceRange, it.description)
                }
            )

            doc.addHeading(messages.get("export.personal.section.content"), 13)
            doc.addText(messages.get("export.personal.result_description"))
            model.resultDescription.lineSequence().forEach { line -> doc.addText(line.ifBlank { " " }) }
            doc.addText(messages.get("export.personal.psychological_suggestion"))
            model.suggestion.lineSequence().forEach { line -> doc.addText(line.ifBlank { " " }) }

            doc.addHeading(messages.get("export.personal.section.notice"), 13)
            doc.addText(messages.get("export.personal.notice"))

            ByteArrayOutputStream().use { output ->
                doc.write(output)
                return output.toByteArray()
            }
        }
    }

    private fun buildPersonalReportModel(report: ReportDetail, generatedAt: String): PersonalReportModel {
        val overallRows = report.metrics.map { metric ->
            PersonalOverallRow(
                metric = metricLabel(metric.code),
                value = metric.displayValue,
                referenceRange = metric.referenceText ?: messages.get("export.personal.reference_not_configured"),
                interpretation = if (metric.reviewStatus == "PENDING_PROFESSIONAL_REVIEW") {
                    messages.get("export.personal.pending_professional_review")
                } else {
                    metric.interpretationCode
                }
            )
        }.ifEmpty {
            listOf(
                PersonalOverallRow(
                    metric = messages.get("export.personal.metric.total_score"),
                    value = formatDecimal(report.totalScore),
                    referenceRange = messages.get("export.personal.reference_not_configured"),
                    interpretation = messages.get("export.personal.pending_professional_review")
                )
            )
        }
        val dimensionRows = report.dimensionResults.map { dimension ->
            PersonalDimensionRow(
                dimensionName = dimension.dimensionName,
                score = formatDecimal(dimension.score),
                referenceRange = dimension.referenceText ?: messages.get("export.personal.reference_not_configured"),
                description = dimension.resultTitle
                    ?: dimension.riskLevel
                    ?: messages.get("export.personal.pending_professional_review")
            )
        }

        val contentParts = splitReportContent(report.content)
        val respondentName = report.displayName?.takeIf { it.isNotBlank() } ?: report.username ?: report.userId?.toString() ?: "-"
        val assessmentDate = report.createdAt?.format(DateTimeFormatter.ofPattern("yyyy/M/d")) ?: generatedAt
        return PersonalReportModel(
            respondentName = respondentName,
            assessmentDate = assessmentDate,
            overallRows = overallRows,
            dimensionRows = dimensionRows,
            resultDescription = contentParts.first.ifBlank { defaultResultDescription(report.riskLevel) },
            suggestion = contentParts.second.ifBlank { defaultSuggestion(report.riskLevel) }
        )
    }

    private fun metricLabel(code: String): String = when (code) {
        "TOTAL_SCORE" -> messages.get("export.personal.metric.total_score")
        "STANDARD_SCORE" -> messages.get("export.personal.metric.standard_score")
        "Z_SCORE" -> messages.get("export.personal.metric.z_score")
        "T_SCORE" -> messages.get("export.personal.metric.t_score")
        else -> code
    }

    private fun splitReportContent(content: String): Pair<String, String> {
        val normalized = content.replace("\r\n", "\n")
        val suggestionMarkers = listOf(
            messages.get("export.personal.marker.suggestion"),
            messages.get("export.personal.marker.suggestion_short"),
            messages.get("export.personal.marker.section_suggestion")
        )
        val marker = suggestionMarkers.firstOrNull { normalized.contains(it) }
        if (marker != null) {
            val parts = normalized.split(marker, limit = 2)
            return stripResultMarker(parts[0]).trim() to parts.getOrElse(1) { "" }.trim()
        }
        return stripResultMarker(normalized).trim() to ""
    }

    private fun stripResultMarker(value: String): String =
        value.replace(messages.get("export.personal.marker.result_description"), "")
            .replace(messages.get("export.personal.marker.result_description_short"), "")

    private fun defaultResultDescription(riskLevel: String): String =
        when (riskLevel.uppercase()) {
            "CRITICAL", "P0", "HIGH", "P1" -> messages.get("export.personal.default_result.high")
            "MODERATE", "MEDIUM", "ATTENTION", "P2" -> messages.get("export.personal.default_result.medium")
            "LOW", "NORMAL" -> messages.get("export.personal.default_result.low")
            else -> messages.get("export.personal.pending_professional_review")
        }

    private fun defaultSuggestion(riskLevel: String): String =
        when (riskLevel.uppercase()) {
            "CRITICAL", "P0", "HIGH", "P1" -> messages.get("export.personal.default_suggestion.high")
            "MODERATE", "MEDIUM", "ATTENTION", "P2" -> messages.get("export.personal.default_suggestion.medium")
            "LOW", "NORMAL" -> messages.get("export.personal.default_suggestion.low")
            else -> messages.get("export.personal.pending_professional_review")
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
        stream.showText(personalReportTitle(report))
        stream.endText()

        cursorY -= 28f
        val sections = listOf(
            messages.get("export.personal.section.basic"),
            messages.get("export.personal.report_id", report.reportId),
            messages.get("export.personal.result_id", report.resultId),
            messages.get("export.personal.generated_at", generatedAt),
            messages.get("export.personal.purpose"),
            "",
            messages.get("export.personal.section.overall"),
            messages.get("export.personal.total_score", report.totalScore),
            messages.get("export.personal.risk_level", report.riskLevel),
            report.standardScore?.let { messages.get("export.personal.standard_score", report.scoreSource, it) },
            report.zScore?.let { messages.get("export.personal.z_score", it) },
            report.tScore?.let { messages.get("export.personal.t_score", it) },
            report.normCode?.takeIf { it.isNotBlank() }?.let { messages.get("export.personal.norm", it) },
            "",
            messages.get("export.personal.section.content")
        ).filterNotNull()

        val trailingSections = listOf(
            "",
            messages.get("export.personal.section.notice"),
            messages.get("export.personal.notice")
        )

        val allSections = sections + report.content + trailingSections

        for (section in allSections) {
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
            val model = buildPersonalReportModel(report, generatedAt)
            appendLine(personalReportTitle(report))
            appendLine()
            appendLine(messages.get("export.personal.section.basic"))
            appendLine(messages.get("export.personal.respondent_name", model.respondentName))
            appendLine(messages.get("export.personal.assessment_date", model.assessmentDate))
            appendLine(messages.get("export.personal.purpose"))
            appendLine()
            appendLine(messages.get("export.personal.section.overall"))
            model.overallRows.forEach {
                appendLine("${it.metric}\t${it.value}\t${it.referenceRange}\t${it.interpretation}")
            }
            appendLine()
            appendLine(messages.get("export.personal.section.dimensions"))
            model.dimensionRows.forEach {
                appendLine("${it.dimensionName}\t${it.score}\t${it.referenceRange}\t${it.description}")
            }
            appendLine()
            appendLine(messages.get("export.personal.section.content"))
            appendLine(messages.get("export.personal.result_description"))
            appendLine(model.resultDescription)
            appendLine(messages.get("export.personal.psychological_suggestion"))
            appendLine(model.suggestion)
            appendLine()
            appendLine(messages.get("export.personal.section.notice"))
            appendLine(messages.get("export.personal.notice"))
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

    private fun formatDecimal(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    private fun personalReportTitle(report: ReportDetail): String {
        val scaleName = report.scaleName?.takeIf { it.isNotBlank() } ?: messages.get("export.personal.default_scale_name")
        return messages.get("export.personal.dynamic_title", scaleName)
    }

    private fun XWPFDocument.addHeading(text: String, fontSize: Int, alignment: ParagraphAlignment = ParagraphAlignment.LEFT) {
        val paragraph = createParagraph()
        paragraph.alignment = alignment
        paragraph.spacingAfter = 160
        val run = paragraph.createRun()
        run.isBold = true
        run.fontSize = fontSize
        run.setText(text)
    }

    private fun XWPFDocument.addText(text: String): XWPFParagraph {
        val paragraph = createParagraph()
        paragraph.spacingAfter = 100
        val run = paragraph.createRun()
        run.fontSize = 11
        run.setText(text)
        return paragraph
    }

    private fun XWPFDocument.addTable(headers: List<String>, rows: List<List<String>>) {
        val table = createTable(rows.size + 1, headers.size)
        headers.forEachIndexed { index, header ->
            val run = table.getRow(0).getCell(index).paragraphs.first().createRun()
            run.isBold = true
            run.setText(header)
        }
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { cellIndex, value ->
                table.getRow(rowIndex + 1).getCell(cellIndex).setText(value)
            }
        }
    }

    private data class ExportPayload(
        val content: String,
        val contentType: String,
        val contentEncoding: String
    )

    private data class PersonalReportModel(
        val respondentName: String,
        val assessmentDate: String,
        val overallRows: List<PersonalOverallRow>,
        val dimensionRows: List<PersonalDimensionRow>,
        val resultDescription: String,
        val suggestion: String
    )

    private data class PersonalOverallRow(
        val metric: String,
        val value: String,
        val referenceRange: String,
        val interpretation: String
    )

    private data class PersonalDimensionRow(
        val dimensionName: String,
        val score: String,
        val referenceRange: String,
        val description: String
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
        val desensitized: Boolean,
        val bytes: ByteArray
    )
}
