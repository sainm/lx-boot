package org.sainm.psy.appointment.repository

import org.sainm.psy.appointment.api.CreateAppointmentRequest
import org.sainm.psy.appointment.api.CreateScheduleRequest
import org.sainm.psy.appointment.domain.AppointmentDetail
import org.sainm.psy.appointment.domain.AppointmentSummary
import org.sainm.psy.appointment.domain.AppointmentStatusLog
import org.sainm.psy.appointment.domain.CounselorOption
import org.sainm.psy.appointment.domain.CounselorScheduleSummary
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class AppointmentRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findBookableCounselors(tenantId: Long? = null): List<CounselorOption> {
        val tenantClause = if (tenantId == null) "" else "and u.tenant_id = :tenantId"
        val sql = """
            select u.id,
                   u.username,
                   coalesce(nullif(u.display_name, ''), u.username) as display_name
            from sys_user u
            where u.deleted = 0
              and u.status = 1
              $tenantClause
              and (
                  exists (
                      select 1
                      from sys_user_role ur
                      join sys_role r on r.id = ur.role_id
                      where ur.user_id = u.id
                        and r.enabled = 1
                        and r.role_code = 'COUNSELOR'
                  )
                  or exists (
                      select 1
                      from sys_group_role gr
                      join sys_role r on r.id = gr.role_id
                      where gr.group_id = u.group_id
                        and r.enabled = 1
                        and r.role_code = 'COUNSELOR'
                  )
              )
              and exists (
                  select 1
                  from psy_counselor_schedule s
                  where s.counselor_user_id = u.id
                    and s.status = 'AVAILABLE'
                    and s.schedule_date >= current_date
              )
            order by display_name asc, u.id asc
        """.trimIndent()
        val params = if (tenantId == null) emptyMap() else mapOf("tenantId" to tenantId)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            CounselorOption(
                userId = rs.getLong("id"),
                username = rs.getString("username"),
                displayName = rs.getString("display_name")
            )
        }
    }

    fun findSchedulesByCounselorId(counselorUserId: Long): List<CounselorScheduleSummary> {
        val sql = """
            select s.id,
                   s.counselor_user_id,
                   s.schedule_date,
                   s.start_time,
                   s.end_time,
                   s.quota_count,
                   s.status,
                   coalesce(a.booked_count, 0) as booked_count
            from psy_counselor_schedule s
            left join (
                select schedule_id, count(1) as booked_count
                from psy_appointment_record
                where appointment_status not in ('CANCELLED', 'NO_SHOW')
                group by schedule_id
            ) a on a.schedule_id = s.id
            where s.counselor_user_id = :counselorUserId
              and s.status = 'AVAILABLE'
              and s.start_time > current_timestamp
              and coalesce(a.booked_count, 0) < s.quota_count
            order by s.schedule_date asc, s.start_time asc, s.id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("counselorUserId" to counselorUserId)) { rs, _ ->
            val quota = rs.getInt("quota_count")
            val booked = rs.getInt("booked_count")
            CounselorScheduleSummary(
                id = rs.getLong("id"),
                counselorUserId = rs.getLong("counselor_user_id"),
                scheduleDate = rs.getDate("schedule_date").toLocalDate(),
                startTime = rs.getTimestamp("start_time").toLocalDateTime(),
                endTime = rs.getTimestamp("end_time").toLocalDateTime(),
                quotaCount = quota,
                bookedCount = booked,
                availableCount = (quota - booked).coerceAtLeast(0),
                status = rs.getString("status")
            )
        }
    }

    fun findScheduleById(scheduleId: Long): CounselorScheduleSummary? {
        return findScheduleById(scheduleId, forUpdate = false)
    }

    fun findScheduleByIdForUpdate(scheduleId: Long): CounselorScheduleSummary? {
        return findScheduleById(scheduleId, forUpdate = true)
    }

    private fun findScheduleById(scheduleId: Long, forUpdate: Boolean): CounselorScheduleSummary? {
        val sql = """
            select s.id,
                   s.counselor_user_id,
                   s.schedule_date,
                   s.start_time,
                   s.end_time,
                   s.quota_count,
                   s.status,
                   coalesce(a.booked_count, 0) as booked_count
            from psy_counselor_schedule s
            left join (
                select schedule_id, count(1) as booked_count
                from psy_appointment_record
                where appointment_status not in ('CANCELLED', 'NO_SHOW')
                group by schedule_id
            ) a on a.schedule_id = s.id
            where s.id = :scheduleId
            ${if (forUpdate) "for update of s" else ""}
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("scheduleId" to scheduleId)) { rs, _ ->
            val quota = rs.getInt("quota_count")
            val booked = rs.getInt("booked_count")
            CounselorScheduleSummary(
                id = rs.getLong("id"),
                counselorUserId = rs.getLong("counselor_user_id"),
                scheduleDate = rs.getDate("schedule_date").toLocalDate(),
                startTime = rs.getTimestamp("start_time").toLocalDateTime(),
                endTime = rs.getTimestamp("end_time").toLocalDateTime(),
                quotaCount = quota,
                bookedCount = booked,
                availableCount = (quota - booked).coerceAtLeast(0),
                status = rs.getString("status")
            )
        }.firstOrNull()
    }

    fun createAppointment(request: CreateAppointmentRequest, userId: Long, sourceType: String): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_appointment_record (
                user_id, counselor_user_id, warning_id, schedule_id,
                appointment_status, source_type, remark, created_at, updated_at
            ) values (
                :userId, :counselorUserId, :warningId, :scheduleId,
                :appointmentStatus, :sourceType, :remark, :createdAt, :updatedAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("counselorUserId", request.counselorUserId)
                .addValue("warningId", request.warningId)
                .addValue("scheduleId", request.scheduleId)
                .addValue("appointmentStatus", "CONFIRMED")
                .addValue("sourceType", sourceType)
                .addValue("remark", request.remark)
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create appointment")
    }

    fun findAppointmentById(id: Long): AppointmentDetail? = findAppointmentById(id, forUpdate = false)

    fun findAppointmentByIdForUpdate(id: Long): AppointmentDetail? = findAppointmentById(id, forUpdate = true)

    private fun findAppointmentById(id: Long, forUpdate: Boolean): AppointmentDetail? {
        val sql = """
            select id, user_id, counselor_user_id, warning_id, schedule_id, appointment_status,
                   source_type, remark, created_at, updated_at
            from psy_appointment_record
            where id = :id
            ${if (forUpdate) "for update" else ""}
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
            AppointmentDetail(
                id = rs.getLong("id"),
                userId = rs.getLong("user_id"),
                counselorUserId = rs.getLong("counselor_user_id"),
                warningId = rs.getObject("warning_id", java.lang.Long::class.java)?.toLong(),
                scheduleId = rs.getObject("schedule_id", java.lang.Long::class.java)?.toLong(),
                appointmentStatus = rs.getString("appointment_status"),
                sourceType = rs.getString("source_type"),
                remark = rs.getString("remark"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }.firstOrNull()
    }

    fun findMyAppointments(userId: Long): List<AppointmentSummary> {
        return findAppointments("a.user_id = :userId", mapOf("userId" to userId))
    }

    fun findCounselorAppointments(counselorUserId: Long): List<AppointmentSummary> =
        findAppointments("a.counselor_user_id = :counselorUserId", mapOf("counselorUserId" to counselorUserId))

    fun findTenantAppointments(tenantId: Long): List<AppointmentSummary> =
        findAppointments("respondent.tenant_id = :tenantId", mapOf("tenantId" to tenantId))

    fun findAllAppointments(): List<AppointmentSummary> = findAppointments("1 = 1", emptyMap<String, Any>())

    fun isUserInTenant(userId: Long, tenantId: Long?): Boolean {
        return (jdbcTemplate.queryForObject(
            "select count(1) from sys_user where id = :userId and tenant_id is not distinct from :tenantId and deleted = 0",
            mapOf("userId" to userId, "tenantId" to tenantId),
            Long::class.java
        ) ?: 0L) > 0
    }

    fun isAppointmentPastEnd(appointmentId: Long, now: LocalDateTime): Boolean =
        (jdbcTemplate.queryForObject(
            """
            select count(1)
            from psy_appointment_record a
            join psy_counselor_schedule s on s.id = a.schedule_id
            where a.id = :appointmentId and s.end_time < :now
            """.trimIndent(),
            mapOf("appointmentId" to appointmentId, "now" to Timestamp.valueOf(now)),
            Long::class.java
        ) ?: 0L) > 0

    private fun findAppointments(whereCondition: String, params: Map<String, *>): List<AppointmentSummary> {
        val sql = """
            select a.id,
                   a.user_id,
                   a.counselor_user_id,
                   counselor.display_name as counselor_display_name,
                   a.warning_id,
                   a.schedule_id,
                   a.appointment_status,
                   a.source_type,
                   a.remark,
                   a.created_at,
                   s.schedule_date,
                   s.start_time,
                   s.end_time
            from psy_appointment_record a
            left join psy_counselor_schedule s on s.id = a.schedule_id
            left join sys_user counselor on counselor.id = a.counselor_user_id
            join sys_user respondent on respondent.id = a.user_id
            where $whereCondition
            order by a.created_at desc, a.id desc
        """.trimIndent()
        return jdbcTemplate.query(sql, params) { rs, _ ->
            AppointmentSummary(
                id = rs.getLong("id"),
                userId = rs.getLong("user_id"),
                counselorUserId = rs.getLong("counselor_user_id"),
                counselorDisplayName = rs.getString("counselor_display_name"),
                warningId = rs.getObject("warning_id", java.lang.Long::class.java)?.toLong(),
                scheduleId = rs.getObject("schedule_id", java.lang.Long::class.java)?.toLong(),
                appointmentStatus = rs.getString("appointment_status"),
                sourceType = rs.getString("source_type"),
                remark = rs.getString("remark"),
                scheduleDate = rs.getDate("schedule_date")?.toLocalDate(),
                startTime = rs.getTimestamp("start_time")?.toLocalDateTime(),
                endTime = rs.getTimestamp("end_time")?.toLocalDateTime(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }
    }

    fun createSchedule(request: CreateScheduleRequest, counselorUserId: Long): Long {
        val sql = """
            insert into psy_counselor_schedule (
                counselor_user_id, schedule_date, start_time, end_time, quota_count, status, created_at
            ) values (
                :counselorUserId, :scheduleDate, :startTime, :endTime, :quotaCount, 'AVAILABLE', :createdAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("counselorUserId", counselorUserId)
                .addValue("scheduleDate", java.sql.Date.valueOf(request.scheduleDate))
                .addValue("startTime", Timestamp.valueOf(request.startTime))
                .addValue("endTime", Timestamp.valueOf(request.endTime))
                .addValue("quotaCount", request.quotaCount)
                .addValue("createdAt", Timestamp.valueOf(LocalDateTime.now())),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create schedule")
    }

    fun hasOverlappingSchedule(counselorUserId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Boolean =
        jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from psy_counselor_schedule
                    where counselor_user_id = :counselorUserId
                      and status = 'AVAILABLE'
                      and start_time < :endTime
                      and end_time > :startTime
                )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("counselorUserId", counselorUserId)
                .addValue("startTime", Timestamp.valueOf(startTime))
                .addValue("endTime", Timestamp.valueOf(endTime)),
            Boolean::class.java
        ) ?: false

    fun lockCounselorScheduleScope(counselorUserId: Long) {
        jdbcTemplate.queryForObject(
            "select id from sys_user where id = :counselorUserId for update",
            mapOf("counselorUserId" to counselorUserId),
            Long::class.java
        )
    }

    fun countActiveAppointmentsByScheduleId(scheduleId: Long): Int = jdbcTemplate.queryForObject(
            """
                select count(1)
                from psy_appointment_record
                where schedule_id = :scheduleId
                  and appointment_status not in ('CANCELLED', 'NO_SHOW')
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId),
            Int::class.java
        ) ?: 0

    fun updateAppointmentStatus(appointmentId: Long, status: String) {
        jdbcTemplate.update(
            """
                update psy_appointment_record
                set appointment_status = :status,
                    updated_at = :updatedAt
                where id = :appointmentId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("appointmentId", appointmentId)
                .addValue("status", status)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }

    fun rescheduleAppointment(
        appointmentId: Long,
        counselorUserId: Long,
        scheduleId: Long,
        remark: String?
    ) {
        jdbcTemplate.update(
            """
                update psy_appointment_record
                set counselor_user_id = :counselorUserId,
                    schedule_id = :scheduleId,
                    appointment_status = 'CONFIRMED',
                    remark = coalesce(:remark, remark),
                    updated_at = :updatedAt
                where id = :appointmentId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("appointmentId", appointmentId)
                .addValue("counselorUserId", counselorUserId)
                .addValue("scheduleId", scheduleId)
                .addValue("remark", remark?.trim()?.takeIf { it.isNotEmpty() })
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }

    fun createStatusLog(
        appointmentId: Long,
        fromStatus: String?,
        toStatus: String,
        actionType: String,
        operatorUserId: Long,
        fromScheduleId: Long?,
        toScheduleId: Long?,
        remark: String? = null
    ) {
        jdbcTemplate.update(
            """
                insert into psy_appointment_status_log(
                    appointment_id, from_status, to_status, action_type, operator_user_id,
                    from_schedule_id, to_schedule_id, remark, created_at
                ) values (
                    :appointmentId, :fromStatus, :toStatus, :actionType, :operatorUserId,
                    :fromScheduleId, :toScheduleId, :remark, :createdAt
                )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("appointmentId", appointmentId)
                .addValue("fromStatus", fromStatus)
                .addValue("toStatus", toStatus)
                .addValue("actionType", actionType)
                .addValue("operatorUserId", operatorUserId)
                .addValue("fromScheduleId", fromScheduleId)
                .addValue("toScheduleId", toScheduleId)
                .addValue("remark", remark?.trim()?.takeIf { it.isNotEmpty() })
                .addValue("createdAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }

    fun findStatusLogs(appointmentId: Long): List<AppointmentStatusLog> = jdbcTemplate.query(
        """
            select id, appointment_id, from_status, to_status, action_type, operator_user_id,
                   from_schedule_id, to_schedule_id, remark, created_at
            from psy_appointment_status_log
            where appointment_id = :appointmentId
            order by created_at asc, id asc
        """.trimIndent(),
        mapOf("appointmentId" to appointmentId)
    ) { rs, _ ->
        AppointmentStatusLog(
            id = rs.getLong("id"),
            appointmentId = rs.getLong("appointment_id"),
            fromStatus = rs.getString("from_status"),
            toStatus = rs.getString("to_status"),
            actionType = rs.getString("action_type"),
            operatorUserId = rs.getLong("operator_user_id"),
            fromScheduleId = rs.getObject("from_schedule_id", java.lang.Long::class.java)?.toLong(),
            toScheduleId = rs.getObject("to_schedule_id", java.lang.Long::class.java)?.toLong(),
            remark = rs.getString("remark"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }
}
