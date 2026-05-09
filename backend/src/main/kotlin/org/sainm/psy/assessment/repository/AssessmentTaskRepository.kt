package org.sainm.psy.assessment.repository

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.api.UpdateAssessmentTaskRequest
import org.sainm.psy.assessment.domain.AssessmentTaskAssignment
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.MyAssessmentTask
import org.sainm.psy.assessment.domain.OverdueTaskNotification
import org.sainm.psy.common.jdbc.addIfNotNull
import org.sainm.psy.common.jdbc.params
import org.sainm.psy.common.jdbc.whereClause
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class AssessmentTaskRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    data class ScaleVersionSnapshot(
        val versionNo: String?,
        val versionGroupId: Long?
    )

    fun findPage(query: TaskListQuery): Pair<List<AssessmentTaskSummary>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val taskName = query.taskName?.trim()?.takeIf(String::isNotEmpty)?.let { "%$it%" }
        val status = query.status?.trim()?.takeIf(String::isNotEmpty)
        val params = params {
            addValue("limit", query.size)
            addValue("offset", offset)
            addIfNotNull("taskName", taskName)
            addIfNotNull("status", status)
        }
        val whereClause = whereClause(
            taskName?.let { "t.task_name like :taskName" },
            status?.let { "t.status = :status" }
        )
        val listSql = """
            select t.id, t.task_name, t.scale_id, s.scale_name, t.task_mode, t.anonymous_flag,
                   coalesce(t.scale_version_no, s.version_no) as scale_version_no,
                   coalesce(t.scale_version_group_id, s.version_group_id, s.id) as scale_version_group_id,
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
        val scaleVersion = findScaleVersionSnapshot(request.scaleId)
        val sql = """
            insert into psy_assessment_task (
                task_name, scale_id, task_mode, anonymous_flag, allow_save_flag,
                allow_timeout_submit_flag, allow_retake_flag, start_time, end_time,
                status, scale_version_no, scale_version_group_id, created_by, created_at, updated_at
            ) values (
                :taskName, :scaleId, :taskMode, :anonymousFlag, :allowSaveFlag,
                :allowTimeoutSubmitFlag, :allowRetakeFlag, :startTime, :endTime,
                :status, :scaleVersionNo, :scaleVersionGroupId, :createdBy, :createdAt, :updatedAt
            )
        """.trimIndent()
        val params = params {
            addValue("taskName", request.taskName.trim())
            addValue("scaleId", request.scaleId)
            addValue("taskMode", request.taskMode.trim().uppercase())
            addValue("anonymousFlag", request.anonymousFlag)
            addValue("allowSaveFlag", request.allowSaveFlag)
            addValue("allowTimeoutSubmitFlag", request.allowTimeoutSubmitFlag)
            addValue("allowRetakeFlag", request.allowRetakeFlag)
            addValue("startTime", Timestamp.valueOf(request.startTime))
            addValue("endTime", Timestamp.valueOf(request.endTime))
            addValue("status", "DRAFT")
            addValue("scaleVersionNo", scaleVersion?.versionNo)
            addValue("scaleVersionGroupId", scaleVersion?.versionGroupId)
            addValue("createdBy", createdBy)
            addValue("createdAt", Timestamp.valueOf(now))
            addValue("updatedAt", Timestamp.valueOf(now))
        }
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder, arrayOf("id"))
        return keyHolder.key?.toLong() ?: error("failed to create task")
    }

    fun findDetailById(taskId: Long): AssessmentTaskDetail? {
        val sql = """
            select t.id, t.task_name, t.scale_id, s.scale_name, t.task_mode, t.anonymous_flag,
                   coalesce(t.scale_version_no, s.version_no) as scale_version_no,
                   coalesce(t.scale_version_group_id, s.version_group_id, s.id) as scale_version_group_id,
                   t.allow_save_flag, t.allow_timeout_submit_flag, t.allow_retake_flag,
                   t.start_time, t.end_time, t.status, t.created_by, t.created_at,
                   t.closed_at, t.closed_by, t.close_reason
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
                scaleVersionNo = rs.getString("scale_version_no"),
                scaleVersionGroupId = rs.getObject("scale_version_group_id", java.lang.Long::class.java)?.toLong(),
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
                closedAt = rs.getTimestamp("closed_at")?.toLocalDateTime(),
                closedBy = rs.getObject("closed_by", java.lang.Long::class.java)?.toLong(),
                closeReason = rs.getString("close_reason"),
                assignments = emptyList()
            )
        }
        val detail = rows.firstOrNull() ?: return null
        return detail.copy(assignments = findAssignmentsByTaskId(taskId))
    }

    fun updateDraft(taskId: Long, request: UpdateAssessmentTaskRequest): Int {
        val scaleVersion = findScaleVersionSnapshot(request.scaleId)
        return jdbcTemplate.update(
            """
            update psy_assessment_task
            set task_name = :taskName,
                scale_id = :scaleId,
                task_mode = :taskMode,
                anonymous_flag = :anonymousFlag,
                allow_save_flag = :allowSaveFlag,
                allow_timeout_submit_flag = :allowTimeoutSubmitFlag,
                allow_retake_flag = :allowRetakeFlag,
                start_time = :startTime,
                end_time = :endTime,
                scale_version_no = :scaleVersionNo,
                scale_version_group_id = :scaleVersionGroupId,
                updated_at = :updatedAt
            where id = :taskId
              and status = 'DRAFT'
            """.trimIndent(),
            params {
                addValue("taskId", taskId)
                addValue("taskName", request.taskName.trim())
                addValue("scaleId", request.scaleId)
                addValue("taskMode", request.taskMode.trim().uppercase())
                addValue("anonymousFlag", request.anonymousFlag)
                addValue("allowSaveFlag", request.allowSaveFlag)
                addValue("allowTimeoutSubmitFlag", request.allowTimeoutSubmitFlag)
                addValue("allowRetakeFlag", request.allowRetakeFlag)
                addValue("startTime", Timestamp.valueOf(request.startTime))
                addValue("endTime", Timestamp.valueOf(request.endTime))
                addValue("scaleVersionNo", scaleVersion?.versionNo)
                addValue("scaleVersionGroupId", scaleVersion?.versionGroupId)
                addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
            }
        )
    }

    fun deleteDraft(taskId: Long): Int {
        jdbcTemplate.update(
            """
            delete from psy_assessment_task_assignment
            where task_id = :taskId
              and exists (
                  select 1
                  from psy_assessment_task t
                  where t.id = :taskId
                    and t.status = 'DRAFT'
              )
            """.trimIndent(),
            mapOf("taskId" to taskId)
        )
        return jdbcTemplate.update(
            """
            delete from psy_assessment_task
            where id = :taskId
              and status = 'DRAFT'
              and not exists (
                  select 1
                  from psy_assessment_answer_sheet ans
                  where ans.task_id = :taskId
              )
            """.trimIndent(),
            mapOf("taskId" to taskId)
        )
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

    private fun findScaleVersionSnapshot(scaleId: Long): ScaleVersionSnapshot? {
        val sql = """
            select version_no, coalesce(version_group_id, id) as version_group_id
            from psy_scale
            where id = :scaleId
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scaleId" to scaleId)) { rs, _ ->
            ScaleVersionSnapshot(
                versionNo = rs.getString("version_no"),
                versionGroupId = rs.getObject("version_group_id", java.lang.Long::class.java)?.toLong()
            )
        }.firstOrNull()
    }

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
            params {
                addValue("taskId", taskId)
                addValue("targetType", targetType)
                addValue("targetId", targetId)
                addValue("assignedBy", assignedBy)
                addValue("assignedAt", now)
            }
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, batchParams)
        activateTask(taskId)
    }

    fun findMyTasks(userId: Long, groupId: Long?): List<MyAssessmentTask> {
        val sql = """
            select distinct
                   t.id as task_id,
                   t.task_name,
                   t.scale_id,
                   s.scale_name,
                   t.end_time,
                   case
                       when exists (
                           select 1
                           from psy_assessment_answer_sheet ans
                           where ans.task_id = t.id
                             and ans.user_id = :userId
                             and ans.answer_status = 'SUBMITTED'
                       ) then 'COMPLETED'
                       when t.end_time < :now then 'OVERDUE'
                       else 'IN_PROGRESS'
                   end as task_status
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            join psy_assessment_task_assignment a on a.task_id = t.id
            where (
                (a.target_type = 'USER' and a.target_id = :userId)
                or
                (:groupId is not null and a.target_type = 'GROUP' and a.target_id = :groupId)
            )
              and t.status <> 'CLOSED'
            order by t.end_time asc, t.id desc
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("userId" to userId, "groupId" to groupId, "now" to Timestamp.valueOf(LocalDateTime.now()))
        ) { rs, _ ->
            MyAssessmentTask(
                taskId = rs.getLong("task_id"),
                taskName = rs.getString("task_name"),
                scaleId = rs.getLong("scale_id"),
                scaleName = rs.getString("scale_name"),
                endTime = rs.getTimestamp("end_time").toLocalDateTime(),
                status = rs.getString("task_status")
            )
        }
    }

    fun markOverdueTasks(now: LocalDateTime): Int =
        jdbcTemplate.update(
            """
            update psy_assessment_task
            set status = 'OVERDUE',
                updated_at = :updatedAt
            where end_time < :now
              and status in ('DRAFT', 'IN_PROGRESS')
            """.trimIndent(),
            mapOf(
                "now" to Timestamp.valueOf(now),
                "updatedAt" to Timestamp.valueOf(now)
            )
        )

    fun findTasksNeedingOverdueNotification(now: LocalDateTime): List<OverdueTaskNotification> {
        val sql = """
            select t.id as task_id, t.task_name, a.target_id as receiver_user_id
            from psy_assessment_task t
            join psy_assessment_task_assignment a on a.task_id = t.id
            where t.end_time < :now
              and t.status = 'OVERDUE'
              and t.overdue_notified_at is null
              and a.target_type = 'USER'
              and not exists (
                  select 1
                  from psy_assessment_answer_sheet ans
                  where ans.task_id = t.id
                    and ans.user_id = a.target_id
                    and ans.answer_status = 'SUBMITTED'
              )
            order by t.id asc, a.target_id asc
        """.trimIndent()
        val rows = jdbcTemplate.query(sql, mapOf("now" to Timestamp.valueOf(now))) { rs, _ ->
            Triple(
                rs.getLong("task_id"),
                rs.getString("task_name"),
                rs.getLong("receiver_user_id")
            )
        }
        return rows
            .groupBy({ it.first }, { it })
            .map { (_, groupedRows) ->
                val firstRow = groupedRows.first()
                OverdueTaskNotification(
                    taskId = firstRow.first,
                    taskName = firstRow.second,
                    receiverUserIds = groupedRows.map { it.third }.distinct()
                )
            }
    }

    fun markOverdueNotificationSent(taskIds: List<Long>, now: LocalDateTime) {
        if (taskIds.isEmpty()) {
            return
        }
        jdbcTemplate.update(
            """
            update psy_assessment_task
            set overdue_notified_at = :notifiedAt,
                updated_at = :updatedAt
            where id in (:taskIds)
            """.trimIndent(),
            mapOf(
                "taskIds" to taskIds.distinct(),
                "notifiedAt" to Timestamp.valueOf(now),
                "updatedAt" to Timestamp.valueOf(now)
            )
        )
    }

    fun closeTask(taskId: Long, closedBy: Long, reason: String): Int {
        val now = Timestamp.valueOf(LocalDateTime.now())
        return jdbcTemplate.update(
            """
            update psy_assessment_task
            set status = 'CLOSED',
                closed_at = :closedAt,
                closed_by = :closedBy,
                close_reason = :closeReason,
                updated_at = :updatedAt
            where id = :taskId
              and status in ('DRAFT', 'IN_PROGRESS', 'OVERDUE')
            """.trimIndent(),
            params {
                addValue("taskId", taskId)
                addValue("closedAt", now)
                addValue("closedBy", closedBy)
                addValue("closeReason", reason)
                addValue("updatedAt", now)
            }
        )
    }

    private fun activateTask(taskId: Long) {
        jdbcTemplate.update(
            """
            update psy_assessment_task
            set status = case when status = 'DRAFT' then 'IN_PROGRESS' else status end,
                updated_at = :updatedAt
            where id = :taskId
            """.trimIndent(),
            mapOf(
                "taskId" to taskId,
                "updatedAt" to Timestamp.valueOf(LocalDateTime.now())
            )
        )
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
            scaleVersionNo = rs.getString("scale_version_no"),
            scaleVersionGroupId = rs.getObject("scale_version_group_id", java.lang.Long::class.java)?.toLong(),
            taskMode = rs.getString("task_mode"),
            anonymousFlag = rs.getBoolean("anonymous_flag"),
            startTime = rs.getTimestamp("start_time").toLocalDateTime(),
            endTime = rs.getTimestamp("end_time").toLocalDateTime(),
            status = rs.getString("status")
        )
    }
}
