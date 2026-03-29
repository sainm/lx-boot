package org.sainm.psy.scale.repository

import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.BatchCreateResponse
import org.sainm.psy.scale.api.ScaleListQuery
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleDimension
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleQuestion
import org.sainm.psy.scale.domain.ScaleQuestionOption
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleResultRule
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.domain.ScaleSummary
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class ScaleRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findPage(query: ScaleListQuery): Pair<List<ScaleSummary>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val params = MapSqlParameterSource()
            .addValue("scaleName", query.scaleName?.trim()?.takeIf { it.isNotEmpty() }?.let { "%$it%" })
            .addValue("status", query.status?.trim()?.takeIf { it.isNotEmpty() })
            .addValue("limit", query.size)
            .addValue("offset", offset)

        val whereClause = buildString {
            append(" where 1 = 1 ")
            if (params.hasValue("scaleName")) {
                append(" and scale_name like :scaleName ")
            }
            if (params.hasValue("status")) {
                append(" and status = :status ")
            }
        }

        val listSql = """
            select id, scale_code, scale_name, applicable_target, version_no, status,
                   score_method, score_coefficient, anonymous_supported, created_at
            from psy_scale
            $whereClause
            order by id desc
            limit :limit offset :offset
        """.trimIndent()

        val countSql = """
            select count(1)
            from psy_scale
            $whereClause
        """.trimIndent()

        val list = jdbcTemplate.query(listSql, params, scaleSummaryRowMapper)
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    fun create(request: CreateScaleRequest, createdBy: Long): Long {
        val now = LocalDateTime.now()
        val sql = """
            insert into psy_scale (
                scale_code,
                scale_name,
                description,
                applicable_target,
                version_no,
                status,
                score_method,
                score_coefficient,
                anonymous_supported,
                report_template,
                created_by,
                created_at,
                updated_by,
                updated_at
            ) values (
                :scaleCode,
                :scaleName,
                :description,
                :applicableTarget,
                :versionNo,
                :status,
                :scoreMethod,
                :scoreCoefficient,
                :anonymousSupported,
                :reportTemplate,
                :createdBy,
                :createdAt,
                :updatedBy,
                :updatedAt
            )
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("scaleCode", request.scaleCode.trim())
            .addValue("scaleName", request.scaleName.trim())
            .addValue("description", request.description)
            .addValue("applicableTarget", request.applicableTarget)
            .addValue("versionNo", request.versionNo)
            .addValue("status", "DRAFT")
            .addValue("scoreMethod", request.scoreMethod.trim().uppercase())
            .addValue("scoreCoefficient", request.scoreCoefficient)
            .addValue("anonymousSupported", request.anonymousSupported)
            .addValue("reportTemplate", request.reportTemplate)
            .addValue("createdBy", createdBy)
            .addValue("createdAt", Timestamp.valueOf(now))
            .addValue("updatedBy", createdBy)
            .addValue("updatedAt", Timestamp.valueOf(now))

        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder, arrayOf("id"))
        return keyHolder.key?.toLong() ?: error("failed to create scale")
    }

    fun existsByScaleCode(scaleCode: String): Boolean {
        val sql = "select count(1) from psy_scale where scale_code = :scaleCode"
        val params = mapOf("scaleCode" to scaleCode)
        return (jdbcTemplate.queryForObject(sql, params, Long::class.java) ?: 0L) > 0
    }

    fun existsById(id: Long): Boolean {
        val sql = "select count(1) from psy_scale where id = :id"
        return (jdbcTemplate.queryForObject(sql, mapOf("id" to id), Long::class.java) ?: 0L) > 0
    }

    fun findDimensionIdsByScaleId(scaleId: Long): Set<Long> {
        val sql = "select id from psy_scale_dimension where scale_id = :scaleId"
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ -> rs.getLong("id") }.toSet()
    }

    fun createDimensions(scaleId: Long, dimensions: List<ScaleDimensionDraft>): BatchCreateResponse {
        val createdIds = dimensions.map { dimension ->
            val sql = """
                insert into psy_scale_dimension (
                    scale_id, dimension_code, dimension_name, description, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :dimensionCode, :dimensionName, :description, :sortNo, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                sql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("dimensionCode", dimension.dimensionCode.trim())
                    .addValue("dimensionName", dimension.dimensionName.trim())
                    .addValue("description", dimension.description)
                    .addValue("sortNo", dimension.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            keyHolder.key?.toLong() ?: error("failed to create scale dimension")
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun createQuestions(scaleId: Long, questions: List<ScaleQuestionDraft>): BatchCreateResponse {
        val createdIds = mutableListOf<Long>()
        questions.forEach { question ->
            val questionSql = """
                insert into psy_scale_question (
                    scale_id, dimension_id, question_no, question_title, question_type,
                    required_flag, reverse_score_flag, weight_value, sort_no, created_at, updated_at
                ) values (
                    :scaleId, :dimensionId, :questionNo, :questionTitle, :questionType,
                    :requiredFlag, :reverseScoreFlag, :weightValue, :sortNo, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val questionKeyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                questionSql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("dimensionId", question.dimensionId)
                    .addValue("questionNo", question.questionNo)
                    .addValue("questionTitle", question.questionTitle.trim())
                    .addValue("questionType", question.questionType.trim().uppercase())
                    .addValue("requiredFlag", question.requiredFlag)
                    .addValue("reverseScoreFlag", question.reverseScoreFlag)
                    .addValue("weightValue", question.weightValue)
                    .addValue("sortNo", question.sortNo)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                questionKeyHolder,
                arrayOf("id")
            )
            val questionId = questionKeyHolder.key?.toLong() ?: error("failed to create scale question")
            createdIds.add(questionId)
            if (question.options.isNotEmpty()) {
                val optionSql = """
                    insert into psy_scale_option (
                        question_id, option_code, option_label, score_value, sort_no, created_at, updated_at
                    ) values (
                        :questionId, :optionCode, :optionLabel, :scoreValue, :sortNo, :createdAt, :updatedAt
                    )
                """.trimIndent()
                val optionNow = Timestamp.valueOf(LocalDateTime.now())
                val optionParams = question.options.map { option ->
                    MapSqlParameterSource()
                        .addValue("questionId", questionId)
                        .addValue("optionCode", option.optionCode.trim())
                        .addValue("optionLabel", option.optionLabel.trim())
                        .addValue("scoreValue", option.scoreValue)
                        .addValue("sortNo", option.sortNo)
                        .addValue("createdAt", optionNow)
                        .addValue("updatedAt", optionNow)
                }.toTypedArray()
                jdbcTemplate.batchUpdate(optionSql, optionParams)
            }
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun createResultRules(scaleId: Long, resultRules: List<ScaleResultRuleDraft>): BatchCreateResponse {
        val createdIds = resultRules.map { rule ->
            val sql = """
                insert into psy_scale_result_rule (
                    scale_id, dimension_id, risk_level, score_min, score_max,
                    result_title, result_description, suggestion_text, created_at, updated_at
                ) values (
                    :scaleId, :dimensionId, :riskLevel, :scoreMin, :scoreMax,
                    :resultTitle, :resultDescription, :suggestionText, :createdAt, :updatedAt
                )
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update(
                sql,
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("dimensionId", rule.dimensionId)
                    .addValue("riskLevel", rule.riskLevel.trim().uppercase())
                    .addValue("scoreMin", rule.scoreMin)
                    .addValue("scoreMax", rule.scoreMax)
                    .addValue("resultTitle", rule.resultTitle)
                    .addValue("resultDescription", rule.resultDescription)
                    .addValue("suggestionText", rule.suggestionText)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now),
                keyHolder,
                arrayOf("id")
            )
            keyHolder.key?.toLong() ?: error("failed to create scale result rule")
        }
        return BatchCreateResponse(createdIds = createdIds)
    }

    fun findDetailById(id: Long): ScaleDetail? {
        val sql = """
            select id, scale_code, scale_name, description, applicable_target, version_no, status,
                   score_method, score_coefficient, anonymous_supported, report_template,
                   created_by, created_at, updated_by, updated_at
            from psy_scale
            where id = :id
        """.trimIndent()
        val rows = jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
            ScaleDetail(
                id = rs.getLong("id"),
                scaleCode = rs.getString("scale_code"),
                scaleName = rs.getString("scale_name"),
                description = rs.getString("description"),
                applicableTarget = rs.getString("applicable_target"),
                versionNo = rs.getString("version_no"),
                status = rs.getString("status"),
                scoreMethod = rs.getString("score_method"),
                scoreCoefficient = rs.getBigDecimal("score_coefficient"),
                anonymousSupported = rs.getBoolean("anonymous_supported"),
                reportTemplate = rs.getString("report_template"),
                createdBy = rs.getObject("created_by", java.lang.Long::class.java)?.toLong(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedBy = rs.getObject("updated_by", java.lang.Long::class.java)?.toLong(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
                dimensions = emptyList(),
                questions = emptyList(),
                resultRules = emptyList()
            )
        }
        val detail = rows.firstOrNull() ?: return null
        return detail.copy(
            dimensions = findDimensionsByScaleId(id),
            questions = findQuestionsByScaleId(id),
            resultRules = findResultRulesByScaleId(id)
        )
    }

    private fun findQuestionsByScaleId(scaleId: Long): List<ScaleQuestion> {
        val questionSql = """
            select id, scale_id, dimension_id, question_no, question_title, question_type,
                   required_flag, reverse_score_flag, weight_value, sort_no
            from psy_scale_question
            where scale_id = :scaleId
            order by sort_no asc, question_no asc
        """.trimIndent()
        val questions = jdbcTemplate.query(questionSql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleQuestion(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                questionNo = rs.getInt("question_no"),
                questionTitle = rs.getString("question_title"),
                questionType = rs.getString("question_type"),
                requiredFlag = rs.getBoolean("required_flag"),
                reverseScoreFlag = rs.getBoolean("reverse_score_flag"),
                weightValue = rs.getBigDecimal("weight_value"),
                sortNo = rs.getInt("sort_no"),
                options = emptyList()
            )
        }
        if (questions.isEmpty()) return emptyList()
        val questionIds = questions.map { it.id }
        val optionSql = """
            select id, question_id, option_code, option_label, score_value, sort_no
            from psy_scale_option
            where question_id in (:questionIds)
            order by sort_no asc
        """.trimIndent()
        val options = jdbcTemplate.query(optionSql, mapOf("questionIds" to questionIds)) { rs, _ ->
            ScaleQuestionOption(
                id = rs.getLong("id"),
                questionId = rs.getLong("question_id"),
                optionCode = rs.getString("option_code"),
                optionLabel = rs.getString("option_label"),
                scoreValue = rs.getBigDecimal("score_value"),
                sortNo = rs.getInt("sort_no")
            )
        }
        val optionsByQuestionId = options.groupBy { it.questionId }
        return questions.map { q -> q.copy(options = optionsByQuestionId[q.id] ?: emptyList()) }
    }

    private fun findResultRulesByScaleId(scaleId: Long): List<ScaleResultRule> {
        val sql = """
            select id, scale_id, dimension_id, risk_level, score_min, score_max,
                   result_title, result_description, suggestion_text
            from psy_scale_result_rule
            where scale_id = :scaleId
            order by id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleResultRule(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                riskLevel = rs.getString("risk_level"),
                scoreMin = rs.getBigDecimal("score_min"),
                scoreMax = rs.getBigDecimal("score_max"),
                resultTitle = rs.getString("result_title"),
                resultDescription = rs.getString("result_description"),
                suggestionText = rs.getString("suggestion_text")
            )
        }
    }

    private fun findDimensionsByScaleId(scaleId: Long): List<ScaleDimension> {
        val sql = """
            select id, scale_id, dimension_code, dimension_name, description, sort_no
            from psy_scale_dimension
            where scale_id = :scaleId
            order by sort_no asc, id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleDimension(
                id = rs.getLong("id"),
                scaleId = rs.getLong("scale_id"),
                dimensionCode = rs.getString("dimension_code"),
                dimensionName = rs.getString("dimension_name"),
                description = rs.getString("description"),
                sortNo = rs.getInt("sort_no")
            )
        }
    }

    private val scaleSummaryRowMapper = RowMapper { rs, _ ->
        ScaleSummary(
            id = rs.getLong("id"),
            scaleCode = rs.getString("scale_code"),
            scaleName = rs.getString("scale_name"),
            applicableTarget = rs.getString("applicable_target"),
            versionNo = rs.getString("version_no"),
            status = rs.getString("status"),
            scoreMethod = rs.getString("score_method"),
            scoreCoefficient = rs.getBigDecimal("score_coefficient"),
            anonymousSupported = rs.getBoolean("anonymous_supported"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }
}
