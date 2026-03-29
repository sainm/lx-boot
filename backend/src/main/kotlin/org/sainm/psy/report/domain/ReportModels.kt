package org.sainm.psy.report.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class ReportDetail(
    val reportId: Long,
    val resultId: Long,
    val reportType: String,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val content: String
)

data class MyReportSummary(
    val reportId: Long,
    val resultId: Long,
    val taskId: Long,
    val taskName: String,
    val scaleName: String,
    val reportType: String,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val createdAt: LocalDateTime
)
