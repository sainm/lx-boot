package org.sainm.psy.statistics.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.sainm.psy.common.exception.BizException
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.statistics.api.GroupReportListQuery
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupDimensionStat
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.repository.StatisticsRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

@Service
class StatisticsService(
    private val statisticsRepository: StatisticsRepository,
    private val messages: LocalizedMessages,
    private val metricPolicy: StatisticsMetricPolicy,
    private val visualizationService: VisualizationService,
    private val currentUserFacade: CurrentUserFacade,
    @Value("\${psy.statistics.anonymous-min-sample-size:5}")
    private val anonymousMinSampleSize: Int = 5
) {

    fun dashboard(): DashboardStatisticsResponse {
        val currentUser = currentUserFacade.requireCurrentUser()
        return statisticsRepository.loadDashboard(currentUser.scopedTenantId())
    }

    fun groupReports(query: GroupReportListQuery): PageResponse<GroupReportSummary> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return groupReportsScoped(query, currentUser.scopedTenantId())
    }

    private fun groupReportsScoped(query: GroupReportListQuery, tenantId: Long?): PageResponse<GroupReportSummary> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        require(query.startDate == null || query.endDate == null || !query.startDate.isAfter(query.endDate)) {
            messages.get("validation.date_range")
        }
        if (query.compareUserId != null && !statisticsRepository.isUserInTenant(query.compareUserId, tenantId)) {
            throw BizException("STATISTICS_COMPARE_USER_OUT_OF_SCOPE", messages.get("error.statistics_compare_user_out_of_scope"))
        }
        val (list, total) = statisticsRepository.findGroupReportPage(query, tenantId)
        val enriched = list.map { summary ->
            if (summary.anonymousFlag && summary.submittedCount < anonymousMinSampleSize.coerceAtLeast(2)) {
                return@map summary.copy(
                    suppressedFlag = true,
                    averageScore = null,
                    highRiskCount = 0,
                    warningCount = 0,
                    riskDistribution = emptyList(),
                    compareUserResult = null,
                    dimensionStats = emptyList(),
                    visualizations = emptyList()
                )
            }
            val compareUserResult = summary.compareUserResult?.let { comparison ->
                comparison.copy(
                    userId = query.compareUserId ?: comparison.userId,
                    scoreGapToAverage = metricPolicy.scoreGapToAverage(comparison.totalScore, summary.averageScore)
                )
            }
            val withDimensions = summary.copy(
                compareUserResult = compareUserResult,
                dimensionStats = statisticsRepository.findDimensionStats(summary.taskId, summary.groupId, tenantId)
            )
            withDimensions.copy(
                visualizations = runCatching { visualizationService.buildGroupVisualizations(withDimensions) }.getOrNull().orEmpty()
            )
        }
        return PageResponse(list = enriched, page = query.page, size = query.size, total = total)
    }

    private fun UserPrincipal.isGlobalAdmin(): Boolean =
        tenantId == null && roles.any { it in GLOBAL_ADMIN_ROLES }

    private fun UserPrincipal.scopedTenantId(): Long? {
        if (isGlobalAdmin()) return null
        return tenantId ?: throw BizException("STATISTICS_TENANT_REQUIRED", messages.get("error.statistics_tenant_required"))
    }

    fun exportGroupReportsPdf(query: GroupReportListQuery): GroupReportExportArtifact {
        return exportGroupReports(query, "PDF")
    }

    fun validateGroupExportQuery(query: GroupReportListQuery, format: String) {
        validateGroupExportStructure(query, format)
        if (groupReports(query.copy(page = 1, size = 1)).list.isEmpty()) {
            throw BizException("GROUP_REPORT_NOT_FOUND", messages.get("error.group_report_not_found"))
        }
    }

    private fun validateGroupExportStructure(query: GroupReportListQuery, format: String) {
        if (query.taskId == null || query.groupId == null) {
            throw BizException("GROUP_REPORT_EXPORT_SCOPE_REQUIRED", messages.get("statistics.group_export.scope_required"))
        }
        val normalizedFormat = format.trim().uppercase()
        if (normalizedFormat !in setOf("PDF", "WORD", "DOCX", "EXCEL", "XLSX", "CSV")) {
            throw BizException("EXPORT_FORMAT_INVALID", messages.get("export.format_invalid", format))
        }
    }

    fun exportGroupReports(query: GroupReportListQuery, format: String): GroupReportExportArtifact {
        validateGroupExportStructure(query, format)
        val currentUser = currentUserFacade.requireCurrentUser()
        return buildGroupReportExport(query, format, currentUser.scopedTenantId())
    }

    fun exportGroupReportsForTenant(query: GroupReportListQuery, format: String, tenantId: Long?): GroupReportExportArtifact {
        validateGroupExportStructure(query, format)
        return buildGroupReportExport(query, format, tenantId)
    }

    private fun buildGroupReportExport(query: GroupReportListQuery, format: String, tenantId: Long?): GroupReportExportArtifact {
        val exportQuery = query.copy(
            page = query.page.coerceAtLeast(1),
            size = query.size.coerceIn(1, 200)
        )
        val page = groupReportsScoped(exportQuery, tenantId)
        if (page.list.isEmpty()) {
            throw BizException("GROUP_REPORT_NOT_FOUND", messages.get("error.group_report_not_found"))
        }
        val generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        return when (format.trim().uppercase().ifBlank { "PDF" }) {
            "PDF" -> GroupReportExportArtifact(
                fileName = buildExportFileName(page.list, generatedAt, "pdf"),
                contentType = "application/pdf",
                bytes = buildGroupReportsPdf(page, exportQuery, generatedAt)
            )
            "WORD", "DOCX" -> GroupReportExportArtifact(
                fileName = buildExportFileName(page.list, generatedAt, "docx"),
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes = buildGroupReportsWord(page, exportQuery, generatedAt)
            )
            "EXCEL", "XLSX" -> GroupReportExportArtifact(
                fileName = buildExportFileName(page.list, generatedAt, "xlsx"),
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes = buildGroupReportsExcel(page.list)
            )
            "CSV" -> GroupReportExportArtifact(
                fileName = buildExportFileName(page.list, generatedAt, "csv"),
                contentType = "text/csv;charset=UTF-8",
                bytes = buildGroupReportsCsv(page.list)
            )
            else -> throw BizException("EXPORT_FORMAT_INVALID", messages.get("export.format_invalid", format))
        }
    }

    private fun buildGroupReportsExcel(summaries: List<GroupReportSummary>): ByteArray {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(messages.get("statistics.group_export.sheet_name").take(31))
            val headers = groupExportHeaders()
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header -> headerRow.createCell(index).setCellValue(header) }
            groupExportRows(summaries).forEachIndexed { rowIndex, values ->
                val row = sheet.createRow(rowIndex + 1)
                values.forEachIndexed { columnIndex, value ->
                    val cell = row.createCell(columnIndex)
                    when (value) {
                        is BigDecimal -> cell.setCellValue(value.toDouble())
                        is Number -> cell.setCellValue(value.toDouble())
                        null -> cell.setBlank()
                        else -> cell.setCellValue(value.toString())
                    }
                }
            }
            headers.indices.forEach(sheet::autoSizeColumn)
            return ByteArrayOutputStream().use { output ->
                workbook.write(output)
                output.toByteArray()
            }
        }
    }

    private fun buildGroupReportsCsv(summaries: List<GroupReportSummary>): ByteArray {
        val rows = sequenceOf(groupExportHeaders()) + groupExportRows(summaries).map { row -> row.map { it?.toString().orEmpty() } }
        val csv = rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::escapeCsv) }
        return ("\uFEFF$csv\r\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun groupExportHeaders(): List<String> = listOf(
        messages.get("statistics.group_export.column.task_id"),
        messages.get("statistics.group_export.column.task_name"),
        messages.get("statistics.group_export.column.scale"),
        messages.get("statistics.group_export.column.group"),
        messages.get("statistics.group_export.column.members"),
        messages.get("statistics.group_export.column.submitted"),
        messages.get("statistics.group_export.column.completion"),
        messages.get("statistics.group_export.column.average"),
        messages.get("statistics.group_export.column.high_risk"),
        messages.get("statistics.group_export.column.warnings"),
        messages.get("statistics.group_export.column.latest")
    )

    private fun groupExportRows(summaries: List<GroupReportSummary>): List<List<Any?>> = summaries.map { summary ->
        listOf(
            summary.taskId,
            summary.taskName,
            summary.scaleName,
            summary.groupName,
            summary.memberCount,
            summary.submittedCount,
            summary.completionRate,
            summary.averageScore.takeUnless { summary.suppressedFlag },
            summary.highRiskCount.takeUnless { summary.suppressedFlag },
            summary.warningCount.takeUnless { summary.suppressedFlag },
            summary.latestSubmittedAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun buildGroupReportsPdf(
        page: PageResponse<GroupReportSummary>,
        query: GroupReportListQuery,
        generatedAt: String
    ): ByteArray {
        val document = PDDocument()
        return document.use { pdf ->
            val font = loadCjkFont(pdf)
            val box = PDRectangle.A4
            val marginLeft = 48f
            val marginTop = 48f
            val marginBottom = 48f
            val contentWidth = box.width - marginLeft * 2
            val bodySize = 10.5f
            val lineGap = 15f
            var cursorY = box.height - marginTop
            var stream: PDPageContentStream? = null

            fun openPage() {
                val nextPage = PDPage(box)
                pdf.addPage(nextPage)
                stream = PDPageContentStream(pdf, nextPage, PDPageContentStream.AppendMode.APPEND, true, true)
                cursorY = box.height - marginTop
            }

            fun closePage() {
                stream?.close()
                stream = null
            }

            fun draw(text: String, size: Float = bodySize, gapAfter: Float = 0f) {
                wrapText(text, font, size, contentWidth).forEach { line ->
                    if (cursorY <= marginBottom) {
                        closePage()
                        openPage()
                    }
                    val currentStream = stream ?: error("PDF content stream is not open")
                    currentStream.beginText()
                    currentStream.setFont(font, size)
                    currentStream.newLineAtOffset(marginLeft, cursorY)
                    currentStream.showText(line)
                    currentStream.endText()
                    cursorY -= lineGap
                }
                cursorY -= gapAfter
            }

            openPage()
            try {
                draw(groupReportTitle(page.list), size = 18f, gapAfter = 10f)
                draw(messages.get("statistics.group_export.generated_at", generatedAt))
                draw(
                    messages.get(
                        "statistics.group_export.filters",
                        query.taskId ?: "-",
                        query.groupId ?: "-",
                        query.scaleId ?: "-",
                        query.compareUserId ?: "-"
                    )
                )
                draw(messages.get("statistics.group_export.page", page.page, page.size, page.total), gapAfter = 8f)

                if (page.list.isEmpty()) {
                    draw(messages.get("statistics.group_export.empty"))
                } else {
                    page.list.forEachIndexed { index, summary ->
                        draw(
                            messages.get(
                                "statistics.group_export.item_title",
                                index + 1,
                                summary.taskName,
                                summary.groupName
                            ),
                            size = 12.5f
                        )
                        draw(messages.get("statistics.group_export.scale", summary.scaleName))
                        draw(messages.get("statistics.group_export.object_group", summary.groupName))
                        if (summary.suppressedFlag) {
                            draw(messages.get("statistics.group_export.suppressed"), gapAfter = 8f)
                            return@forEachIndexed
                        }
                        draw(
                            messages.get(
                                "statistics.group_export.metrics",
                                summary.memberCount,
                                summary.submittedCount,
                                formatDecimal(summary.completionRate),
                                summary.averageScore?.let(::formatDecimal) ?: "-",
                                summary.highRiskCount,
                                summary.warningCount
                            )
                        )
                        draw(
                            messages.get(
                                "statistics.group_export.latest",
                                summary.latestSubmittedAt?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "-"
                            )
                        )
                        draw(
                            messages.get(
                                "statistics.group_export.risk_distribution",
                                summary.riskDistribution.joinToString(", ") { "${it.key}: ${it.value}" }.ifBlank { "-" }
                            )
                        )
                        draw(messages.get("statistics.group_export.section.dimensions"), size = 12.5f)
                        draw(messages.get("statistics.group_export.dimensions", formatDimensions(summary.dimensionStats)))
                        draw(messages.get("statistics.group_export.prominent", prominentDimensions(summary.dimensionStats)))
                        summary.compareUserResult?.let { comparison ->
                            draw(
                                messages.get(
                                    "statistics.group_export.compare_user",
                                    comparison.displayName ?: messages.get("statistics.group_export.anonymous"),
                                    formatDecimal(comparison.totalScore),
                                    comparison.riskLevel,
                                    comparison.scoreGapToAverage?.let(::formatDecimal) ?: "-"
                                )
                            )
                        }
                        draw(messages.get("statistics.group_export.section.conclusion"), size = 12.5f)
                        draw(buildGroupConclusion(summary))
                        draw(messages.get("statistics.group_export.section.suggestion"), size = 12.5f)
                        draw(messages.get("statistics.group_export.suggestion"))
                        draw("", gapAfter = 2f)
                    }
                }
            } finally {
                closePage()
            }

            ByteArrayOutputStream().use { output ->
                pdf.save(output)
                output.toByteArray()
            }
        }
    }

    private fun formatDimensions(dimensions: List<GroupDimensionStat>): String =
        dimensions.joinToString("; ") {
            "${it.dimensionName}: ${formatDecimal(it.averageScore)} (${it.answerCount})"
        }.ifBlank { "-" }

    private fun buildExportFileName(summaries: List<GroupReportSummary>, generatedAt: String, extension: String): String {
        val scaleName = reportScaleName(summaries)
        val base = messages.get("statistics.group_export.dynamic_file_base", scaleName)
        return "${sanitizeFileName(base)}-$generatedAt.$extension"
    }

    private fun groupReportTitle(summaries: List<GroupReportSummary>): String =
        messages.get("statistics.group_export.dynamic_title", reportScaleName(summaries))

    private fun reportScaleName(summaries: List<GroupReportSummary>): String =
        summaries.mapNotNull { it.scaleName.takeIf(String::isNotBlank) }
            .distinct()
            .singleOrNull()
            ?: messages.get("statistics.group_export.multi_scale")

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().ifBlank { "group-report" }

    private fun buildGroupReportsWord(
        page: PageResponse<GroupReportSummary>,
        query: GroupReportListQuery,
        generatedAt: String
    ): ByteArray {
        val document = XWPFDocument()
        document.use { doc ->
            doc.addHeading(groupReportTitle(page.list), 18, ParagraphAlignment.CENTER)
            doc.addText(messages.get("statistics.group_export.generated_at", generatedAt))

            if (page.list.isEmpty()) {
                doc.addText(messages.get("statistics.group_export.empty"))
            } else {
                doc.addPicture(
                    buildStatusDistributionChart(page.list),
                    "group-status-distribution.png",
                    widthEmu = Units.toEMU(460.0),
                    heightEmu = Units.toEMU(230.0)
                )
                page.list.forEachIndexed { index, summary ->
                    doc.addHeading(messages.get("statistics.group_export.item_title", index + 1, summary.taskName, summary.groupName), 12)
                    doc.addText(messages.get("statistics.group_export.scale", summary.scaleName))
                    if (summary.suppressedFlag) {
                        doc.addText(messages.get("statistics.group_export.suppressed"))
                        return@forEachIndexed
                    }
                    doc.addTable(
                        listOf(messages.get("statistics.group_export.status"), messages.get("statistics.group_export.people_count")),
                        listOf(
                            listOf(messages.get("statistics.group_export.status.normal"), riskCount(summary, "NORMAL").toString()),
                            listOf(messages.get("statistics.group_export.status.attention"), riskCount(summary, "ATTENTION").toString()),
                            listOf(messages.get("statistics.group_export.status.high"), riskCount(summary, "HIGH").toString())
                        )
                    )
                    doc.addTable(
                        listOf(
                            messages.get("statistics.group_export.metric"),
                            messages.get("statistics.group_export.value")
                        ),
                        listOf(
                            messages.get("statistics.group_export.member_count") to summary.memberCount.toString(),
                            messages.get("statistics.group_export.submitted_count") to summary.submittedCount.toString(),
                            messages.get("statistics.group_export.completion_rate") to "${formatDecimal(summary.completionRate)}%",
                            messages.get("statistics.group_export.average_score") to (summary.averageScore?.let(::formatDecimal) ?: "-"),
                            messages.get("statistics.group_export.high_risk_count") to summary.highRiskCount.toString(),
                            messages.get("statistics.group_export.warning_count") to summary.warningCount.toString(),
                            messages.get("statistics.group_export.latest_label") to (summary.latestSubmittedAt?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "-")
                        ).map { listOf(it.first, it.second) }
                    )
                    doc.addText(messages.get("statistics.group_export.risk_distribution", summary.riskDistribution.joinToString(", ") { "${it.key}: ${it.value}" }.ifBlank { "-" }))
                    doc.addHeading(messages.get("statistics.group_export.section.dimensions"), 12)
                    doc.addText(messages.get("statistics.group_export.dimension_compare_label"))
                    doc.addPicture(
                        buildDimensionAverageChart(summary.dimensionStats),
                        "group-dimension-average-${summary.taskId}-${summary.groupId}.png",
                        widthEmu = Units.toEMU(460.0),
                        heightEmu = Units.toEMU(260.0)
                    )
                    doc.addDimensionTable(summary.dimensionStats)
                    doc.addText(messages.get("statistics.group_export.prominent", prominentDimensions(summary.dimensionStats)))
                    summary.compareUserResult?.let { comparison ->
                        doc.addText(
                            messages.get(
                                "statistics.group_export.compare_user",
                                comparison.displayName ?: messages.get("statistics.group_export.anonymous"),
                                formatDecimal(comparison.totalScore),
                                comparison.riskLevel,
                                comparison.scoreGapToAverage?.let(::formatDecimal) ?: "-"
                            )
                        )
                    }
                    doc.addHeading(messages.get("statistics.group_export.section.conclusion"), 12)
                    doc.addText(buildGroupConclusion(summary))
                    doc.addHeading(messages.get("statistics.group_export.section.suggestion"), 12)
                    doc.addText(messages.get("statistics.group_export.suggestion"))
                }
            }
            ByteArrayOutputStream().use { output ->
                doc.write(output)
                return output.toByteArray()
            }
        }
    }

    private fun prominentDimensions(dimensions: List<GroupDimensionStat>): String {
        val prominent = dimensions
            .sortedByDescending { it.averageScore }
            .take(3)
            .joinToString("; ") { "${it.dimensionName}: ${formatDecimal(it.averageScore)}" }
        return prominent.ifBlank { "-" }
    }

    private fun buildGroupConclusion(summary: GroupReportSummary): String {
        val submittedRate = formatDecimal(summary.completionRate)
        val averageScore = summary.averageScore?.let(::formatDecimal) ?: "-"
        return messages.get(
            "statistics.group_export.conclusion",
            summary.groupName,
            submittedRate,
            averageScore,
            summary.highRiskCount,
            summary.warningCount
        )
    }

    private fun riskCount(summary: GroupReportSummary, key: String): Long =
        summary.riskDistribution.firstOrNull { it.key == key }?.value ?: 0L

    private fun formatDecimal(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    private fun formatOptionalDecimal(value: BigDecimal?): String =
        value?.let(::formatDecimal) ?: "-"

    private fun screeningTime(summaries: List<GroupReportSummary>): String {
        val start = summaries.mapNotNull { it.taskStartTime }.minOrNull()
        val end = summaries.mapNotNull { it.taskEndTime }.maxOrNull()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return when {
            start != null && end != null -> messages.get(
                "statistics.group_export.screening_time_range",
                start.format(dateFormatter),
                end.format(dateFormatter)
            )
            start != null -> start.format(dateFormatter)
            end != null -> end.format(dateFormatter)
            else -> "-"
        }
    }

    private fun wrapText(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.lineSequence().forEach { paragraph ->
            if (paragraph.isBlank()) {
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
        try {
            val stream = javaClass.getResourceAsStream("/fonts/NotoSansCJK-Regular.ttf")
            if (stream != null) {
                return PDType0Font.load(document, stream)
            }
        } catch (_: Exception) {
        }

        val candidates = listOf(
            File("C:/Windows/Fonts/simhei.ttf"),
            File("C:/Windows/Fonts/msyh.ttc"),
            File("C:/Windows/Fonts/simsun.ttc"),
            File("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"),
            File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
            File("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"),
            File("/System/Library/Fonts/STHeiti Medium.ttc"),
            File("/System/Library/Fonts/Hiragino Sans GB.ttc")
        )
        for (fontFile in candidates) {
            if (!fontFile.exists()) continue
            return try {
                PDType0Font.load(document, fontFile)
            } catch (_: Exception) {
                continue
            }
        }
        return PDType1Font(Standard14Fonts.FontName.HELVETICA)
    }

    private fun XWPFDocument.addHeading(text: String, fontSize: Int, alignment: ParagraphAlignment = ParagraphAlignment.LEFT) {
        val paragraph = createParagraph()
        paragraph.alignment = alignment
        paragraph.spacingAfter = 160
        val run = paragraph.createRun()
        run.isBold = true
        run.fontFamily = "Microsoft YaHei"
        run.fontSize = fontSize
        run.setText(text)
    }

    private fun XWPFDocument.addText(text: String) {
        val paragraph = createParagraph()
        paragraph.spacingAfter = 100
        val run = paragraph.createRun()
        run.fontFamily = "Microsoft YaHei"
        run.fontSize = 11
        run.setText(text)
    }

    private fun XWPFDocument.addPicture(bytes: ByteArray, fileName: String, widthEmu: Int, heightEmu: Int) {
        val paragraph = createParagraph()
        paragraph.alignment = ParagraphAlignment.CENTER
        paragraph.spacingAfter = 180
        val run = paragraph.createRun()
        bytes.inputStream().use {
            run.addPicture(it, Document.PICTURE_TYPE_PNG, fileName, widthEmu, heightEmu)
        }
    }

    private fun XWPFDocument.addTable(headers: List<String>, rows: List<List<String>>) {
        val table = createTable(rows.size + 1, headers.size)
        table.setWidth("100%")
        headers.forEachIndexed { index, header ->
            val cell = table.getRow(0).getCell(index)
            cell.color = "F2F4F7"
            val paragraph = cell.paragraphs.first()
            val run = paragraph.createRun()
            run.isBold = true
            run.fontFamily = "Microsoft YaHei"
            run.fontSize = 10
            run.setText(header)
        }
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { cellIndex, value ->
                table.getRow(rowIndex + 1).getCell(cellIndex).setCellText(value)
            }
        }
    }

    private fun XWPFTableCell.setCellText(value: String) {
        removeParagraph(0)
        val paragraph = addParagraph()
        val run = paragraph.createRun()
        run.fontFamily = "Microsoft YaHei"
        run.fontSize = 10
        run.setText(value)
    }

    private fun XWPFDocument.addDimensionTable(dimensions: List<GroupDimensionStat>) {
        if (dimensions.isEmpty()) {
            addText(messages.get("statistics.group_export.dimensions", "-"))
            return
        }
        val overallAverage = dimensions.map { it.averageScore }.takeIf { it.isNotEmpty() }
            ?.reduce(BigDecimal::add)
            ?.divide(BigDecimal(dimensions.size), 2, java.math.RoundingMode.HALF_UP)
        addTable(
            listOf(
                messages.get("statistics.group_export.metric"),
                messages.get("statistics.group_export.average_score"),
                messages.get("statistics.group_export.standard_deviation"),
                messages.get("statistics.group_export.max_score"),
                messages.get("statistics.group_export.min_score"),
                messages.get("statistics.group_export.critical_value"),
                messages.get("statistics.group_export.exceed_count")
            ),
            listOfNotNull(
                overallAverage?.let {
                    listOf(
                        messages.get("statistics.dimension.overall"),
                        formatDecimal(it),
                        "-",
                        "-",
                        "-",
                        "2.0",
                        dimensions.sumOf { dimension -> dimension.exceedCount ?: 0L }.toString()
                    )
                }
            ) + dimensions.map {
                listOf(
                    it.dimensionName,
                    formatDecimal(it.averageScore),
                    formatOptionalDecimal(it.standardDeviation),
                    formatOptionalDecimal(it.maxScore),
                    formatOptionalDecimal(it.minScore),
                    "2.0",
                    (it.exceedCount ?: 0L).toString()
                )
            }
        )
    }

    private fun buildStatusDistributionChart(summaries: List<GroupReportSummary>): ByteArray {
        val normal = summaries.sumOf { riskCount(it, "NORMAL") }
        val attention = summaries.sumOf { riskCount(it, "ATTENTION") }
        val high = summaries.sumOf { riskCount(it, "HIGH") }
        return drawHorizontalBarChart(
            title = messages.get("statistics.group_export.chart.status_title"),
            rows = listOf(
                ChartRow(messages.get("statistics.group_export.status.normal"), normal.toBigDecimal(), Color(0x2E7D32)),
                ChartRow(messages.get("statistics.group_export.status.attention"), attention.toBigDecimal(), Color(0xF9A825)),
                ChartRow(messages.get("statistics.group_export.status.high"), high.toBigDecimal(), Color(0xC62828))
            )
        )
    }

    private fun buildDimensionAverageChart(dimensions: List<GroupDimensionStat>): ByteArray {
        val rows = dimensions
            .take(10)
            .mapIndexed { index, dimension ->
                ChartRow(
                    label = dimension.dimensionName,
                    value = dimension.averageScore,
                    color = chartPalette[index % chartPalette.size]
                )
            }
        return drawHorizontalBarChart(
            title = messages.get("statistics.group_export.chart.dimension_title"),
            rows = rows.ifEmpty { listOf(ChartRow("-", BigDecimal.ZERO, Color(0x607D8B))) },
            referenceValue = BigDecimal("2.0")
        )
    }

    private fun drawHorizontalBarChart(
        title: String,
        rows: List<ChartRow>,
        referenceValue: BigDecimal? = null
    ): ByteArray {
        val width = 980
        val height = 520
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = Color(0x1F2933)
            g.font = Font("Microsoft YaHei", Font.BOLD, 28)
            g.drawString(title, 48, 52)

            val chartX = 210
            val chartY = 90
            val chartWidth = 680
            val rowHeight = ((height - chartY - 45) / rows.size.coerceAtLeast(1)).coerceIn(34, 58)
            val maxValue = (rows.maxOfOrNull { it.value } ?: BigDecimal.ONE)
                .max(referenceValue ?: BigDecimal.ZERO)
                .max(BigDecimal.ONE)
            val maxDouble = maxValue.toDouble()

            g.font = Font("Microsoft YaHei", Font.PLAIN, 20)
            rows.forEachIndexed { index, row ->
                val y = chartY + index * rowHeight
                g.color = Color(0x374151)
                g.drawString(row.label.take(12), 48, y + 25)
                g.color = Color(0xEEF2F7)
                g.fillRoundRect(chartX, y + 5, chartWidth, 24, 8, 8)
                val barWidth = ((row.value.toDouble() / maxDouble) * chartWidth).toInt().coerceAtLeast(if (row.value > BigDecimal.ZERO) 4 else 0)
                g.color = row.color
                g.fillRoundRect(chartX, y + 5, barWidth, 24, 8, 8)
                g.color = Color(0x111827)
                g.drawString(formatDecimal(row.value), chartX + chartWidth + 18, y + 25)
            }

            referenceValue?.let {
                val refX = chartX + ((it.toDouble() / maxDouble) * chartWidth).toInt()
                g.color = Color(0xDC2626)
                g.stroke = BasicStroke(2f)
                g.drawLine(refX, chartY, refX, chartY + rows.size * rowHeight)
                g.font = Font("Microsoft YaHei", Font.PLAIN, 16)
                g.drawString(messages.get("statistics.group_export.chart.reference", formatDecimal(it)), refX + 6, chartY - 10)
            }
        } finally {
            g.dispose()
        }
        ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            return output.toByteArray()
        }
    }

    private val chartPalette = listOf(
        Color(0x2563EB),
        Color(0x059669),
        Color(0xD97706),
        Color(0x7C3AED),
        Color(0xDC2626),
        Color(0x0891B2),
        Color(0x4F46E5),
        Color(0x65A30D),
        Color(0xDB2777),
        Color(0x475569)
    )

    private data class ChartRow(
        val label: String,
        val value: BigDecimal,
        val color: Color
    )

    companion object {
        private val GLOBAL_ADMIN_ROLES = setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN")
    }
}

data class GroupReportExportArtifact(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)
