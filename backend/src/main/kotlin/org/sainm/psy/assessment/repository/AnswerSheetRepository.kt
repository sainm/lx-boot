package org.sainm.psy.assessment.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.sainm.psy.assessment.api.AnswerItemRequest
import org.sainm.psy.assessment.domain.AnswerSheetRescoreContext
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskDraftAnswerItem
import org.sainm.psy.assessment.domain.TaskQuestionItem
import org.sainm.psy.assessment.domain.TaskQuestionOption
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.domain.TaskSkipRule
import org.sainm.psy.assessment.service.DimensionScoreResult
import org.sainm.psy.assessment.service.NormMatchingContext
import org.sainm.psy.assessment.service.QuestionScoreContext
import org.sainm.psy.assessment.service.AnswerQualityAssessment
import org.sainm.psy.common.i18n.SupportedContentLocale
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
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
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    data class DraftAnswerSheetInfo(
        val answerSheetId: Long,
        val versionNo: Int,
        val responseLocaleCode: String? = null,
        val startTime: LocalDateTime? = null
    )

    data class OverdueDraftAnswerSheet(
        val answerSheetId: Long,
        val taskId: Long,
        val scaleId: Long,
        val userId: Long?,
        val anonymousToken: String? = null,
        val responseLocaleCode: String? = null
    )

    data class SubmittedTaskReportInfo(
        val reportId: Long,
        val resultId: Long,
        val riskLevel: String
    )

    private data class TaskQuestionBase(
        val taskId: Long,
        val scaleId: Long,
        val scaleName: String,
        val allowSaveFlag: Boolean,
        val allowRetakeFlag: Boolean,
        val anonymousFlag: Boolean,
        val allowTimeoutSubmitFlag: Boolean,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val taskStatus: String,
        val skipRulesJson: String?
    )

    data class ScaleScoringContext(
        val scoreMethod: String,
        val scoreCoefficient: BigDecimal,
        val applicableTarget: String?,
        val normDefaultGroup: String?,
        val highRiskWarningEnabled: Boolean = true,
        val qualityPolicy: ScalePackageQualityPolicy = ScalePackageQualityPolicy(),
        val totalQuestionCount: Int = 0,
        val totalWeight: BigDecimal = BigDecimal.ZERO
    )

    data class DimensionReportMeta(
        val dimensionId: Long,
        val dimensionCode: String,
        val dimensionName: String,
        val sortNo: Int
    )

    private data class ScoringQuestionMeta(
        val questionType: String,
        val dimensionId: Long?,
        val reverseScoreFlag: Boolean,
        val weightValue: BigDecimal,
        val dimensionQuestionCount: Int
    )

    /** Serializes writes for one authenticated respondent and task across app instances. */
    fun lockRespondentWrite(taskId: Long, authenticatedUserId: Long) {
        lockRespondentWrite(taskId, "user:$authenticatedUserId")
    }

    fun lockAnonymousRespondentWrite(taskId: Long, anonymousToken: String) {
        lockRespondentWrite(taskId, "anonymous:$anonymousToken")
    }

    private fun lockRespondentWrite(taskId: Long, identityKey: String) {
        jdbcTemplate.queryForObject(
            "select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
            mapOf("lockKey" to "assessment:$taskId:$identityKey"),
            Any::class.java
        )
    }

    fun findTaskQuestionPayload(taskId: Long, userId: Long): TaskQuestionPayload? {
        return findTaskQuestionPayload(taskId, userId, null)
    }

    fun findAnonymousTaskQuestionPayload(taskId: Long, anonymousToken: String): TaskQuestionPayload? {
        return findTaskQuestionPayload(taskId, null, anonymousToken)
    }

    private fun findTaskQuestionPayload(taskId: Long, userId: Long?, anonymousToken: String?): TaskQuestionPayload? {
        val localeCode = SupportedContentLocale.currentCode()
        val taskSql = """
            select t.id as task_id, t.scale_id, coalesce(st.scale_name, s.scale_name) as scale_name,
                   t.allow_save_flag, t.allow_retake_flag,
                   t.anonymous_flag, t.allow_timeout_submit_flag, t.start_time, t.end_time, t.status,
                   s.skip_rules_json
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            left join psy_scale_translation st
              on st.scale_id = s.id
             and st.locale_code = :localeCode
             and st.review_status = 'APPROVED'
            where t.id = :taskId
        """.trimIndent()
        val taskRows = jdbcTemplate.query(taskSql, mapOf("taskId" to taskId, "localeCode" to localeCode)) { rs, _ ->
            TaskQuestionBase(
                taskId = rs.getLong("task_id"),
                scaleId = rs.getLong("scale_id"),
                scaleName = rs.getString("scale_name"),
                allowSaveFlag = rs.getBoolean("allow_save_flag"),
                allowRetakeFlag = rs.getBoolean("allow_retake_flag"),
                anonymousFlag = rs.getBoolean("anonymous_flag"),
                allowTimeoutSubmitFlag = rs.getBoolean("allow_timeout_submit_flag"),
                startTime = rs.getTimestamp("start_time").toLocalDateTime(),
                endTime = rs.getTimestamp("end_time").toLocalDateTime(),
                taskStatus = rs.getString("status"),
                skipRulesJson = rs.getString("skip_rules_json")
            )
        }
        val task = taskRows.firstOrNull() ?: return null
        val questionSql = """
            select q.id as question_id, q.question_no,
                   coalesce(qt.question_title, q.question_title) as question_title,
                   q.question_type, q.required_flag,
                   q.option_selection_limit, q.slider_min, q.slider_max, q.slider_step,
                   q.text_input_enabled,
                   coalesce(qt.text_input_placeholder, q.text_input_placeholder) as text_input_placeholder,
                   q.matrix_group_code, q.row_code, q.column_code,
                   o.id as option_id, o.option_code,
                   coalesce(ot.option_label, o.option_label) as option_label,
                   o.score_value, o.exclusive_flag
            from psy_scale_question q
            left join psy_scale_question_translation qt
              on qt.question_id = q.id
             and qt.locale_code = :localeCode
             and qt.review_status = 'APPROVED'
            left join psy_scale_option o on o.question_id = q.id
            left join psy_scale_option_translation ot
              on ot.option_id = o.id
             and ot.locale_code = :localeCode
             and ot.review_status = 'APPROVED'
            where q.scale_id = :scaleId
            order by q.sort_no asc, q.question_no asc, o.sort_no asc, o.id asc
        """.trimIndent()
        val questionMap = linkedMapOf<Long, MutableList<TaskQuestionOption>>()
        val questionMeta = linkedMapOf<Long, TaskQuestionItem>()
        jdbcTemplate.query(questionSql, mapOf("scaleId" to task.scaleId, "localeCode" to localeCode)) { rs ->
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
        val draftInfo = if (anonymousToken == null) {
            userId?.let { findDraftAnswerSheetInfo(taskId, it) }
        } else {
            findAnonymousDraftAnswerSheetInfo(taskId, anonymousToken)
        }
        val draftAnswers = draftInfo
            ?.let { info ->
                loadAnswerItems(info.answerSheetId).map { answer ->
                    TaskDraftAnswerItem(
                        questionId = answer.questionId,
                        optionId = answer.optionId,
                        answerText = answer.answerText,
                        answerValue = answer.answerValue
                    )
                }
            }
            .orEmpty()
        return TaskQuestionPayload(
            taskId = task.taskId,
            scaleId = task.scaleId,
            scaleName = task.scaleName,
            allowSaveFlag = task.allowSaveFlag,
            allowRetakeFlag = task.allowRetakeFlag,
            anonymousFlag = task.anonymousFlag,
            allowTimeoutSubmitFlag = task.allowTimeoutSubmitFlag,
            startTime = task.startTime,
            endTime = task.endTime,
            taskStatus = task.taskStatus,
            draftAnswerSheetId = draftInfo?.answerSheetId,
            draftVersionNo = draftInfo?.versionNo,
            draftAnswers = draftAnswers,
            skipRules = parseSkipRules(task.skipRulesJson),
            questions = questions
        )
    }

    private fun parseSkipRules(json: String?): List<TaskSkipRule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val node = objectMapper.readTree(json)
            node.mapNotNull { ruleNode ->
                val whenQuestionNo = ruleNode.path("whenQuestionNo").takeIf { it.isNumber }?.intValue()
                    ?: return@mapNotNull null
                val whenOptionCode = ruleNode.path("whenOptionCode").asText()
                val skipQuestionNos = ruleNode.path("skipQuestionNos").mapNotNull { questionNo ->
                    questionNo.takeIf { it.isNumber }?.intValue()
                }
                if (whenOptionCode.isBlank() || skipQuestionNos.isEmpty()) return@mapNotNull null
                TaskSkipRule(whenQuestionNo, whenOptionCode, skipQuestionNos)
            }
        }.getOrDefault(emptyList())
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
                  (cast(:groupId as bigint) is not null and target_type = 'GROUP' and target_id = :groupId)
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
            select id, version_no, response_locale_code, start_time
            from psy_assessment_answer_sheet
            where task_id = :taskId and user_id = :userId and answer_status = 'DRAFT'
            order by id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId, "userId" to userId)) { rs, _ ->
            DraftAnswerSheetInfo(
                answerSheetId = rs.getLong("id"),
                versionNo = rs.getInt("version_no"),
                responseLocaleCode = rs.getString("response_locale_code"),
                startTime = rs.getTimestamp("start_time")?.toLocalDateTime()
            )
        }
            .firstOrNull()
    }

    fun findAnonymousDraftAnswerSheetInfo(taskId: Long, anonymousToken: String): DraftAnswerSheetInfo? {
        val sql = """
            select id, version_no, response_locale_code, start_time
            from psy_assessment_answer_sheet
            where task_id = :taskId
              and user_id is null
              and anonymous_token = :anonymousToken
              and answer_status = 'DRAFT'
            order by id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId, "anonymousToken" to anonymousToken)) { rs, _ ->
            DraftAnswerSheetInfo(
                answerSheetId = rs.getLong("id"),
                versionNo = rs.getInt("version_no"),
                responseLocaleCode = rs.getString("response_locale_code"),
                startTime = rs.getTimestamp("start_time")?.toLocalDateTime()
            )
        }.firstOrNull()
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

    fun hasSubmittedAnonymousAnswerSheet(taskId: Long, anonymousToken: String): Boolean =
        (jdbcTemplate.queryForObject(
            """
            select count(1)
            from psy_assessment_answer_sheet
            where task_id = :taskId
              and user_id is null
              and anonymous_token = :anonymousToken
              and answer_status = 'SUBMITTED'
            """.trimIndent(),
            mapOf("taskId" to taskId, "anonymousToken" to anonymousToken),
            Long::class.java
        ) ?: 0L) > 0

    fun createDraftAnswerSheetIfAbsent(taskId: Long, scaleId: Long, userId: Long): Long? =
        createDraftAnswerSheetIfAbsent(taskId, scaleId, userId, null)

    fun createAnonymousDraftAnswerSheetIfAbsent(taskId: Long, scaleId: Long, anonymousToken: String): Long? =
        createDraftAnswerSheetIfAbsent(taskId, scaleId, null, anonymousToken)

    /**
     * Creates the first draft without using an exception as the concurrency path.
     *
     * V5's partial unique indexes are the database boundary. A competing request
     * waits for the winning transaction and then receives no row from RETURNING,
     * leaving the PostgreSQL transaction usable so the service can return a
     * stable version-conflict response.
     */
    private fun createDraftAnswerSheetIfAbsent(
        taskId: Long,
        scaleId: Long,
        userId: Long?,
        anonymousToken: String?
    ): Long? {
        val now = LocalDateTime.now()
        val conflictTarget = if (userId != null) {
            "(task_id, user_id) where answer_status = 'DRAFT' and user_id is not null"
        } else {
            "(task_id, anonymous_token) where answer_status = 'DRAFT' and user_id is null and anonymous_token is not null"
        }
        val sql = """
            insert into psy_assessment_answer_sheet (
                tenant_id, task_id, scale_id, user_id, anonymous_token, response_locale_code,
                answer_status, version_no, start_time, created_at, updated_at
            ) values (
                (select tenant_id from psy_assessment_task where id = :taskId),
                :taskId, :scaleId, :userId, :anonymousToken, :responseLocaleCode,
                'DRAFT', 1, :startTime, :createdAt, :updatedAt
            )
            on conflict $conflictTarget do nothing
            returning id
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("taskId", taskId)
            .addValue("scaleId", scaleId)
            .addValue("userId", userId)
            .addValue("anonymousToken", anonymousToken)
            .addValue("responseLocaleCode", SupportedContentLocale.currentCode())
            .addValue("startTime", Timestamp.valueOf(now))
            .addValue("createdAt", Timestamp.valueOf(now))
            .addValue("updatedAt", Timestamp.valueOf(now))
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getLong("id") }.firstOrNull()
    }

    fun findOverdueDraftAnswerSheets(now: LocalDateTime = LocalDateTime.now()): List<OverdueDraftAnswerSheet> {
        val sql = """
            select ans.id as answer_sheet_id, ans.task_id, ans.scale_id, ans.user_id, ans.anonymous_token,
                   ans.response_locale_code
            from psy_assessment_answer_sheet ans
            join psy_assessment_task t on t.id = ans.task_id
            where ans.answer_status = 'DRAFT'
              and t.end_time < :now
              and t.allow_timeout_submit_flag = true
              and (ans.quality_status is null or ans.quality_status != 'INVALID')
              and not exists (
                  select 1
                  from psy_assessment_answer_sheet submitted
                  where submitted.task_id = ans.task_id
                    and (
                        (ans.user_id is not null and submitted.user_id = ans.user_id)
                        or (ans.user_id is null and submitted.user_id is null and submitted.anonymous_token = ans.anonymous_token)
                    )
                    and submitted.answer_status = 'SUBMITTED'
              )
            order by ans.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("now" to Timestamp.valueOf(now))) { rs, _ ->
            OverdueDraftAnswerSheet(
                answerSheetId = rs.getLong("answer_sheet_id"),
                taskId = rs.getLong("task_id"),
                scaleId = rs.getLong("scale_id"),
                userId = rs.getObject("user_id", java.lang.Long::class.java)?.toLong(),
                anonymousToken = rs.getString("anonymous_token"),
                responseLocaleCode = rs.getString("response_locale_code")
            )
        }
    }

    fun deleteDraftAnswerSheetsUpdatedBefore(cutoff: LocalDateTime): Int {
        val sql = """
            delete from psy_assessment_answer_sheet
            where answer_status = 'DRAFT'
              and updated_at < :cutoff
        """.trimIndent()
        return jdbcTemplate.update(sql, mapOf("cutoff" to Timestamp.valueOf(cutoff)))
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
            .addValue("responseLocaleCode", SupportedContentLocale.currentCode())
        val sql = buildString {
            append(
                """
                update psy_assessment_answer_sheet
                set version_no = version_no + 1,
                    response_locale_code = :responseLocaleCode,
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

    fun submitDraftAnswerSheet(answerSheetId: Long, submitToken: String?, expectedVersion: Int?): Int =
        submitDraftAnswerSheetWithLocale(answerSheetId, submitToken, expectedVersion, SupportedContentLocale.currentCode())

    fun submitDraftAnswerSheetWithLocale(
        answerSheetId: Long,
        submitToken: String?,
        expectedVersion: Int?,
        responseLocaleCode: String
    ): Int {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val params = MapSqlParameterSource()
            .addValue("id", answerSheetId)
            .addValue("submitTime", now)
            .addValue("updatedAt", now)
            .addValue("submitToken", submitToken)
            .addValue("responseLocaleCode", responseLocaleCode)
        val sql = buildString {
            append(
                """
                update psy_assessment_answer_sheet target
                set answer_status = 'SUBMITTED',
                    submit_time = :submitTime,
                    submit_token = :submitToken,
                    response_locale_code = coalesce(:responseLocaleCode, response_locale_code),
                    version_no = version_no + 1,
                    updated_at = :updatedAt
                where target.id = :id
                  and target.answer_status = 'DRAFT'
                  and (
                      cast(:submitToken as varchar) is null
                      or not exists (
                          select 1
                          from psy_assessment_answer_sheet existing
                          where existing.id <> target.id
                            and existing.task_id = target.task_id
                            and existing.answer_status = 'SUBMITTED'
                            and existing.submit_token = :submitToken
                            and (
                                (target.user_id is not null and existing.user_id = target.user_id)
                                or (
                                    target.user_id is null
                                    and existing.user_id is null
                                    and existing.anonymous_token = target.anonymous_token
                                )
                            )
                      )
                  )
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
        return findSubmittedResultBySubmitToken(taskId, userId, null, submitToken)
    }

    fun findAnonymousSubmittedResultBySubmitToken(taskId: Long, anonymousToken: String, submitToken: String): AnswerSubmitResult? {
        return findSubmittedResultBySubmitToken(taskId, null, anonymousToken, submitToken)
    }

    private fun findSubmittedResultBySubmitToken(
        taskId: Long,
        userId: Long?,
        anonymousToken: String?,
        submitToken: String
    ): AnswerSubmitResult? {
        val sql = """
            select sh.id as answer_sheet_id, rs.id as result_id, rp.id as report_id, rs.risk_level, sh.version_no
            from psy_assessment_answer_sheet sh
            join psy_assessment_result rs on rs.answer_sheet_id = sh.id and rs.is_current = true
            left join psy_report rp on rp.result_id = rs.id
            where sh.task_id = :taskId
              and ((cast(:userId as bigint) is not null and sh.user_id = :userId)
                   or (cast(:anonymousToken as varchar) is not null and sh.user_id is null and sh.anonymous_token = :anonymousToken))
              and sh.answer_status = 'SUBMITTED'
              and sh.submit_token = :submitToken
            order by rp.id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("taskId" to taskId, "userId" to userId, "anonymousToken" to anonymousToken, "submitToken" to submitToken)
        ) { rs, _ ->
            AnswerSubmitResult(
                answerSheetId = rs.getLong("answer_sheet_id"),
                resultId = rs.getLong("result_id"),
                reportId = rs.getObject("report_id", java.lang.Long::class.java)?.toLong(),
                riskLevel = rs.getString("risk_level"),
                versionNo = rs.getInt("version_no")
            )
        }.firstOrNull()
    }

    fun findLatestSubmittedTaskReport(taskId: Long, userId: Long): SubmittedTaskReportInfo? {
        val sql = """
            select rp.id as report_id, rs.id as result_id, rs.risk_level
            from psy_assessment_answer_sheet sh
            join psy_assessment_result rs on rs.answer_sheet_id = sh.id and rs.is_current = true
            join psy_report rp on rp.result_id = rs.id
            where sh.task_id = :taskId
              and sh.user_id = :userId
              and sh.answer_status = 'SUBMITTED'
            order by rp.id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId, "userId" to userId)) { rs, _ ->
            SubmittedTaskReportInfo(
                reportId = rs.getLong("report_id"),
                resultId = rs.getLong("result_id"),
                riskLevel = rs.getString("risk_level")
            )
        }.firstOrNull()
    }

    fun findRescoreContextByResultId(resultId: Long, tenantId: Long?): AnswerSheetRescoreContext? {
        val sql = """
            select sh.id as answer_sheet_id,
                   sh.task_id,
                   sh.scale_id,
                   sh.user_id,
                   rs.id as result_id,
                   rs.risk_level,
                   rs.calculation_version,
                   sh.response_locale_code
            from psy_assessment_result rs
            join psy_assessment_answer_sheet sh on sh.id = rs.answer_sheet_id
            where rs.id = :resultId
              and sh.answer_status = 'SUBMITTED'
              and sh.user_id is not null
              and rs.is_current = true
              and (cast(:tenantId as bigint) is null or sh.tenant_id = :tenantId)
            for update of rs
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("resultId" to resultId, "tenantId" to tenantId)) { rs, _ ->
            AnswerSheetRescoreContext(
                answerSheetId = rs.getLong("answer_sheet_id"),
                taskId = rs.getLong("task_id"),
                scaleId = rs.getLong("scale_id"),
                userId = rs.getLong("user_id"),
                resultId = rs.getLong("result_id"),
                previousRiskLevel = rs.getString("risk_level"),
                calculationVersion = rs.getInt("calculation_version"),
                responseLocaleCode = rs.getString("response_locale_code")
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

    fun loadOptionScoreMap(answers: List<AnswerItemRequest>): Map<Long, BigDecimal> =
        loadOptionScores(answers.mapNotNull { it.optionId }.distinct())

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
            select id, question_type, dimension_id, reverse_score_flag, weight_value,
                   (
                       select count(*)
                       from psy_scale_question dimension_question
                       where dimension_question.scale_id = :scaleId
                         and dimension_question.dimension_id is not distinct from question.dimension_id
                   ) as dimension_question_count
            from psy_scale_question question
            where question.scale_id = :scaleId and question.id in (:questionIds)
        """.trimIndent()
        val metaMap = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId, "questionIds" to questionIds)) { rs, _ ->
            rs.getLong("id") to ScoringQuestionMeta(
                questionType = rs.getString("question_type"),
                dimensionId = rs.getObject("dimension_id", java.lang.Long::class.java)?.toLong(),
                reverseScoreFlag = rs.getBoolean("reverse_score_flag"),
                weightValue = rs.getBigDecimal("weight_value") ?: BigDecimal.ONE,
                dimensionQuestionCount = rs.getInt("dimension_question_count")
            )
        }.toMap()
        return answers.groupBy { it.questionId }.mapNotNull { (questionId, groupedAnswers) ->
            val meta = metaMap[questionId] ?: return@mapNotNull null
            val rawScore = when (meta.questionType) {
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
                dimensionId = meta.dimensionId,
                reverseScoreFlag = meta.reverseScoreFlag,
                weightValue = meta.weightValue,
                rawScore = rawScore,
                selectedOptionIds = groupedAnswers.mapNotNull { it.optionId }.distinct(),
                answerValue = groupedAnswers.firstOrNull()?.answerValue,
                answerText = groupedAnswers.firstOrNull()?.answerText,
                dimensionQuestionCount = meta.dimensionQuestionCount
            )
        }
    }

    fun loadScaleScoringContext(scaleId: Long, userId: Long?): Pair<ScaleScoringContext, NormMatchingContext?> {
        val sql = """
            select scale.score_method,
                   scale.score_coefficient,
                   scale.applicable_target,
                   scale.norm_default_group,
                   scale.high_risk_warning_enabled,
                   coalesce(quality.missing_answer_policy, 'REJECT') as missing_answer_policy,
                   coalesce(quality.max_missing_ratio, 0) as max_missing_ratio,
                   quality.minimum_duration_seconds,
                   quality.maximum_duration_seconds,
                   coalesce(quality.invalid_result_action, 'INVALIDATE') as invalid_result_action,
                   coalesce(quality.require_all_required_answers, true) as require_all_required_answers,
                   (select count(*) from psy_scale_question question where question.scale_id = scale.id) as total_question_count,
                   coalesce((select sum(question.weight_value) from psy_scale_question question where question.scale_id = scale.id), 0) as total_weight
            from psy_scale scale
            left join psy_scale_quality_policy quality on quality.scale_id = scale.id
            where scale.id = :scaleId
        """.trimIndent()
        val scaleContext = jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleScoringContext(
                scoreMethod = rs.getString("score_method"),
                scoreCoefficient = rs.getBigDecimal("score_coefficient"),
                applicableTarget = rs.getString("applicable_target"),
                normDefaultGroup = rs.getString("norm_default_group"),
                highRiskWarningEnabled = rs.getBoolean("high_risk_warning_enabled"),
                qualityPolicy = ScalePackageQualityPolicy(
                    missingAnswerPolicy = rs.getString("missing_answer_policy"),
                    maxMissingRatio = rs.getBigDecimal("max_missing_ratio"),
                    minimumDurationSeconds = rs.getObject("minimum_duration_seconds", java.lang.Integer::class.java)?.toInt(),
                    maximumDurationSeconds = rs.getObject("maximum_duration_seconds", java.lang.Integer::class.java)?.toInt(),
                    invalidResultAction = rs.getString("invalid_result_action"),
                    requireAllRequiredAnswers = rs.getBoolean("require_all_required_answers")
                ),
                totalQuestionCount = rs.getInt("total_question_count"),
                totalWeight = rs.getBigDecimal("total_weight") ?: BigDecimal.ZERO
            )
        }.firstOrNull() ?: ScaleScoringContext("SIMPLE_SUM", BigDecimal.ONE, null, null, false)
        val normContext = userId?.let { loadNormMatchingContext(it, scaleContext) }
        return scaleContext to normContext
    }

    /**
     * Returns the immutable quality policy for submission validation. A missing
     * row is intentionally represented as null so callers and legacy tests can
     * apply the safe REJECT/default policy without inventing governance data.
     */
    fun loadScaleQualityPolicy(scaleId: Long): ScalePackageQualityPolicy? {
        val sql = """
            select missing_answer_policy, max_missing_ratio, minimum_duration_seconds,
                   maximum_duration_seconds, invalid_result_action, require_all_required_answers
            from psy_scale_quality_policy
            where scale_id = :scaleId
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScalePackageQualityPolicy(
                missingAnswerPolicy = rs.getString("missing_answer_policy"),
                maxMissingRatio = rs.getBigDecimal("max_missing_ratio"),
                minimumDurationSeconds = rs.getObject("minimum_duration_seconds", java.lang.Integer::class.java)?.toInt(),
                maximumDurationSeconds = rs.getObject("maximum_duration_seconds", java.lang.Integer::class.java)?.toInt(),
                invalidResultAction = rs.getString("invalid_result_action"),
                requireAllRequiredAnswers = rs.getBoolean("require_all_required_answers")
            )
        }.firstOrNull()
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

    fun findDimensionReportMeta(
        dimensionIds: Collection<Long>,
        localeCode: String = SupportedContentLocale.currentCode()
    ): Map<Long, DimensionReportMeta> {
        val ids = dimensionIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val sql = """
            select dimension.id,
                   dimension.dimension_code,
                   coalesce(translation.dimension_name, dimension.dimension_name) as dimension_name,
                   dimension.sort_no
            from psy_scale_dimension dimension
            left join psy_scale_dimension_translation translation
              on translation.dimension_id = dimension.id
             and translation.locale_code = :localeCode
             and translation.review_status = 'APPROVED'
            where dimension.id in (:ids)
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("ids" to ids, "localeCode" to localeCode)) { rs, _ ->
            val meta = DimensionReportMeta(
                dimensionId = rs.getLong("id"),
                dimensionCode = rs.getString("dimension_code"),
                dimensionName = rs.getString("dimension_name"),
                sortNo = rs.getInt("sort_no")
            )
            meta.dimensionId to meta
        }.toMap()
    }

    fun createRescoreResult(
        previousResultId: Long,
        rescoredBy: Long,
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
        highRiskRuleCode: String? = null,
        scoringTraceJson: String? = null
    ): Long? {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val deactivated = jdbcTemplate.update(
            """
            update psy_assessment_result
            set is_current = false
            where id = :previousResultId
              and is_current = true
            """.trimIndent(),
            mapOf("previousResultId" to previousResultId)
        )
        if (deactivated != 1) return null

        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """
            insert into psy_assessment_result (
                answer_sheet_id, total_score, risk_level, warning_flag, result_summary,
                score_source, standard_score, z_score, t_score, norm_code, high_risk_flag, high_risk_rule_code,
                quality_status, quality_issue_codes, quality_missing_ratio, quality_duration_seconds,
                calculation_version, is_current, supersedes_result_id, rescored_by,
                scale_content_hash, scoring_engine_version, scoring_trace_json, scored_at, created_at
            )
            select answer_sheet_id, :totalScore, :riskLevel, :warningFlag, :resultSummary,
                   :scoreSource, :standardScore, :zScore, :tScore, :normCode, :highRiskFlag, :highRiskRuleCode,
                   quality_status, quality_issue_codes, quality_missing_ratio, quality_duration_seconds,
                   calculation_version + 1, true, id, :rescoredBy,
                   scale_content_hash, scoring_engine_version, cast(:scoringTraceJson as jsonb), :scoredAt, :createdAt
            from psy_assessment_result
            where id = :previousResultId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("previousResultId", previousResultId)
                .addValue("rescoredBy", rescoredBy)
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
                .addValue("scoringTraceJson", scoringTraceJson)
                .addValue("scoredAt", now)
                .addValue("createdAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong()
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
        highRiskRuleCode: String? = null,
        scoringTraceJson: String? = null
    ): Long {
        val sql = """
            insert into psy_assessment_result (
                answer_sheet_id, total_score, risk_level, warning_flag, result_summary,
                score_source, standard_score, z_score, t_score, norm_code, high_risk_flag, high_risk_rule_code,
                quality_status, quality_issue_codes, quality_missing_ratio, quality_duration_seconds,
                scale_content_hash, scoring_trace_json, scored_at, created_at
            ) values (
                :answerSheetId, :totalScore, :riskLevel, :warningFlag, :resultSummary,
                :scoreSource, :standardScore, :zScore, :tScore, :normCode, :highRiskFlag, :highRiskRuleCode,
                (
                    select quality_status from psy_assessment_answer_sheet where id = :answerSheetId
                ),
                (
                    select quality_issue_codes from psy_assessment_answer_sheet where id = :answerSheetId
                ),
                (
                    select quality_missing_ratio from psy_assessment_answer_sheet where id = :answerSheetId
                ),
                (
                    select quality_duration_seconds from psy_assessment_answer_sheet where id = :answerSheetId
                ),
                (
                    select task.scale_content_hash
                    from psy_assessment_answer_sheet sheet
                    join psy_assessment_task task on task.id = sheet.task_id
                    where sheet.id = :answerSheetId
                ),
                cast(:scoringTraceJson as jsonb), :scoredAt, :createdAt
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
                .addValue("scoringTraceJson", scoringTraceJson)
                .addValue("scoredAt", now)
                .addValue("createdAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create result")
    }

    fun updateAnswerSheetQuality(answerSheetId: Long, assessment: AnswerQualityAssessment): Int {
        return jdbcTemplate.update(
            """
            update psy_assessment_answer_sheet
            set quality_status = :qualityStatus,
                quality_issue_codes = :qualityIssueCodes,
                quality_missing_ratio = :qualityMissingRatio,
                quality_duration_seconds = :qualityDurationSeconds,
                updated_at = :updatedAt
            where id = :answerSheetId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("answerSheetId", answerSheetId)
                .addValue("qualityStatus", assessment.status)
                .addValue("qualityIssueCodes", assessment.issueCodes.joinToString(",").takeIf { it.isNotBlank() })
                .addValue("qualityMissingRatio", assessment.missingRatio)
                .addValue("qualityDurationSeconds", assessment.durationSeconds)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }

    fun createReport(resultId: Long, authorUserId: Long, title: String, content: String): Long {
        val sql = """
            insert into psy_report (
                result_id, report_type, author_user_id, report_title, report_content,
                locale_code, version_no, created_at, updated_at
            )
            select :resultId, 'SYSTEM', :authorUserId, :reportTitle, :reportContent,
                   sh.response_locale_code, 1, :createdAt, :updatedAt
            from psy_assessment_result ar
            join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
            where ar.id = :resultId
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
            with warning_context as (
                select
                    a.tenant_id,
                    case
                        when upper(:warningLevel) in ('CRITICAL', 'P0') then 'P0'
                        when upper(:warningLevel) in ('HIGH', 'P1') then 'P1'
                        when upper(:warningLevel) in ('MODERATE', 'MEDIUM', 'ATTENTION', 'P2') then 'P2'
                        else 'P3'
                    end as risk_category
                from psy_assessment_result r
                join psy_assessment_answer_sheet a on a.id = r.answer_sheet_id
                where r.id = :resultId
            ), selected_policy as (
                select policy.*
                from psy_safety_response_policy policy
                cross join warning_context context
                where policy.active_flag = true
                  and policy.status = 'APPROVED'
                  and policy.risk_category = context.risk_category
                  and (policy.tenant_id = context.tenant_id or policy.tenant_id is null)
                order by (policy.tenant_id is not null) desc, policy.version_no desc
                limit 1
            )
            insert into psy_warning_record (
                tenant_id, result_id, warning_level, warning_priority, warning_reason, status,
                deadline_time, safety_policy_id, safety_policy_version,
                policy_resolution_status, safety_policy_snapshot, created_at, updated_at
            )
            select
                context.tenant_id,
                :resultId,
                :warningLevel,
                context.risk_category,
                :warningReason,
                :status,
                case when policy.id is null then null
                    else cast(:createdAt as timestamp) + (policy.first_response_minutes * interval '1 minute') end,
                policy.id,
                policy.version_no,
                case when policy.id is null then 'MISSING' else 'RESOLVED' end,
                case when policy.id is null then null else jsonb_build_object(
                    'policyCode', policy.policy_code,
                    'versionNo', policy.version_no,
                    'riskCategory', policy.risk_category,
                    'firstResponseMinutes', policy.first_response_minutes,
                    'escalationMinutes', policy.escalation_minutes,
                    'followUpMinutes', policy.follow_up_minutes,
                    'responsibleRole', policy.responsible_role,
                    'backupRole', policy.backup_role,
                    'emergencyContactText', policy.emergency_contact_text,
                    'approvedBy', policy.approved_by,
                    'professionalReviewerId', policy.professional_reviewer_id,
                    'approvedAt', policy.approved_at
                ) end,
                :createdAt,
                :updatedAt
            from warning_context context
            left join selected_policy policy on true
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("resultId", resultId)
                .addValue("warningLevel", warningLevel)
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
