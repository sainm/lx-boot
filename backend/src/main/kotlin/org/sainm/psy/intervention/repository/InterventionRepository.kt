package org.sainm.psy.intervention.repository

import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.intervention.domain.InterventionDetail
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class InterventionRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val messages: LocalizedMessages
) {

    data class RetestTaskSeed(
        val warningId: Long,
        val userId: Long,
        val scaleId: Long,
        val sourceTaskId: Long,
        val sourceTaskName: String
    )

    fun existsWarningById(warningId: Long): Boolean =
        (jdbcTemplate.queryForObject(
            "select count(1) from psy_warning_record where id = :warningId",
            mapOf("warningId" to warningId),
            Long::class.java
        ) ?: 0L) > 0

    fun findDetailById(interventionId: Long): InterventionDetail? {
        val sql = """
            select id, warning_id, counselor_user_id, current_status, plan_text, close_summary,
                   need_retest_flag, retest_task_id, created_at, updated_at, tenant_id
            from psy_intervention_record
            where id = :interventionId
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("interventionId" to interventionId)) { rs, _ ->
            InterventionDetail(
                id = rs.getLong("id"),
                warningId = rs.getLong("warning_id"),
                counselorUserId = rs.getObject("counselor_user_id", java.lang.Long::class.java)?.toLong(),
                currentStatus = rs.getString("current_status"),
                planText = rs.getString("plan_text"),
                closeSummary = rs.getString("close_summary"),
                needRetestFlag = rs.getBoolean("need_retest_flag"),
                retestTaskId = rs.getObject("retest_task_id", java.lang.Long::class.java)?.toLong(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
                tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong()
            )
        }.firstOrNull()
    }

    fun findByWarningId(warningId: Long): InterventionDetail? {
        val sql = """
            select id, warning_id, counselor_user_id, current_status, plan_text, close_summary,
                   need_retest_flag, retest_task_id, created_at, updated_at, tenant_id
            from psy_intervention_record
            where warning_id = :warningId
            order by id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("warningId" to warningId)) { rs, _ ->
            InterventionDetail(
                id = rs.getLong("id"),
                warningId = rs.getLong("warning_id"),
                counselorUserId = rs.getObject("counselor_user_id", java.lang.Long::class.java)?.toLong(),
                currentStatus = rs.getString("current_status"),
                planText = rs.getString("plan_text"),
                closeSummary = rs.getString("close_summary"),
                needRetestFlag = rs.getBoolean("need_retest_flag"),
                retestTaskId = rs.getObject("retest_task_id", java.lang.Long::class.java)?.toLong(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
                tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong()
            )
        }.firstOrNull()
    }

    fun createIntervention(warningId: Long, counselorUserId: Long?, planText: String, createdBy: Long): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_intervention_record (
                tenant_id, warning_id, counselor_user_id, current_status, plan_text, need_retest_flag, created_at, updated_at
            ) values (
                (select tenant_id from psy_warning_record where id = :warningId),
                :warningId, :counselorUserId, :currentStatus, :planText, false, :createdAt, :updatedAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("warningId", warningId)
                .addValue("counselorUserId", counselorUserId)
                .addValue("currentStatus", "PROCESSING")
                .addValue("planText", planText)
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        val interventionId = keyHolder.key?.toLong() ?: error("failed to create intervention")
        insertStatusLog(interventionId, null, "PROCESSING", messages.get("intervention.log.created"), createdBy)
        return interventionId
    }

    fun closeIntervention(interventionId: Long, closeSummary: String, needRetest: Boolean, changedBy: Long): Boolean {
        val current = findDetailById(interventionId) ?: return false
        if (current.currentStatus == "CLOSED") {
            return true
        }
        val now = Timestamp.valueOf(LocalDateTime.now())
        val updated = jdbcTemplate.update(
            """
                update psy_intervention_record
                set current_status = 'CLOSED',
                    close_summary = :closeSummary,
                    need_retest_flag = :needRetestFlag,
                    updated_at = :updatedAt
                where id = :interventionId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("interventionId", interventionId)
                .addValue("closeSummary", closeSummary)
                .addValue("needRetestFlag", needRetest)
                .addValue("updatedAt", now)
        )
        if (updated > 0) {
            insertStatusLog(interventionId, current.currentStatus, "CLOSED", closeSummary, changedBy)
        }
        return updated > 0
    }

    fun markRetestTaskCreated(interventionId: Long, taskId: Long) {
        jdbcTemplate.update(
            """
                update psy_intervention_record
                set retest_task_id = :taskId,
                    need_retest_flag = true,
                    updated_at = :updatedAt
                where id = :interventionId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("interventionId", interventionId)
                .addValue("taskId", taskId)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }

    fun findRetestTaskSeed(warningId: Long): RetestTaskSeed? {
        val sql = """
            select w.id as warning_id,
                   sh.user_id,
                   sh.scale_id,
                   sh.task_id as source_task_id,
                   t.task_name as source_task_name
            from psy_warning_record w
            join psy_assessment_result r on r.id = w.result_id
            join psy_assessment_answer_sheet sh on sh.id = r.answer_sheet_id
            join psy_assessment_task t on t.id = sh.task_id
            where w.id = :warningId
              and sh.user_id is not null
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("warningId" to warningId)) { rs, _ ->
            RetestTaskSeed(
                warningId = rs.getLong("warning_id"),
                userId = rs.getLong("user_id"),
                scaleId = rs.getLong("scale_id"),
                sourceTaskId = rs.getLong("source_task_id"),
                sourceTaskName = rs.getString("source_task_name")
            )
        }.firstOrNull()
    }

    private fun insertStatusLog(
        interventionId: Long,
        fromStatus: String?,
        toStatus: String,
        remark: String?,
        changedBy: Long
    ) {
        jdbcTemplate.update(
            """
                insert into psy_intervention_status_log (
                    tenant_id, intervention_id, from_status, to_status, remark, changed_by, changed_at
                ) values (
                    (select tenant_id from psy_intervention_record where id = :interventionId),
                    :interventionId, :fromStatus, :toStatus, :remark, :changedBy, :changedAt
                )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("interventionId", interventionId)
                .addValue("fromStatus", fromStatus)
                .addValue("toStatus", toStatus)
                .addValue("remark", remark)
                .addValue("changedBy", changedBy)
                .addValue("changedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }
}
