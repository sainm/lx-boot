package org.sainm.psy.appointment.repository

import org.sainm.psy.appointment.api.CreateAppointmentRequest
import org.sainm.psy.appointment.api.CreateScheduleRequest
import org.sainm.psy.appointment.domain.AppointmentDetail
import org.sainm.psy.appointment.domain.AppointmentSummary
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

    fun findBookableCounselors(): List<CounselorOption> {
        val sql = """
            select u.id,
                   u.username,
                   coalesce(nullif(u.display_name, ''), u.username) as display_name
            from sys_user u
            where u.deleted = 0
              and u.status = 1
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
        return jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
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
            ${if (forUpdate) "for update" else ""}
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

    fun findAppointmentById(id: Long): AppointmentDetail? {
        val sql = """
            select id, user_id, counselor_user_id, warning_id, schedule_id, appointment_status,
                   source_type, remark, created_at, updated_at
            from psy_appointment_record
            where id = :id
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
            where a.user_id = :userId
            order by a.created_at desc, a.id desc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("userId" to userId)) { rs, _ ->
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

    fun countActiveAppointmentsByScheduleId(scheduleId: Long): Int =        jdbcTemplate.queryForObject(
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
}
