package org.sainm.psy.assessment.repository

import org.sainm.psy.assessment.api.AnswerItemRequest
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionItem
import org.sainm.psy.assessment.domain.TaskQuestionOption
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.service.DimensionScoreResult
import org.sainm.psy.assessment.service.QuestionScoreContext
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class AnswerSheetRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findTaskQuestionPayload(taskId: Long): TaskQuestionPayload? {
        val taskSql = """
            select t.id as task_id, t.scale_id, s.scale_name
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            where t.id = :taskId
        """.trimIndent()
        val taskRows = jdbcTemplate.query(taskSql, mapOf("taskId" to taskId)) { rs, _ ->
            Triple(rs.getLong("task_id"), rs.getLong("scale_id"), rs.getString("scale_name"))
        }
        val task = taskRows.firstOrNull() ?: return null
        val questionSql = """
            select q.id as question_id, q.question_no, q.question_title, q.question_type, q.required_flag,
                   o.id as option_id, o.option_code, o.option_label, o.score_value
            from psy_scale_question q
            left join psy_scale_option o on o.question_id = q.id
            where q.scale_id = :scaleId
            order by q.sort_no asc, q.question_no asc, o.sort_no asc, o.id asc
        """.trimIndent()
        val questionMap = linkedMapOf<Long, MutableList<TaskQuestionOption>>()
        val questionMeta = linkedMapOf<Long, TaskQuestionItem>()
        jdbcTemplate.query(questionSql, mapOf("scaleId" to task.second)) { rs ->
            val questionId = rs.getLong("question_id")
            questionMeta.putIfAbsent(
                questionId,
                TaskQuestionItem(
                    questionId = questionId,
                    questionNo = rs.getInt("question_no"),
                    questionTitle = rs.getString("question_title"),
                    questionType = rs.getString("question_type"),
                    requiredFlag = rs.getBoolean("required_flag"),
                    options = emptyList()
                )
            )
            questionMap.computeIfAbsent(questionId) { mutableListOf() }
            val optionId = rs.getObject("option_id", java.lang.Long::class.java)?.toLong()
            if (optionId != null) {
                questionMap.getValue(questionId).add(
                    TaskQuestionOption(
                        optionId = optionId,
                        optionCode = rs.getString("option_code"),
                        optionLabel = rs.getString("option_label"),
                        scoreValue = rs.getBigDecimal("score_value")
                    )
                )
            }
        }
        val questions = questionMeta.values.map { meta ->
            meta.copy(options = questionMap[meta.questionId].orEmpty())
        }
        return TaskQuestionPayload(
            taskId = task.first,
            scaleId = task.second,
            scaleName = task.third,
            questions = questions
        )
    }

    fun isAssignedToUser(taskId: Long, userId: Long, groupId: Long?): Boolean {
        val sql = """
            select count(1)
            from psy_assessment_task_assignment
            where task_id = :taskId
              and (
                  (target_type = 'USER' and target_id = :userId)
                  or
                  (:groupId is not null and target_type = 'GROUP' and target_id = :groupId)
              )
        """.trimIndent()
        return (jdbcTemplate.queryForObject(
            sql,
            mapOf("taskId" to taskId, "userId" to userId, "groupId" to groupId),
            Long::class.java
        ) ?: 0L) > 0
    }

    fun findDraftAnswerSheet(taskId: Long, userId: Long): Long? {
        val sql = """
            select id
            from psy_assessment_answer_sheet
            where task_id = :taskId and user_id = :userId and answer_status = 'DRAFT'
            order by id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId, "userId" to userId)) { rs, _ -> rs.getLong("id") }
            .firstOrNull()
    }

    fun createAnswerSheet(taskId: Long, scaleId: Long, userId: Long, status: String): Long {
        val now = LocalDateTime.now()
        val sql = """
            insert into psy_assessment_answer_sheet (
                task_id, scale_id, user_id, answer_status, start_time, created_at, updated_at
            ) values (
                :taskId, :scaleId, :userId, :answerStatus, :startTime, :createdAt, :updatedAt
            )
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("taskId", taskId)
            .addValue("scaleId", scaleId)
            .addValue("userId", userId)
            .addValue("answerStatus", status)
            .addValue("startTime", Timestamp.valueOf(now))
            .addValue("createdAt", Timestamp.valueOf(now))
            .addValue("updatedAt", Timestamp.valueOf(now))
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder, arrayOf("id"))
        return keyHolder.key?.toLong() ?: error("failed to create answer sheet")
    }

    fun updateAnswerSheetStatus(answerSheetId: Long, status: String) {
        val sql = """
            update psy_assessment_answer_sheet
            set answer_status = :answerStatus,
                submit_time = case when :answerStatus = 'SUBMITTED' then :submitTime else submit_time end,
                updated_at = :updatedAt
            where id = :id
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("id", answerSheetId)
                .addValue("answerStatus", status)
                .addValue("submitTime", now)
                .addValue("updatedAt", now)
        )
    }

    fun replaceAnswerItems(answerSheetId: Long, answers: List<AnswerItemRequest>): Map<Long, BigDecimal> {
        jdbcTemplate.update(
            "delete from psy_assessment_answer_item where answer_sheet_id = :answerSheetId",
            mapOf("answerSheetId" to answerSheetId)
        )
        if (answers.isEmpty()) return emptyMap()
        val optionIds = answers.mapNotNull { it.optionId }.distinct()
        val scoreMap = if (optionIds.isEmpty()) emptyMap() else loadOptionScores(optionIds)
        val sql = """
            insert into psy_assessment_answer_item (
                answer_sheet_id, question_id, option_id, answer_text, score_value, created_at
            ) values (
                :answerSheetId, :questionId, :optionId, :answerText, :scoreValue, :createdAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val batchParams = answers.map { answer ->
            val score = answer.optionId?.let { scoreMap[it] } ?: BigDecimal.ZERO
            MapSqlParameterSource()
                .addValue("answerSheetId", answerSheetId)
                .addValue("questionId", answer.questionId)
                .addValue("optionId", answer.optionId)
                .addValue("answerText", answer.answerText)
                .addValue("scoreValue", score)
                .addValue("createdAt", now)
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, batchParams)
        return scoreMap
    }

    fun loadQuestionScoringMeta(
        scaleId: Long,
        answers: List<AnswerItemRequest>,
        optionScoreMap: Map<Long, BigDecimal>
    ): List<QuestionScoreContext> {
        if (answers.isEmpty()) return emptyList()
        val questionIds = answers.map { it.questionId }.distinct()
        val sql = """
            select id, dimension_id, reverse_score_flag, weight_value
            from psy_scale_question
            where scale_id = :scaleId and id in (:questionIds)
        """.trimIndent()
        val metaMap = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId, "questionIds" to questionIds)) { rs, _ ->
            rs.getLong("id") to Triple(
                rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                rs.getBoolean("reverse_score_flag"),
                rs.getBigDecimal("weight_value") ?: BigDecimal.ONE
            )
        }.toMap()
        return answers.mapNotNull { answer ->
            val meta = metaMap[answer.questionId] ?: return@mapNotNull null
            QuestionScoreContext(
                questionId = answer.questionId,
                dimensionId = meta.first,
                reverseScoreFlag = meta.second,
                weightValue = meta.third,
                rawScore = answer.optionId?.let { optionScoreMap[it] } ?: BigDecimal.ZERO
            )
        }
    }

    fun loadScaleScoring(scaleId: Long): Pair<String, BigDecimal> {
        val sql = "select score_method, score_coefficient from psy_scale where id = :scaleId"
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            rs.getString("score_method") to rs.getBigDecimal("score_coefficient")
        }.firstOrNull() ?: ("SIMPLE_SUM" to BigDecimal.ONE)
    }

    fun saveDimensionScores(resultId: Long, dimensionScores: List<DimensionScoreResult>) {
        if (dimensionScores.isEmpty()) return
        val sql = """
            insert into psy_assessment_result_dimension (
                result_id, dimension_id, dimension_score, risk_level, result_title, created_at
            ) values (
                :resultId, :dimensionId, :dimensionScore, :riskLevel, :resultTitle, :createdAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val batchParams = dimensionScores.map { d ->
            MapSqlParameterSource()
                .addValue("resultId", resultId)
                .addValue("dimensionId", d.dimensionId)
                .addValue("dimensionScore", d.score)
                .addValue("riskLevel", d.riskLevel)
                .addValue("resultTitle", d.resultTitle)
                .addValue("createdAt", now)
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, batchParams)
    }

    fun createResult(answerSheetId: Long, totalScore: BigDecimal, riskLevel: String, warningFlag: Boolean, resultSummary: String): Long {
        val sql = """
            insert into psy_assessment_result (
                answer_sheet_id, total_score, risk_level, warning_flag, result_summary, scored_at, created_at
            ) values (
                :answerSheetId, :totalScore, :riskLevel, :warningFlag, :resultSummary, :scoredAt, :createdAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("answerSheetId", answerSheetId)
                .addValue("totalScore", totalScore)
                .addValue("riskLevel", riskLevel)
                .addValue("warningFlag", warningFlag)
                .addValue("resultSummary", resultSummary)
                .addValue("scoredAt", now)
                .addValue("createdAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create result")
    }

    fun createReport(resultId: Long, authorUserId: Long, title: String, content: String): Long {
        val sql = """
            insert into psy_report (
                result_id, report_type, author_user_id, report_title, report_content, version_no, created_at, updated_at
            ) values (
                :resultId, 'SYSTEM', :authorUserId, :reportTitle, :reportContent, 1, :createdAt, :updatedAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
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
        return keyHolder.key?.toLong() ?: error("failed to create report")
    }

    fun createWarningIfNeeded(resultId: Long, riskLevel: String, reason: String): Long? {
        if (riskLevel == "NORMAL") {
            return null
        }
        val sql = """
            insert into psy_warning_record (
                result_id, warning_level, warning_priority, warning_reason, status, created_at, updated_at
            ) values (
                :resultId, :warningLevel, :warningPriority, :warningReason, :status, :createdAt, :updatedAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("resultId", resultId)
                .addValue("warningLevel", riskLevel)
                .addValue("warningPriority", if (riskLevel == "HIGH") "HIGH" else "MEDIUM")
                .addValue("warningReason", reason)
                .addValue("status", "PENDING")
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong()
    }

    private fun loadOptionScores(optionIds: List<Long>): Map<Long, BigDecimal> {
        if (optionIds.isEmpty()) {
            return emptyMap()
        }
        val sql = """
            select id, score_value
            from psy_scale_option
            where id in (:optionIds)
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("optionIds" to optionIds)) { rs, _ ->
            rs.getLong("id") to rs.getBigDecimal("score_value")
        }.toMap()
    }
}
