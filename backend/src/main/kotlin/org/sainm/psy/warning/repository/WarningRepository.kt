package org.sainm.psy.warning.repository

import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.jdbc.addIfNotNull
import org.sainm.psy.common.jdbc.params
import org.sainm.psy.common.jdbc.whereClause
import org.sainm.psy.warning.api.WarningListQuery
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.domain.WarningAutomationCandidate
import org.sainm.psy.warning.domain.WarningSummary
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class WarningRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val messages: LocalizedMessages
) {

    private data class WarningClaimContext(
        val status: String,
        val pendingAssigneeUserId: Long?
    )

    fun findPage(query: WarningListQuery): Pair<List<WarningSummary>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val status = query.status?.trim()?.takeIf { it.isNotEmpty() }
        val warningLevel = query.warningLevel?.trim()?.takeIf { it.isNotEmpty() }
        val params = params {
            addValue("limit", query.size)
            addValue("offset", offset)
            addIfNotNull("status", status)
            addIfNotNull("warningLevel", warningLevel)
        }
        val whereClause = whereClause(
            status?.let { "status = :status" },
            warningLevel?.let { "warning_level = :warningLevel" }
        )
        val listSql = """
            select id, result_id, warning_level, warning_priority, warning_reason, status, created_at
            from psy_warning_record
            $whereClause
            order by id desc
            limit :limit offset :offset
        """.trimIndent()
        val countSql = """
            select count(1)
            from psy_warning_record
            $whereClause
        """.trimIndent()
        val list = jdbcTemplate.query(listSql, params, warningSummaryRowMapper)
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    fun existsById(warningId: Long): Boolean =
        (jdbcTemplate.queryForObject(
            "select count(1) from psy_warning_record where id = :warningId",
            mapOf("warningId" to warningId),
            Long::class.java
        ) ?: 0L) > 0

    fun claimWarning(warningId: Long, assigneeUserId: Long, claimedBy: Long): WarningActionResult {
        val now = Timestamp.valueOf(LocalDateTime.now())
        if (tryClaimAssignedWarning(warningId, assigneeUserId, now)) {
            markLatestPendingAssignmentClaimed(warningId, assigneeUserId, now)
            return WarningActionResult(
                warningId = warningId,
                status = "PROCESSING",
                assigneeUserId = assigneeUserId
            )
        }
        if (tryClaimPendingWarning(warningId, now)) {
            insertAssignment(
                warningId = warningId,
                assigneeUserId = assigneeUserId,
                assignedBy = claimedBy,
                assignedAt = now,
                claimTime = now
            )
            return WarningActionResult(
                warningId = warningId,
                status = "PROCESSING",
                assigneeUserId = assigneeUserId
            )
        }
        throw claimFailure(warningId, assigneeUserId)
    }

    fun assignWarning(warningId: Long, assigneeUserId: Long, assignedBy: Long): WarningActionResult {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val updated = jdbcTemplate.update(
            """
                update psy_warning_record
                set status = 'ASSIGNED',
                    updated_at = :now
                where id = :warningId
                  and status = 'PENDING'
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("now", now)
            }
        )
        if (updated == 0) {
            throw assignFailure(warningId)
        }
        insertAssignment(
            warningId = warningId,
            assigneeUserId = assigneeUserId,
            assignedBy = assignedBy,
            assignedAt = now
        )
        return WarningActionResult(
            warningId = warningId,
            status = "ASSIGNED",
            assigneeUserId = assigneeUserId
        )
    }

    fun closeWarning(warningId: Long) {
        val now = Timestamp.valueOf(LocalDateTime.now())
        jdbcTemplate.update(
            """
                update psy_warning_record
                set status = 'CLOSED',
                    closed_time = coalesce(closed_time, :now),
                    updated_at = :now
                where id = :warningId
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("now", now)
            }
        )
    }

    fun markProcessing(warningId: Long) {
        val now = Timestamp.valueOf(LocalDateTime.now())
        jdbcTemplate.update(
            """
                update psy_warning_record
                set status = 'PROCESSING',
                    first_response_time = coalesce(first_response_time, :now),
                    updated_at = :now
                where id = :warningId
                  and status <> 'CLOSED'
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("now", now)
            }
        )
    }

    fun findHighRiskWarningsNeedingEscalation(createdBefore: LocalDateTime): List<WarningAutomationCandidate> {
        val sql = """
            select
                w.id as warning_id,
                a.assignee_user_id
            from psy_warning_record w
            left join lateral (
                select assignee_user_id
                from psy_warning_assignment
                where warning_id = w.id
                order by assigned_at desc, id desc
                limit 1
            ) a on true
            where w.warning_level = 'HIGH'
              and w.status in ('PENDING', 'ASSIGNED')
              and coalesce(w.warning_priority, '') <> 'P0'
              and w.escalated_at is null
              and coalesce(w.deadline_time, w.created_at) < :createdBefore
            order by w.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("createdBefore" to Timestamp.valueOf(createdBefore))) { rs, _ ->
            WarningAutomationCandidate(
                warningId = rs.getLong("warning_id"),
                receiverUserIds = listOfNotNull(rs.getObject("assignee_user_id", java.lang.Long::class.java)?.toLong())
            )
        }
    }

    fun markWarningsEscalated(warningIds: List<Long>, now: LocalDateTime): Int {
        if (warningIds.isEmpty()) {
            return 0
        }
        return jdbcTemplate.update(
            """
            update psy_warning_record
            set warning_priority = 'P0',
                escalation_count = escalation_count + 1,
                escalated_at = coalesce(escalated_at, :now),
                updated_at = :now
            where id in (:warningIds)
              and status <> 'CLOSED'
            """.trimIndent(),
            mapOf(
                "warningIds" to warningIds.distinct(),
                "now" to Timestamp.valueOf(now)
            )
        )
    }

    fun findWarningsNeedingReminder(referenceBefore: LocalDateTime): List<WarningAutomationCandidate> {
        val sql = """
            select
                w.id as warning_id,
                a.assignee_user_id
            from psy_warning_record w
            join lateral (
                select assignee_user_id
                from psy_warning_assignment
                where warning_id = w.id
                order by assigned_at desc, id desc
                limit 1
            ) a on true
            where w.status in ('ASSIGNED', 'PROCESSING')
              and coalesce(w.last_reminded_at, w.first_response_time, w.updated_at, w.created_at) < :referenceBefore
              and w.closed_time is null
            order by w.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("referenceBefore" to Timestamp.valueOf(referenceBefore))) { rs, _ ->
            WarningAutomationCandidate(
                warningId = rs.getLong("warning_id"),
                receiverUserIds = listOf(rs.getLong("assignee_user_id"))
            )
        }
    }

    fun markWarningsReminded(warningIds: List<Long>, now: LocalDateTime): Int {
        if (warningIds.isEmpty()) {
            return 0
        }
        return jdbcTemplate.update(
            """
            update psy_warning_record
            set last_reminded_at = :now,
                updated_at = :now
            where id in (:warningIds)
              and status <> 'CLOSED'
            """.trimIndent(),
            mapOf(
                "warningIds" to warningIds.distinct(),
                "now" to Timestamp.valueOf(now)
            )
        )
    }

    private fun tryClaimAssignedWarning(warningId: Long, assigneeUserId: Long, now: Timestamp): Boolean =
        jdbcTemplate.update(
            """
                update psy_warning_record
                set status = 'PROCESSING',
                    first_response_time = coalesce(first_response_time, :now),
                    updated_at = :now
                where id = :warningId
                  and status = 'ASSIGNED'
                  and exists (
                      select 1
                      from psy_warning_assignment a
                      where a.id = (
                          select id
                          from psy_warning_assignment
                          where warning_id = :warningId
                            and claim_time is null
                          order by assigned_at desc, id desc
                          limit 1
                      )
                        and a.assignee_user_id = :assigneeUserId
                  )
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("assigneeUserId", assigneeUserId)
                addValue("now", now)
            }
        ) > 0

    private fun tryClaimPendingWarning(warningId: Long, now: Timestamp): Boolean =
        jdbcTemplate.update(
            """
                update psy_warning_record
                set status = 'PROCESSING',
                    first_response_time = coalesce(first_response_time, :now),
                    updated_at = :now
                where id = :warningId
                  and status = 'PENDING'
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("now", now)
            }
        ) > 0

    private fun markLatestPendingAssignmentClaimed(warningId: Long, assigneeUserId: Long, claimTime: Timestamp) {
        val updated = jdbcTemplate.update(
            """
                update psy_warning_assignment
                set claim_time = :claimTime
                where id = (
                    select id
                    from psy_warning_assignment
                    where warning_id = :warningId
                      and claim_time is null
                    order by assigned_at desc, id desc
                    limit 1
                )
                  and assignee_user_id = :assigneeUserId
                  and claim_time is null
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("assigneeUserId", assigneeUserId)
                addValue("claimTime", claimTime)
            }
        )
        if (updated == 0) {
            throw claimFailure(warningId, assigneeUserId)
        }
    }

    private fun insertAssignment(
        warningId: Long,
        assigneeUserId: Long,
        assignedBy: Long,
        assignedAt: Timestamp,
        claimTime: Timestamp? = null
    ) {
        jdbcTemplate.update(
            """
                insert into psy_warning_assignment (
                    warning_id, assignee_user_id, assigned_by, assigned_at, claim_time
                ) values (
                    :warningId, :assigneeUserId, :assignedBy, :assignedAt, :claimTime
                )
            """.trimIndent(),
            params {
                addValue("warningId", warningId)
                addValue("assigneeUserId", assigneeUserId)
                addValue("assignedBy", assignedBy)
                addValue("assignedAt", assignedAt)
                addValue("claimTime", claimTime)
            }
        )
    }

    private fun claimFailure(warningId: Long, assigneeUserId: Long): BizException =
        when (val context = findClaimContext(warningId)) {
            null -> BizException("WARNING_NOT_FOUND_OR_CLOSED", messages.get("warning.not_found_or_closed"))
            else -> when {
                context.status == "CLOSED" -> BizException("WARNING_NOT_FOUND_OR_CLOSED", messages.get("warning.not_found_or_closed"))
                context.status == "PROCESSING" -> BizException("WARNING_ALREADY_CLAIMED", messages.get("warning.already_claimed"))
                context.status == "ASSIGNED" && context.pendingAssigneeUserId != null && context.pendingAssigneeUserId != assigneeUserId ->
                    BizException("WARNING_ASSIGNED_TO_OTHER", messages.get("warning.assigned_to_other"))
                context.status == "ASSIGNED" -> BizException("WARNING_ALREADY_ASSIGNED", messages.get("warning.already_assigned"))
                else -> BizException("WARNING_NOT_FOUND_OR_CLOSED", messages.get("warning.not_found_or_closed"))
            }
        }

    private fun assignFailure(warningId: Long): BizException =
        when (findClaimContext(warningId)?.status) {
            null, "CLOSED" -> BizException("WARNING_NOT_FOUND_OR_CLOSED", messages.get("warning.not_found_or_closed"))
            "PROCESSING" -> BizException("WARNING_ALREADY_CLAIMED", messages.get("warning.already_claimed"))
            "ASSIGNED" -> BizException("WARNING_ALREADY_ASSIGNED", messages.get("warning.already_assigned"))
            else -> BizException("WARNING_NOT_FOUND_OR_CLOSED", messages.get("warning.not_found_or_closed"))
        }

    private fun findClaimContext(warningId: Long): WarningClaimContext? =
        jdbcTemplate.query(
            """
                select
                    w.status,
                    (
                        select assignee_user_id
                        from psy_warning_assignment
                        where warning_id = w.id
                          and claim_time is null
                        order by assigned_at desc, id desc
                        limit 1
                    ) as pending_assignee_user_id
                from psy_warning_record w
                where w.id = :warningId
            """.trimIndent(),
            mapOf("warningId" to warningId)
        ) { rs, _ ->
            WarningClaimContext(
                status = rs.getString("status"),
                pendingAssigneeUserId = rs.getObject("pending_assignee_user_id", java.lang.Long::class.java)?.toLong()
            )
        }.firstOrNull()

    private val warningSummaryRowMapper = RowMapper { rs, _ ->
        WarningSummary(
            id = rs.getLong("id"),
            resultId = rs.getLong("result_id"),
            warningLevel = rs.getString("warning_level"),
            warningPriority = rs.getString("warning_priority"),
            warningReason = rs.getString("warning_reason"),
            status = rs.getString("status"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }
}
