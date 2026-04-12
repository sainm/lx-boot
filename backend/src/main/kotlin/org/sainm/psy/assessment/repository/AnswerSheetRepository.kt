package org.sainm.psy.assessment.repository

import org.sainm.psy.assessment.api.AnswerItemRequest
import org.sainm.psy.assessment.domain.AnswerSheetRescoreContext
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionItem
import org.sainm.psy.assessment.domain.TaskQuestionOption
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.service.DimensionScoreResult
import org.sainm.psy.assessment.service.NormMatchingContext
import org.sainm.psy.assessment.service.QuestionScoreContext
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

@Repository
class AnswerSheetRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    data class DraftAnswerSheetInfo(
        val answerSheetId: Long,
        val versionNo: Int
    )

    data class OverdueDraftAnswerSheet(
        val answerSheetId: Long,
        val taskId: Long,
        val scaleId: Long,
        val userId: Long
    )

    private data class TaskQuestionBase(
        val taskId: Long,
        val scaleId: Long,
        val scaleName: String,
        val allowSaveFlag: Boolean
    )

    data class ScaleScoringContext(
        val scoreMethod: String,
        val scoreCoefficient: BigDecimal,
        val applicableTarget: String?,
        val normDefaultGroup: String?
    )

    fun findTaskQuestionPayload(taskId: Long, userId: Long): TaskQuestionPayload? {
        val taskSql = """
            select t.id as task_id, t.scale_id, s.scale_name, t.allow_save_flag
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            where t.id = :taskId
        """.trimIndent()
        val taskRows = jdbcTemplate.query(taskSql, mapOf("taskId" to taskId)) { rs, _ ->
            TaskQuestionBase(
                taskId = rs.getLong("task_id"),
                scaleId = rs.getLong("scale_id"),
                scaleName = rs.getString("scale_name"),
                allowSaveFlag = rs.getBoolean("allow_save_flag")
            )
        }
        val task = taskRows.firstOrNull() ?: return null
        val questionSql = """
            select q.id as question_id, q.question_no, q.question_title, q.question_type, q.required_flag,
                   q.option_selection_limit, q.slider_min, q.slider_max, q.slider_step,
                   q.text_input_enabled, q.text_input_placeholder, q.matrix_group_code, q.row_code, q.column_code,
                   o.id as option_id, o.option_code, o.option_label, o.score_value, o.exclusive_flag
            from psy_scale_question q
            left join psy_scale_option o on o.question_id = q.id
            where q.scale_id = :scaleId
            order by q.sort_no asc, q.question_no asc, o.sort_no asc, o.id asc
        """.trimIndent()
        val questionMap = linkedMapOf<Long, MutableList<TaskQuestionOption>>()
        val questionMeta = linkedMapOf<Long, TaskQuestionItem>()
        jdbcTemplate.query(questionSql, mapOf("scaleId" to task.scaleId)) { rs ->
            val questionId = rs.getLong("question_id")
            questionMeta.putIfAbsent(
                questionId,
                TaskQuestionItem(
                    questionId = questionId,
                    questionNo = rs.getInt("question_no"),
                    questionTitle = rs.getString("question_title"),
                    questionType = rs.getString("question_type"),
                    requiredFlag = rs.getBoolean("required_flag"),
                    optionSelectionLimit = rs.getObject("option_selection_limit", java.lang.Integer::class.java)?.toInt(),
                    sliderMin = rs.getBigDecimal("slider_min"),
                    sliderMax = rs.getBigDecimal("slider_max"),
                    sliderStep = rs.getBigDecimal("slider_step"),
                    textInputEnabled = rs.getBoolean("text_input_enabled"),
                    textInputPlaceholder = rs.getString("text_input_placeholder"),
                    matrixGroupCode = rs.getString("matrix_group_code"),
                    rowCode = rs.getString("row_code"),
                    columnCode = rs.getString("column_code"),
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
                        scoreValue = rs.getBigDecimal("score_value"),
                        exclusiveFlag = rs.getBoolean("exclusive_flag")
                    )
                )
            }
        }
        val questions = questionMeta.values.map { meta ->
            meta.copy(options = questionMap[meta.questionId].orEmpty())
        }
        val draftInfo = findDraftAnswerSheetInfo(taskId, userId)
        return TaskQuestionPayload(
            taskId = task.taskId,
            scaleId = task.scaleId,
            scaleName = task.scaleName,
            allowSaveFlag = task.allowSaveFlag,
            draftAnswerSheetId = draftInfo?.answerSheetId,
            draftVersionNo = draftInfo?.versionNo,
            questions = questions
        )
    }

    fun isTaskAllowSave(taskId: Long): Boolean? =
        jdbcTemplate.query(
            "select allow_save_flag from psy_assessment_task where id = :taskId",
            mapOf("taskId" to taskId)
        ) { rs, _ -> rs.getBoolean("allow_save_flag") }.firstOrNull()

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

    fun findDraftAnswerSheetInfo(taskId: Long, userId: Long): DraftAnswerSheetInfo? {
        val sql = """
            select id, version_no
            from psy_assessment_answer_sheet
            where task_id = :taskId and user_id = :userId and answer_status = 'DRAFT'
            order by id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId, "userId" to userId)) { rs, _ ->
            DraftAnswerSheetInfo(
                answerSheetId = rs.getLong("id"),
                versionNo = rs.getInt("version_no")
            )
        }
            .firstOrNull()
    }

    fun findDraftAnswerSheet(taskId: Long, userId: Long): Long? =
        findDraftAnswerSheetInfo(taskId, userId)?.answerSheetId

    fun hasSubmittedAnswerSheet(taskId: Long, userId: Long): Boolean {
        val sql = """
            select count(1)
            from psy_assessment_answer_sheet
            where task_id = :taskId and user_id = :userId and answer_status = 'SUBMITTED'
        """.trimIndent()
        return (jdbcTemplate.queryForObject(sql, mapOf("taskId" to taskId, "userId" to userId), Long::class.java) ?: 0L) > 0
    }

    fun createAnswerSheet(taskId: Long, scaleId: Long, userId: Long, status: String): Long {
        val now = LocalDateTime.now()
        val sql = """
            insert into psy_assessment_answer_sheet (
                task_id, scale_id, user_id, answer_status, version_no, start_time, created_at, updated_at
            ) values (
                :taskId, :scaleId, :userId, :answerStatus, :versionNo, :startTime, :createdAt, :updatedAt
            )
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("taskId", taskId)
            .addValue("scaleId", scaleId)
            .addValue("userId", userId)
            .addValue("answerStatus", status)
            .addValue("versionNo", 1)
            .addValue("startTime", Timestamp.valueOf(now))
            .addValue("createdAt", Timestamp.valueOf(now))
            .addValue("updatedAt", Timestamp.valueOf(now))
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder, arrayOf("id"))
        return keyHolder.key?.toLong() ?: error("failed to create answer sheet")
    }

    fun findOverdueDraftAnswerSheets(now: LocalDateTime = LocalDateTime.now()): List<OverdueDraftAnswerSheet> {
        val sql = """
            select ans.id as answer_sheet_id, ans.task_id, ans.scale_id, ans.user_id
            from psy_assessment_answer_sheet ans
            join psy_assessment_task t on t.id = ans.task_id
            where ans.answer_status = 'DRAFT'
              and ans.user_id is not null
              and t.end_time < :now
              and t.allow_timeout_submit_flag = true
              and not exists (
                  select 1
                  from psy_assessment_answer_sheet submitted
                  where submitted.task_id = ans.task_id
                    and submitted.user_id = ans.user_id
                    and submitted.answer_status = 'SUBMITTED'
              )
            order by ans.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("now" to Timestamp.valueOf(now))) { rs, _ ->
            OverdueDraftAnswerSheet(
                answerSheetId = rs.getLong("answer_sheet_id"),
                taskId = rs.getLong("task_id"),
                scaleId = rs.getLong("scale_id"),
                userId = rs.getLong("user_id")
            )
        }
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

    fun incrementDraftVersion(answerSheetId: Long, expectedVersion: Int?): Int {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val params = MapSqlParameterSource()
            .addValue("id", answerSheetId)
            .addValue("updatedAt", now)
        val sql = buildString {
            append(
                """
                update psy_assessment_answer_sheet
                set version_no = version_no + 1,
                    updated_at = :updatedAt
                where id = :id
                  and answer_status = 'DRAFT'
                """.trimIndent()
            )
            if (expectedVersion != null) {
                append("\n  and version_no = :expectedVersion")
                params.addValue("expectedVersion", expectedVersion)
            }
        }
        val updated = jdbcTemplate.update(sql, params)
        if (updated == 0) {
            return 0
        }
        return jdbcTemplate.queryForObject(
            "select version_no from psy_assessment_answer_sheet where id = :id",
            mapOf("id" to answerSheetId),
            Int::class.java
        ) ?: 0
    }

    fun submitDraftAnswerSheet(answerSheetId: Long, submitToken: String?, expectedVersion: Int?): Int {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val params = MapSqlParameterSource()
            .addValue("id", answerSheetId)
            .addValue("submitTime", now)
            .addValue("updatedAt", now)
            .addValue("submitToken", submitToken)
        val sql = buildString {
            append(
                """
                update psy_assessment_answer_sheet
                set answer_status = 'SUBMITTED',
                    submit_time = :submitTime,
                    submit_token = :submitToken,
                    version_no = version_no + 1,
                    updated_at = :updatedAt
                where id = :id
                  and answer_status = 'DRAFT'
                """.trimIndent()
            )
            if (expectedVersion != null) {
                append("\n  and version_no = :expectedVersion")
                params.addValue("expectedVersion", expectedVersion)
            }
        }
        return jdbcTemplate.update(sql, params)
    }

    fun findSubmittedResultBySubmitToken(taskId: Long, userId: Long, submitToken: String): AnswerSubmitResult? {
        val sql = """
            select sh.id as answer_sheet_id, rs.id as result_id, rp.id as report_id, rs.risk_level, sh.version_no
            from psy_assessment_answer_sheet sh
            join psy_assessment_result rs on rs.answer_sheet_id = sh.id
            join psy_report rp on rp.result_id = rs.id
            where sh.task_id = :taskId
              and sh.user_id = :userId
              and sh.answer_status = 'SUBMITTED'
              and sh.submit_token = :submitToken
            order by rp.id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("taskId" to taskId, "userId" to userId, "submitToken" to submitToken)
        ) { rs, _ ->
            AnswerSubmitResult(
                answerSheetId = rs.getLong("answer_sheet_id"),
                resultId = rs.getLong("result_id"),
                reportId = rs.getLong("report_id"),
                riskLevel = rs.getString("risk_level"),
                versionNo = rs.getInt("version_no")
            )
        }.firstOrNull()
    }

    fun findRescoreContextByResultId(resultId: Long): AnswerSheetRescoreContext? {
        val sql = """
            select sh.id as answer_sheet_id,
                   sh.task_id,
                   sh.scale_id,
                   sh.user_id,
                   rs.id as result_id,
                   rs.risk_level
            from psy_assessment_result rs
            join psy_assessment_answer_sheet sh on sh.id = rs.answer_sheet_id
            where rs.id = :resultId
              and sh.answer_status = 'SUBMITTED'
              and sh.user_id is not null
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("resultId" to resultId)) { rs, _ ->
            AnswerSheetRescoreContext(
                answerSheetId = rs.getLong("answer_sheet_id"),
                taskId = rs.getLong("task_id"),
                scaleId = rs.getLong("scale_id"),
                userId = rs.getLong("user_id"),
                resultId = rs.getLong("result_id"),
                previousRiskLevel = rs.getString("risk_level")
            )
        }.firstOrNull()
    }

    fun loadAnswerItems(answerSheetId: Long): List<AnswerItemRequest> {
        val sql = """
            select question_id, option_id, answer_text
                 , answer_value
            from psy_assessment_answer_item
            where answer_sheet_id = :answerSheetId
            order by id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("answerSheetId" to answerSheetId)) { rs, _ ->
            AnswerItemRequest(
                questionId = rs.getLong("question_id"),
                optionId = rs.getObject("option_id", java.lang.Long::class.java)?.toLong(),
                answerText = rs.getString("answer_text"),
                answerValue = rs.getBigDecimal("answer_value")
            )
        }
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
                answer_sheet_id, question_id, option_id, answer_text, answer_value, score_value, created_at
            ) values (
                :answerSheetId, :questionId, :optionId, :answerText, :answerValue, :scoreValue, :createdAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val batchParams = answers.map { answer ->
            val score = answer.optionId?.let { scoreMap[it] } ?: answer.answerValue ?: BigDecimal.ZERO
            MapSqlParameterSource()
                .addValue("answerSheetId", answerSheetId)
                .addValue("questionId", answer.questionId)
                .addValue("optionId", answer.optionId)
                .addValue("answerText", answer.answerText)
                .addValue("answerValue", answer.answerValue)
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
            select id, question_type, dimension_id, reverse_score_flag, weight_value
            from psy_scale_question
            where scale_id = :scaleId and id in (:questionIds)
        """.trimIndent()
        val metaMap = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId, "questionIds" to questionIds)) { rs, _ ->
            rs.getLong("id") to Triple(
                rs.getString("question_type"),
                rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                Pair(rs.getBoolean("reverse_score_flag"), rs.getBigDecimal("weight_value") ?: BigDecimal.ONE)
            )
        }.toMap()
        return answers.groupBy { it.questionId }.mapNotNull { (questionId, groupedAnswers) ->
            val meta = metaMap[questionId] ?: return@mapNotNull null
            val rawScore = when (meta.first) {
                "MULTI_SELECT" -> groupedAnswers.fold(BigDecimal.ZERO) { acc, answer ->
                    acc + (answer.optionId?.let { optionScoreMap[it] } ?: answer.answerValue ?: BigDecimal.ZERO)
                }
                "SLIDER" -> groupedAnswers.firstOrNull()?.answerValue ?: BigDecimal.ZERO
                else -> groupedAnswers.firstOrNull()?.let { answer ->
                    answer.optionId?.let { optionScoreMap[it] } ?: answer.answerValue ?: BigDecimal.ZERO
                } ?: BigDecimal.ZERO
            }
            QuestionScoreContext(
                questionId = questionId,
                dimensionId = meta.second,
                reverseScoreFlag = meta.third.first,
                weightValue = meta.third.second,
                rawScore = rawScore,
                selectedOptionIds = groupedAnswers.mapNotNull { it.optionId }.distinct(),
                answerValue = groupedAnswers.firstOrNull()?.answerValue
            )
        }
    }

    fun loadScaleScoringContext(scaleId: Long, userId: Long?): Pair<ScaleScoringContext, NormMatchingContext?> {
        val sql = "select score_method, score_coefficient, applicable_target, norm_default_group from psy_scale where id = :scaleId"
        val scaleContext = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleScoringContext(
                scoreMethod = rs.getString("score_method"),
                scoreCoefficient = rs.getBigDecimal("score_coefficient"),
                applicableTarget = rs.getString("applicable_target"),
                normDefaultGroup = rs.getString("norm_default_group")
            )
        }.firstOrNull() ?: ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null)
        val normContext = userId?.let { loadNormMatchingContext(it, scaleContext) }
        return scaleContext to normContext
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

    fun replaceDimensionScores(resultId: Long, dimensionScores: List<DimensionScoreResult>) {
        jdbcTemplate.update(
            "delete from psy_assessment_result_dimension where result_id = :resultId",
            mapOf("resultId" to resultId)
        )
        saveDimensionScores(resultId, dimensionScores)
    }

    fun updateResult(
        resultId: Long,
        totalScore: BigDecimal,
        riskLevel: String,
        warningFlag: Boolean,
        resultSummary: String,
        scoreSource: String = "RAW_SCORE",
        standardScore: BigDecimal? = null,
        zScore: BigDecimal? = null,
        tScore: BigDecimal? = null,
        normCode: String? = null,
        highRiskFlag: Boolean = false,
        highRiskRuleCode: String? = null
    ) {
        val now = Timestamp.valueOf(LocalDateTime.now())
        jdbcTemplate.update(
            """
            update psy_assessment_result
            set total_score = :totalScore,
                risk_level = :riskLevel,
                warning_flag = :warningFlag,
                result_summary = :resultSummary,
                score_source = :scoreSource,
                standard_score = :standardScore,
                z_score = :zScore,
                t_score = :tScore,
                norm_code = :normCode,
                high_risk_flag = :highRiskFlag,
                high_risk_rule_code = :highRiskRuleCode,
                scored_at = :scoredAt
            where id = :resultId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("resultId", resultId)
                .addValue("totalScore", totalScore)
                .addValue("riskLevel", riskLevel)
                .addValue("warningFlag", warningFlag)
                .addValue("resultSummary", resultSummary)
                .addValue("scoreSource", scoreSource)
                .addValue("standardScore", standardScore)
                .addValue("zScore", zScore)
                .addValue("tScore", tScore)
                .addValue("normCode", normCode)
                .addValue("highRiskFlag", highRiskFlag)
                .addValue("highRiskRuleCode", highRiskRuleCode)
                .addValue("scoredAt", now)
        )
    }

    fun createResult(
        answerSheetId: Long,
        totalScore: BigDecimal,
        riskLevel: String,
        warningFlag: Boolean,
        resultSummary: String,
        scoreSource: String = "RAW_SCORE",
        standardScore: BigDecimal? = null,
        zScore: BigDecimal? = null,
        tScore: BigDecimal? = null,
        normCode: String? = null,
        highRiskFlag: Boolean = false,
        highRiskRuleCode: String? = null
    ): Long {
        val sql = """
            insert into psy_assessment_result (
                answer_sheet_id, total_score, risk_level, warning_flag, result_summary,
                score_source, standard_score, z_score, t_score, norm_code, high_risk_flag, high_risk_rule_code,
                scored_at, created_at
            ) values (
                :answerSheetId, :totalScore, :riskLevel, :warningFlag, :resultSummary,
                :scoreSource, :standardScore, :zScore, :tScore, :normCode, :highRiskFlag, :highRiskRuleCode,
                :scoredAt, :createdAt
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
                .addValue("scoreSource", scoreSource)
                .addValue("standardScore", standardScore)
                .addValue("zScore", zScore)
                .addValue("tScore", tScore)
                .addValue("normCode", normCode)
                .addValue("highRiskFlag", highRiskFlag)
                .addValue("highRiskRuleCode", highRiskRuleCode)
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
        return createWarningIfNeeded(resultId, riskLevel, riskLevel, reason)
    }

    fun createWarningIfNeeded(resultId: Long, riskLevel: String, warningLevel: String, reason: String): Long? {
        if (riskLevel == "NORMAL" && warningLevel == "NORMAL") {
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
                .addValue("warningLevel", warningLevel)
                .addValue("warningPriority", if (warningLevel == "HIGH") "HIGH" else "MEDIUM")
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

    private fun loadNormMatchingContext(userId: Long, scaleContext: ScaleScoringContext): NormMatchingContext? {
        val availableColumns = jdbcTemplate.query(
            """
            select upper(column_name)
            from information_schema.columns
            where upper(table_name) = 'SYS_USER'
            """.trimIndent(),
            emptyMap<String, Any>()
        ) { rs, _ -> rs.getString(1) }.toSet()
        if (availableColumns.isEmpty()) {
            return NormMatchingContext(
                applicableTarget = scaleContext.applicableTarget,
                preferredNormCode = scaleContext.normDefaultGroup
            )
        }
        val dateColumn = when {
            "BIRTH_DATE" in availableColumns -> "birth_date"
            "BIRTHDAY" in availableColumns -> "birthday"
            else -> null
        }
        val selectFields = mutableListOf<String>()
        if ("GENDER" in availableColumns) selectFields += "gender"
        if ("ORG_TYPE" in availableColumns) selectFields += "org_type"
        if (dateColumn != null) selectFields += dateColumn
        if (selectFields.isEmpty()) {
            return NormMatchingContext(
                applicableTarget = scaleContext.applicableTarget,
                preferredNormCode = scaleContext.normDefaultGroup
            )
        }
        val userSql = "select ${selectFields.joinToString(", ")} from sys_user where id = :userId"
        return jdbcTemplate.query(userSql, mapOf("userId" to userId)) { rs, _ ->
            val birthDate = dateColumn?.let { rs.getDate(it)?.toLocalDate() }
            NormMatchingContext(
                age = birthDate?.let { Period.between(it, LocalDate.now()).years },
                gender = if ("GENDER" in availableColumns) rs.getString("gender") else null,
                orgType = if ("ORG_TYPE" in availableColumns) rs.getString("org_type") else null,
                applicableTarget = scaleContext.applicableTarget,
                preferredNormCode = scaleContext.normDefaultGroup
            )
        }.firstOrNull() ?: NormMatchingContext(
            applicableTarget = scaleContext.applicableTarget,
            preferredNormCode = scaleContext.normDefaultGroup
        )
    }
}
