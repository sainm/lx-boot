package org.sainm.psy.report.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.sainm.psy.common.jdbc.addIfNotNull
import org.sainm.psy.common.jdbc.params
import org.sainm.psy.common.jdbc.whereClause
import org.sainm.psy.common.i18n.SupportedContentLocale
import org.sainm.psy.report.domain.MyReportSummary
import org.sainm.psy.report.domain.ReportAnswerDetail
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.domain.ReportDimensionResult
import org.sainm.psy.report.domain.ReportMetric
import org.sainm.psy.report.domain.ReportSearchQuery
import org.sainm.psy.report.domain.StaffReportSummary
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class ReportRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    fun findDetailById(reportId: Long): ReportDetail? {
        return findDetailBySql(
            """
                select
                    r.id as report_id,
                    r.result_id,
                    sh.id as answer_sheet_id,
                    sh.scale_id,
                    sh.user_id,
                    sh.tenant_id,
                    u.username,
                    u.display_name,
                    coalesce(st.scale_name, s.scale_name) as scale_name,
                    s.scale_code,
                    s.version_no as scale_version_no,
                    s.report_template,
                    r.report_type,
                    r.locale_code,
                    r.created_at,
                    ar.total_score,
                    ar.risk_level,
                    ar.score_source,
                    ar.standard_score,
                    ar.z_score,
                    ar.t_score,
                    ar.norm_code,
                    ar.high_risk_flag,
                    ar.high_risk_rule_code,
                    ar.calculation_version,
                    ar.scale_content_hash,
                    ar.scoring_engine_version,
                    ar.scoring_trace_json,
                    r.report_content
                from psy_report r
                join psy_assessment_result ar on ar.id = r.result_id
                join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
                join sys_user u on u.id = sh.user_id
                join psy_scale s on s.id = sh.scale_id
                left join psy_scale_translation st
                  on st.scale_id = s.id
                 and st.locale_code = :localeCode
                 and st.review_status = 'APPROVED'
                where r.id = :id
                order by r.id desc
                limit 1
            """.trimIndent(),
            mapOf("id" to reportId, "localeCode" to SupportedContentLocale.currentCode())
        )
    }

    fun findDetailByResultId(resultId: Long): ReportDetail? {
        return findDetailBySql(
            """
                select
                    r.id as report_id,
                    r.result_id,
                    sh.id as answer_sheet_id,
                    sh.scale_id,
                    sh.user_id,
                    sh.tenant_id,
                    u.username,
                    u.display_name,
                    coalesce(st.scale_name, s.scale_name) as scale_name,
                    s.scale_code,
                    s.version_no as scale_version_no,
                    s.report_template,
                    r.report_type,
                    r.locale_code,
                    r.created_at,
                    ar.total_score,
                    ar.risk_level,
                    ar.score_source,
                    ar.standard_score,
                    ar.z_score,
                    ar.t_score,
                    ar.norm_code,
                    ar.high_risk_flag,
                    ar.high_risk_rule_code,
                    ar.calculation_version,
                    ar.scale_content_hash,
                    ar.scoring_engine_version,
                    ar.scoring_trace_json,
                    r.report_content
                from psy_report r
                join psy_assessment_result ar on ar.id = r.result_id
                join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
                join sys_user u on u.id = sh.user_id
                join psy_scale s on s.id = sh.scale_id
                left join psy_scale_translation st
                  on st.scale_id = s.id
                 and st.locale_code = :localeCode
                 and st.review_status = 'APPROVED'
                where r.result_id = :resultId
                order by r.id desc
                limit 1
            """.trimIndent(),
            mapOf("resultId" to resultId, "localeCode" to SupportedContentLocale.currentCode())
        )
    }

    fun findMyReports(userId: Long): List<MyReportSummary> {
        return findReportsByUserId(userId)
    }

    fun findReportsByUserId(userId: Long, tenantId: Long? = null): List<MyReportSummary> {
        val localeCode = SupportedContentLocale.currentCode()
        val sql = """
            select
                r.id as report_id,
                r.result_id,
                sh.task_id,
                t.task_name,
                coalesce(st.scale_name, s.scale_name) as scale_name,
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
            left join psy_scale_translation st
              on st.scale_id = s.id
             and st.locale_code = :localeCode
             and st.review_status = 'APPROVED'
            where sh.user_id = :userId
              ${if (tenantId == null) "" else "and sh.tenant_id = :tenantId"}
            order by r.created_at desc, r.id desc
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("userId" to userId, "tenantId" to tenantId, "localeCode" to localeCode)
        ) { rs, _ ->
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

    fun searchReports(query: ReportSearchQuery, tenantId: Long? = null): List<StaffReportSummary> {
        val page = query.page.coerceAtLeast(1)
        val size = query.size.coerceIn(1, 100)
        val offset = (page - 1) * size
        val where = reportSearchWhere(query, tenantId)
        val sql = """
            select
                r.id as report_id,
                r.result_id,
                sh.user_id,
                u.username,
                u.display_name,
                u.group_id,
                g.group_name,
                sh.task_id,
                t.task_name,
                sh.scale_id,
                coalesce(st.scale_name, s.scale_name) as scale_name,
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
            left join psy_scale_translation st
              on st.scale_id = s.id
             and st.locale_code = :localeCode
             and st.review_status = 'APPROVED'
            join sys_user u on u.id = sh.user_id
            left join sys_group g on g.id = u.group_id
            $where
            order by r.created_at desc, r.id desc
            limit :limit offset :offset
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            reportSearchParams(query, tenantId)
                .addValue("limit", size)
                .addValue("offset", offset)
                .addValue("localeCode", SupportedContentLocale.currentCode())
        ) { rs, _ -> rs.toStaffReportSummary() }
    }

    fun countSearchReports(query: ReportSearchQuery, tenantId: Long? = null): Long {
        val sql = """
            select count(*)
            from psy_report r
            join psy_assessment_result ar on ar.id = r.result_id
            join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
            join sys_user u on u.id = sh.user_id
            ${reportSearchWhere(query, tenantId)}
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, reportSearchParams(query, tenantId), Long::class.java) ?: 0L
    }

    fun createSystemReportVersion(resultId: Long, authorUserId: Long, title: String, content: String): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_report (
                result_id, report_type, author_user_id, report_title, report_content,
                locale_code, version_no, created_at, updated_at
            )
            select :resultId, 'SYSTEM', :authorUserId, :reportTitle, :reportContent,
                coalesce(
                    (select locale_code from psy_report where result_id = :resultId order by version_no desc, id desc limit 1),
                    sh.response_locale_code
                ),
                (
                    select coalesce(max(version_no), 0) + 1
                    from psy_report
                    where result_id = :resultId
                ),
                :createdAt,
                :updatedAt
            from psy_assessment_result ar
            join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
            where ar.id = :resultId
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
                scaleId = rs.getLong("scale_id"),
                userId = rs.getLong("user_id").let { if (rs.wasNull()) null else it },
                tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong(),
                answerSheetId = rs.getLong("answer_sheet_id"),
                username = rs.getString("username"),
                displayName = rs.getString("display_name"),
                scaleName = rs.getString("scale_name"),
                scaleCode = rs.getString("scale_code"),
                scaleVersionNo = rs.getString("scale_version_no"),
                reportTemplate = rs.getString("report_template"),
                createdAt = rs.getTimestamp("created_at")?.toLocalDateTime(),
                reportType = rs.getString("report_type"),
                totalScore = rs.getBigDecimal("total_score"),
                riskLevel = rs.getString("risk_level"),
                content = rs.getString("report_content"),
                localeCode = rs.getString("locale_code"),
                scoreSource = rs.getString("score_source"),
                standardScore = rs.getBigDecimal("standard_score"),
                zScore = rs.getBigDecimal("z_score"),
                tScore = rs.getBigDecimal("t_score"),
                normCode = rs.getString("norm_code"),
                highRiskFlag = rs.getBoolean("high_risk_flag"),
                highRiskRuleCode = rs.getString("high_risk_rule_code"),
                calculationVersion = rs.getInt("calculation_version").let { if (rs.wasNull()) null else it },
                scaleContentHash = rs.getString("scale_content_hash"),
                scoringEngineVersion = rs.getString("scoring_engine_version"),
                scoringTraceJson = rs.getString("scoring_trace_json")
            )
        }.firstOrNull()
        if (detail == null) return null
        val metrics = buildMetrics(detail)
        return detail.copy(
            answerDetails = detail.answerSheetId?.let(::findAnswerDetails).orEmpty(),
            metrics = metrics,
            dimensionResults = findDimensionResults(detail.resultId)
        )
    }

    private fun buildMetrics(detail: ReportDetail): List<ReportMetric> = buildList {
        fun addMetric(code: String, value: java.math.BigDecimal?) {
            if (value == null) return
            add(
                ReportMetric(
                    code = code,
                    rawValue = value,
                    displayValue = value.stripTrailingZeros().toPlainString()
                )
            )
        }
        addMetric("TOTAL_SCORE", detail.totalScore)
        addMetric("STANDARD_SCORE", detail.standardScore)
        addMetric("Z_SCORE", detail.zScore)
        addMetric("T_SCORE", detail.tScore)
        val trace = detail.scoringTraceJson?.let { json ->
            runCatching { objectMapper.readTree(json) }.getOrNull()
        }
        trace?.path("derivedMetrics")?.takeIf { it.isObject }?.fields()?.forEach { (code, value) ->
            if (value.isNumber) {
                add(
                    ReportMetric(
                        code = code,
                        rawValue = value.decimalValue(),
                        displayValue = value.decimalValue().stripTrailingZeros().toPlainString(),
                        reviewStatus = "PENDING_PROFESSIONAL_REVIEW"
                    )
                )
            }
        }
    }

    private fun findDimensionResults(resultId: Long): List<ReportDimensionResult> {
        val localeCode = SupportedContentLocale.currentCode()
        val sql = """
            select d.dimension_code,
                   coalesce(dt.dimension_name, d.dimension_name) as dimension_name,
                   rd.dimension_score,
                   rd.risk_level, rd.result_title
            from psy_assessment_result_dimension rd
            join psy_scale_dimension d on d.id = rd.dimension_id
            left join psy_scale_dimension_translation dt
              on dt.dimension_id = d.id
             and dt.locale_code = :localeCode
             and dt.review_status = 'APPROVED'
            where rd.result_id = :resultId
            order by d.sort_no asc, d.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("resultId" to resultId, "localeCode" to localeCode)) { rs, _ ->
            ReportDimensionResult(
                dimensionCode = rs.getString("dimension_code"),
                dimensionName = rs.getString("dimension_name"),
                score = rs.getBigDecimal("dimension_score"),
                riskLevel = rs.getString("risk_level"),
                resultTitle = rs.getString("result_title")
            )
        }
    }

    private fun findAnswerDetails(answerSheetId: Long): List<ReportAnswerDetail> {
        val localeCode = SupportedContentLocale.currentCode()
        val sql = """
            select
                q.id as question_id,
                q.question_no,
                coalesce(qt.question_title, q.question_title) as question_title,
                q.question_type,
                d.dimension_code,
                coalesce(dt.dimension_name, d.dimension_name) as dimension_name,
                o.option_code,
                coalesce(ot.option_label, o.option_label) as option_label,
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
            left join psy_scale_question_translation qt
              on qt.question_id = q.id
             and qt.locale_code = :localeCode
             and qt.review_status = 'APPROVED'
            left join psy_scale_dimension d on d.id = q.dimension_id
            left join psy_scale_dimension_translation dt
              on dt.dimension_id = d.id
             and dt.locale_code = :localeCode
             and dt.review_status = 'APPROVED'
            left join psy_scale_option o on o.id = ai.option_id
            left join psy_scale_option_translation ot
              on ot.option_id = o.id
             and ot.locale_code = :localeCode
             and ot.review_status = 'APPROVED'
            where ai.answer_sheet_id = :answerSheetId
            order by q.sort_no asc, q.question_no asc, ai.id asc
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("answerSheetId" to answerSheetId, "localeCode" to localeCode)
        ) { rs, _ ->
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

    private fun reportSearchWhere(query: ReportSearchQuery, tenantId: Long?): String =
        whereClause(
            query.userId?.let { "sh.user_id = :userId" },
            query.groupId?.let { "u.group_id = :groupId" },
            query.scaleId?.let { "sh.scale_id = :scaleId" },
            query.taskId?.let { "sh.task_id = :taskId" },
            tenantId?.let { "sh.tenant_id = :tenantId" }
        )

    private fun reportSearchParams(query: ReportSearchQuery, tenantId: Long?): MapSqlParameterSource =
        params {
            addIfNotNull("userId", query.userId)
            addIfNotNull("groupId", query.groupId)
            addIfNotNull("scaleId", query.scaleId)
            addIfNotNull("taskId", query.taskId)
            addIfNotNull("tenantId", tenantId)
        }

    private fun java.sql.ResultSet.toStaffReportSummary() = StaffReportSummary(
        reportId = getLong("report_id"),
        resultId = getLong("result_id"),
        userId = getLong("user_id"),
        username = getString("username"),
        displayName = getString("display_name"),
        groupId = getLong("group_id").let { if (wasNull()) null else it },
        groupName = getString("group_name"),
        taskId = getLong("task_id"),
        taskName = getString("task_name"),
        scaleId = getLong("scale_id"),
        scaleName = getString("scale_name"),
        reportType = getString("report_type"),
        totalScore = getBigDecimal("total_score"),
        riskLevel = getString("risk_level"),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        scoreSource = getString("score_source"),
        standardScore = getBigDecimal("standard_score"),
        zScore = getBigDecimal("z_score"),
        tScore = getBigDecimal("t_score"),
        normCode = getString("norm_code"),
        highRiskFlag = getBoolean("high_risk_flag")
    )
}
