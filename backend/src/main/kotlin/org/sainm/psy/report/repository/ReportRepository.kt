package org.sainm.psy.report.repository

import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportAnswerDetail
import org.sainm.psy.report.domain.ReportDetail
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class ReportRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findDetailById(reportId: Long): ReportDetail? {
        return findDetailBySql(
            """
                select
                    r.id as report_id,
                    r.result_id,
                    sh.id as answer_sheet_id,
                    sh.user_id,
                    r.report_type,
                    ar.total_score,
                    ar.risk_level,
                    ar.score_source,
                    ar.standard_score,
                    ar.z_score,
                    ar.t_score,
                    ar.norm_code,
                    ar.high_risk_flag,
                    ar.high_risk_rule_code,
                    r.report_content
                from psy_report r
                join psy_assessment_result ar on ar.id = r.result_id
                join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
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
                select
                    r.id as report_id,
                    r.result_id,
                    sh.id as answer_sheet_id,
                    sh.user_id,
                    r.report_type,
                    ar.total_score,
                    ar.risk_level,
                    ar.score_source,
                    ar.standard_score,
                    ar.z_score,
                    ar.t_score,
                    ar.norm_code,
                    ar.high_risk_flag,
                    ar.high_risk_rule_code,
                    r.report_content
                from psy_report r
                join psy_assessment_result ar on ar.id = r.result_id
                join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
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
                ar.score_source,
                ar.standard_score,
                ar.z_score,
                ar.t_score,
                ar.norm_code,
                ar.high_risk_flag,
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
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                scoreSource = rs.getString("score_source"),
                standardScore = rs.getBigDecimal("standard_score"),
                zScore = rs.getBigDecimal("z_score"),
                tScore = rs.getBigDecimal("t_score"),
                normCode = rs.getString("norm_code"),
                highRiskFlag = rs.getBoolean("high_risk_flag")
            )
        }
    }

    fun createSystemReportVersion(resultId: Long, authorUserId: Long, title: String, content: String): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_report (
                result_id, report_type, author_user_id, report_title, report_content, version_no, created_at, updated_at
            ) values (
                :resultId,
                'SYSTEM',
                :authorUserId,
                :reportTitle,
                :reportContent,
                (
                    select coalesce(max(version_no), 0) + 1
                    from psy_report
                    where result_id = :resultId
                ),
                :createdAt,
                :updatedAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("resultId", resultId)
                .addValue("authorUserId", authorUserId)
                .addValue("reportTitle", title)
                .addValue("reportContent", content)
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to regenerate report")
    }

    private fun findDetailBySql(sql: String, params: Map<String, Any>): ReportDetail? {
        val detail = jdbcTemplate.query(sql, params) { rs, _ ->
            ReportDetail(
                reportId = rs.getLong("report_id"),
                resultId = rs.getLong("result_id"),
                userId = rs.getLong("user_id").let { if (rs.wasNull()) null else it },
                answerSheetId = rs.getLong("answer_sheet_id"),
                reportType = rs.getString("report_type"),
                totalScore = rs.getBigDecimal("total_score"),
                riskLevel = rs.getString("risk_level"),
                content = rs.getString("report_content"),
                scoreSource = rs.getString("score_source"),
                standardScore = rs.getBigDecimal("standard_score"),
                zScore = rs.getBigDecimal("z_score"),
                tScore = rs.getBigDecimal("t_score"),
                normCode = rs.getString("norm_code"),
                highRiskFlag = rs.getBoolean("high_risk_flag"),
                highRiskRuleCode = rs.getString("high_risk_rule_code")
            )
        }.firstOrNull()
        return detail?.copy(answerDetails = findAnswerDetails(detail.answerSheetId ?: return detail))
    }

    private fun findAnswerDetails(answerSheetId: Long): List<ReportAnswerDetail> {
        val sql = """
            select
                q.id as question_id,
                q.question_no,
                q.question_title,
                q.question_type,
                d.dimension_code,
                d.dimension_name,
                o.option_code,
                o.option_label,
                ai.answer_text,
                ai.answer_value,
                ai.score_value,
                q.slider_min,
                q.slider_max,
                q.slider_step,
                q.matrix_group_code,
                q.row_code,
                q.column_code,
                ai.id as answer_item_id
            from psy_assessment_answer_item ai
            join psy_scale_question q on q.id = ai.question_id
            left join psy_scale_dimension d on d.id = q.dimension_id
            left join psy_scale_option o on o.id = ai.option_id
            where ai.answer_sheet_id = :answerSheetId
            order by q.sort_no asc, q.question_no asc, ai.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("answerSheetId" to answerSheetId)) { rs, _ ->
            ReportAnswerDetail(
                questionId = rs.getLong("question_id"),
                questionNo = rs.getInt("question_no"),
                questionTitle = rs.getString("question_title"),
                questionType = rs.getString("question_type"),
                dimensionCode = rs.getString("dimension_code"),
                dimensionName = rs.getString("dimension_name"),
                optionCode = rs.getString("option_code"),
                optionLabel = rs.getString("option_label"),
                answerText = rs.getString("answer_text"),
                answerValue = rs.getBigDecimal("answer_value"),
                scoreValue = rs.getBigDecimal("score_value"),
                sliderMin = rs.getBigDecimal("slider_min"),
                sliderMax = rs.getBigDecimal("slider_max"),
                sliderStep = rs.getBigDecimal("slider_step"),
                matrixGroupCode = rs.getString("matrix_group_code"),
                rowCode = rs.getString("row_code"),
                columnCode = rs.getString("column_code")
            )
        }
    }
}
