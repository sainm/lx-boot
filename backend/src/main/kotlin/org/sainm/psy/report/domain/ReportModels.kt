package org.sainm.psy.report.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReportDetail(
    val reportId: Long,
    val resultId: Long,
    @get:JsonIgnore
    val userId: Long?,
    val reportType: String,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val content: String,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false,
    val highRiskRuleCode: String? = null
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
    val createdAt: LocalDateTime,
    val scoreSource: String = "RAW_SCORE",
    val standardScore: BigDecimal? = null,
    val zScore: BigDecimal? = null,
    val tScore: BigDecimal? = null,
    val normCode: String? = null,
    val highRiskFlag: Boolean = false
)
