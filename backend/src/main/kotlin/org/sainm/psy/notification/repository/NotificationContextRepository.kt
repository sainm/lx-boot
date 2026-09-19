package org.sainm.psy.notification.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Minimal lookups used to re-render legacy notifications that were written
 * before the dispatcher started storing every parameter in ``payload_json``.
 */
@Repository
class NotificationContextRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findTaskName(taskId: Long): String? =
        jdbcTemplate.query(
            "select task_name from psy_assessment_task where id = :taskId",
            mapOf("taskId" to taskId)
        ) { rs, _ -> rs.getString("task_name") }.firstOrNull()

    fun findInterventionWarningId(interventionId: Long): Long? =
        jdbcTemplate.query(
            "select warning_id from psy_intervention_record where id = :interventionId",
            mapOf("interventionId" to interventionId)
        ) { rs, _ -> rs.getLong("warning_id") }.firstOrNull()
}
