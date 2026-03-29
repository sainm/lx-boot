package org.sainm.psy.assessment.repository

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.domain.AssessmentTaskAssignment
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.MyAssessmentTask
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class AssessmentTaskRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findPage(query: TaskListQuery): Pair<List<AssessmentTaskSummary>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val params = MapSqlParameterSource()
            .addValue("taskName", query.taskName?.trim()?.takeIf { it.isNotEmpty() }?.let { "%$it%" })
            .addValue("status", query.status?.trim()?.takeIf { it.isNotEmpty() })
            .addValue("limit", query.size)
            .addValue("offset", offset)
        val whereClause = buildString {
            append(" where 1 = 1 ")
            if (params.hasValue("taskName")) append(" and t.task_name like :taskName ")
            if (params.hasValue("status")) append(" and t.status = :status ")
        }
        val listSql = """
            select t.id, t.task_name, t.scale_id, s.scale_name, t.task_mode, t.anonymous_flag,
                   t.start_time, t.end_time, t.status
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            $whereClause
            order by t.id desc
            limit :limit offset :offset
        """.trimIndent()
        val countSql = """
            select count(1)
            from psy_assessment_task t
            $whereClause
        """.trimIndent()
        val list = jdbcTemplate.query(listSql, params, assessmentTaskSummaryRowMapper)
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    fun create(request: CreateAssessmentTaskRequest, createdBy: Long): Long {
        val now = LocalDateTime.now()
        val sql = """
            insert into psy_assessment_task (
                task_name, scale_id, task_mode, anonymous_flag, allow_save_flag,
                allow_timeout_submit_flag, allow_retake_flag, start_time, end_time,
                status, created_by, created_at, updated_at
            ) values (
                :taskName, :scaleId, :taskMode, :anonymousFlag, :allowSaveFlag,
                :allowTimeoutSubmitFlag, :allowRetakeFlag, :startTime, :endTime,
                :status, :createdBy, :createdAt, :updatedAt
            )
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("taskName", request.taskName.trim())
            .addValue("scaleId", request.scaleId)
            .addValue("taskMode", request.taskMode.trim().uppercase())
            .addValue("anonymousFlag", request.anonymousFlag)
            .addValue("allowSaveFlag", request.allowSaveFlag)
            .addValue("allowTimeoutSubmitFlag", request.allowTimeoutSubmitFlag)
            .addValue("allowRetakeFlag", request.allowRetakeFlag)
            .addValue("startTime", Timestamp.valueOf(request.startTime))
            .addValue("endTime", Timestamp.valueOf(request.endTime))
            .addValue("status", "DRAFT")
            .addValue("createdBy", createdBy)
            .addValue("createdAt", Timestamp.valueOf(now))
            .addValue("updatedAt", Timestamp.valueOf(now))
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder, arrayOf("id"))
        return keyHolder.key?.toLong() ?: error("failed to create task")
    }

    fun findDetailById(taskId: Long): AssessmentTaskDetail? {
        val sql = """
            select t.id, t.task_name, t.scale_id, s.scale_name, t.task_mode, t.anonymous_flag,
                   t.allow_save_flag, t.allow_timeout_submit_flag, t.allow_retake_flag,
                   t.start_time, t.end_time, t.status, t.created_by, t.created_at
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            where t.id = :taskId
        """.trimIndent()
        val rows = jdbcTemplate.query(sql, mapOf("taskId" to taskId)) { rs, _ ->
            AssessmentTaskDetail(
                id = rs.getLong("id"),
                taskName = rs.getString("task_name"),
                scaleId = rs.getLong("scale_id"),
                scaleName = rs.getString("scale_name"),
                taskMode = rs.getString("task_mode"),
                anonymousFlag = rs.getBoolean("anonymous_flag"),
                allowSaveFlag = rs.getBoolean("allow_save_flag"),
                allowTimeoutSubmitFlag = rs.getBoolean("allow_timeout_submit_flag"),
                allowRetakeFlag = rs.getBoolean("allow_retake_flag"),
                startTime = rs.getTimestamp("start_time").toLocalDateTime(),
                endTime = rs.getTimestamp("end_time").toLocalDateTime(),
                status = rs.getString("status"),
                createdBy = rs.getObject("created_by", java.lang.Long::class.java)?.toLong(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                assignments = emptyList()
            )
        }
        val detail = rows.firstOrNull() ?: return null
        return detail.copy(assignments = findAssignmentsByTaskId(taskId))
    }

    fun existsById(taskId: Long): Boolean =
        (jdbcTemplate.queryForObject(
            "select count(1) from psy_assessment_task where id = :taskId",
            mapOf("taskId" to taskId),
            Long::class.java
        ) ?: 0L) > 0

    fun existsScaleById(scaleId: Long): Boolean =
        (jdbcTemplate.queryForObject(
            "select count(1) from psy_scale where id = :scaleId",
            mapOf("scaleId" to scaleId),
            Long::class.java
        ) ?: 0L) > 0

    fun assignTargets(taskId: Long, targetType: String, targetIds: List<Long>, assignedBy: Long) {
        val sql = """
            insert into psy_assessment_task_assignment (
                task_id, target_type, target_id, assigned_by, assigned_at
            ) values (
                :taskId, :targetType, :targetId, :assignedBy, :assignedAt
            )
        """.trimIndent()
        val now = Timestamp.valueOf(LocalDateTime.now())
        val batchParams = targetIds.distinct().map { targetId ->
            MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("targetType", targetType)
                .addValue("targetId", targetId)
                .addValue("assignedBy", assignedBy)
                .addValue("assignedAt", now)
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, batchParams)
    }

    fun findMyTasks(userId: Long, groupId: Long?): List<MyAssessmentTask> {
        val sql = """
            select distinct t.id as task_id, t.task_name, t.scale_id, s.scale_name, t.end_time, t.status
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            join psy_assessment_task_assignment a on a.task_id = t.id
            where (
                (a.target_type = 'USER' and a.target_id = :userId)
                or
                (:groupId is not null and a.target_type = 'GROUP' and a.target_id = :groupId)
            )
            order by t.end_time asc, t.id desc
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("userId" to userId, "groupId" to groupId)
        ) { rs, _ ->
            MyAssessmentTask(
                taskId = rs.getLong("task_id"),
                taskName = rs.getString("task_name"),
                scaleId = rs.getLong("scale_id"),
                scaleName = rs.getString("scale_name"),
                endTime = rs.getTimestamp("end_time").toLocalDateTime(),
                status = rs.getString("status")
            )
        }
    }

    private fun findAssignmentsByTaskId(taskId: Long): List<AssessmentTaskAssignment> {
        val sql = """
            select id, task_id, target_type, target_id, assigned_by, assigned_at
            from psy_assessment_task_assignment
            where task_id = :taskId
            order by id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("taskId" to taskId)) { rs, _ ->
            AssessmentTaskAssignment(
                id = rs.getLong("id"),
                taskId = rs.getLong("task_id"),
                targetType = rs.getString("target_type"),
                targetId = rs.getLong("target_id"),
                assignedBy = rs.getObject("assigned_by", java.lang.Long::class.java)?.toLong(),
                assignedAt = rs.getTimestamp("assigned_at").toLocalDateTime()
            )
        }
    }

    private val assessmentTaskSummaryRowMapper = RowMapper { rs, _ ->
        AssessmentTaskSummary(
            id = rs.getLong("id"),
            taskName = rs.getString("task_name"),
            scaleId = rs.getLong("scale_id"),
            scaleName = rs.getString("scale_name"),
            taskMode = rs.getString("task_mode"),
            anonymousFlag = rs.getBoolean("anonymous_flag"),
            startTime = rs.getTimestamp("start_time").toLocalDateTime(),
            endTime = rs.getTimestamp("end_time").toLocalDateTime(),
            status = rs.getString("status")
        )
    }
}
