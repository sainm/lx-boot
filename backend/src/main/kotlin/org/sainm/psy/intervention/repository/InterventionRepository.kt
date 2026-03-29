package org.sainm.psy.intervention.repository

import org.sainm.psy.intervention.domain.InterventionDetail
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class InterventionRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun existsWarningById(warningId: Long): Boolean =
        (jdbcTemplate.queryForObject(
            "select count(1) from psy_warning_record where id = :warningId",
            mapOf("warningId" to warningId),
            Long::class.java
        ) ?: 0L) > 0

    fun findDetailById(interventionId: Long): InterventionDetail? {
        val sql = """
            select id, warning_id, counselor_user_id, current_status, plan_text, close_summary, created_at, updated_at
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
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }.firstOrNull()
    }

    fun findByWarningId(warningId: Long): InterventionDetail? {
        val sql = """
            select id, warning_id, counselor_user_id, current_status, plan_text, close_summary, created_at, updated_at
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
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }.firstOrNull()
    }

    fun createIntervention(warningId: Long, counselorUserId: Long?, planText: String, createdBy: Long): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_intervention_record (
                warning_id, counselor_user_id, current_status, plan_text, created_at, updated_at
            ) values (
                :warningId, :counselorUserId, :currentStatus, :planText, :createdAt, :updatedAt
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
        insertStatusLog(interventionId, null, "PROCESSING", "创建干预记录", createdBy)
        return interventionId
    }

    fun closeIntervention(interventionId: Long, closeSummary: String, changedBy: Long): Boolean {
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
                    updated_at = :updatedAt
                where id = :interventionId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("interventionId", interventionId)
                .addValue("closeSummary", closeSummary)
                .addValue("updatedAt", now)
        )
        if (updated > 0) {
            insertStatusLog(interventionId, current.currentStatus, "CLOSED", closeSummary, changedBy)
        }
        return updated > 0
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
                    intervention_id, from_status, to_status, remark, changed_by, changed_at
                ) values (
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
