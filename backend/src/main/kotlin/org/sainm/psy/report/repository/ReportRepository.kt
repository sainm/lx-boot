package org.sainm.psy.report.repository

import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportDetail
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ReportRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findDetailById(reportId: Long): ReportDetail? {
        return findDetailBySql(
            """
                select r.id as report_id, r.result_id, r.report_type, ar.total_score, ar.risk_level, r.report_content
                from psy_report r
                join psy_assessment_result ar on ar.id = r.result_id
                where r.id = :id
                order by r.id desc
                limit 1
            """.trimIndent(),
            mapOf("id" to reportId)
        )
    }

    fun findDetailByResultId(resultId: Long): ReportDetail? {
        return findDetailBySql(
            """
                select r.id as report_id, r.result_id, r.report_type, ar.total_score, ar.risk_level, r.report_content
                from psy_report r
                join psy_assessment_result ar on ar.id = r.result_id
                where r.result_id = :resultId
                order by r.id desc
                limit 1
            """.trimIndent(),
            mapOf("resultId" to resultId)
        )
    }

    fun findMyReports(userId: Long): List<MyReportSummary> {
        val sql = """
            select
                r.id as report_id,
                r.result_id,
                sh.task_id,
                t.task_name,
                s.scale_name,
                r.report_type,
                ar.total_score,
                ar.risk_level,
                r.created_at
            from psy_report r
            join psy_assessment_result ar on ar.id = r.result_id
            join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
            join psy_assessment_task t on t.id = sh.task_id
            join psy_scale s on s.id = sh.scale_id
            where sh.user_id = :userId
            order by r.created_at desc, r.id desc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("userId" to userId)) { rs, _ ->
            MyReportSummary(
                reportId = rs.getLong("report_id"),
                resultId = rs.getLong("result_id"),
                taskId = rs.getLong("task_id"),
                taskName = rs.getString("task_name"),
                scaleName = rs.getString("scale_name"),
                reportType = rs.getString("report_type"),
                totalScore = rs.getBigDecimal("total_score"),
                riskLevel = rs.getString("risk_level"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }
    }

    private fun findDetailBySql(sql: String, params: Map<String, Any>): ReportDetail? =
        jdbcTemplate.query(sql, params) { rs, _ ->
            ReportDetail(
                reportId = rs.getLong("report_id"),
                resultId = rs.getLong("result_id"),
                reportType = rs.getString("report_type"),
                totalScore = rs.getBigDecimal("total_score"),
                riskLevel = rs.getString("risk_level"),
                content = rs.getString("report_content")
            )
        }.firstOrNull()
}
