package org.sainm.psy.statistics.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.statistics.api.GroupReportListQuery
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupDimensionStat
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.repository.StatisticsRepository
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class StatisticsService(
    private val statisticsRepository: StatisticsRepository,
    private val messages: LocalizedMessages,
    private val metricPolicy: StatisticsMetricPolicy
) {

    fun dashboard(): DashboardStatisticsResponse =
        statisticsRepository.loadDashboard()

    fun groupReports(query: GroupReportListQuery): PageResponse<GroupReportSummary> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        val (list, total) = statisticsRepository.findGroupReportPage(query)
        val enriched = list.map { summary ->
            val compareUserResult = summary.compareUserResult?.let { comparison ->
                comparison.copy(
                    userId = query.compareUserId ?: comparison.userId,
                    scoreGapToAverage = metricPolicy.scoreGapToAverage(comparison.totalScore, summary.averageScore)
                )
            }
            summary.copy(
                compareUserResult = compareUserResult,
                dimensionStats = statisticsRepository.findDimensionStats(summary.taskId, summary.groupId)
            )
        }
        return PageResponse(list = enriched, page = query.page, size = query.size, total = total)
    }

    fun exportGroupReportsPdf(query: GroupReportListQuery): GroupReportExportArtifact {
        val exportQuery = query.copy(
            page = query.page.coerceAtLeast(1),
            size = query.size.coerceIn(1, 200)
        )
        val page = groupReports(exportQuery)
        val generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val bytes = buildGroupReportsPdf(page, exportQuery, generatedAt)
        return GroupReportExportArtifact(
            fileName = "psy-group-report-$generatedAt.pdf",
            contentType = "application/pdf",
            bytes = bytes
        )
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
                draw(messages.get("statistics.group_export.title"), size = 18f, gapAfter = 10f)
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
                        draw(messages.get("statistics.group_export.dimensions", formatDimensions(summary.dimensionStats)))
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

    private fun formatDecimal(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

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
}

data class GroupReportExportArtifact(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)
