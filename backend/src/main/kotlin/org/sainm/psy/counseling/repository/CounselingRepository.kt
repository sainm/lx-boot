package org.sainm.psy.counseling.repository

import org.sainm.psy.counseling.domain.CounselingRecordDetail
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class CounselingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findByAppointmentId(appointmentId: Long): CounselingRecordDetail? {
        val sql = """
            select id, appointment_id, counselor_user_id, summary_text, suggestion_text,
                   need_retest_flag, need_transfer_flag, created_at, updated_at
            from psy_counseling_record
            where appointment_id = :appointmentId
            order by id desc
            limit 1
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("appointmentId" to appointmentId)) { rs, _ ->
            CounselingRecordDetail(
                id = rs.getLong("id"),
                appointmentId = rs.getLong("appointment_id"),
                counselorUserId = rs.getLong("counselor_user_id"),
                summaryText = rs.getString("summary_text"),
                suggestionText = rs.getString("suggestion_text"),
                needRetestFlag = rs.getBoolean("need_retest_flag"),
                needTransferFlag = rs.getBoolean("need_transfer_flag"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }.firstOrNull()
    }

    fun createRecord(
        appointmentId: Long,
        counselorUserId: Long,
        summaryText: String?,
        suggestionText: String?,
        needRetestFlag: Boolean,
        needTransferFlag: Boolean
    ): Long {
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_counseling_record (
                tenant_id, appointment_id, counselor_user_id, summary_text, suggestion_text,
                need_retest_flag, need_transfer_flag, created_at, updated_at
            ) values (
                (select tenant_id from psy_appointment_record where id = :appointmentId),
                :appointmentId, :counselorUserId, :summaryText, :suggestionText,
                :needRetestFlag, :needTransferFlag, :createdAt, :updatedAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            MapSqlParameterSource()
                .addValue("appointmentId", appointmentId)
                .addValue("counselorUserId", counselorUserId)
                .addValue("summaryText", summaryText)
                .addValue("suggestionText", suggestionText)
                .addValue("needRetestFlag", needRetestFlag)
                .addValue("needTransferFlag", needTransferFlag)
                .addValue("createdAt", now)
                .addValue("updatedAt", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create counseling record")
    }

    fun updateRecord(
        recordId: Long,
        summaryText: String?,
        suggestionText: String?,
        needRetestFlag: Boolean,
        needTransferFlag: Boolean
    ) {
        jdbcTemplate.update(
            """
                update psy_counseling_record
                set summary_text = :summaryText,
                    suggestion_text = :suggestionText,
                    need_retest_flag = :needRetestFlag,
                    need_transfer_flag = :needTransferFlag,
                    updated_at = :updatedAt
                where id = :recordId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("recordId", recordId)
                .addValue("summaryText", summaryText)
                .addValue("suggestionText", suggestionText)
                .addValue("needRetestFlag", needRetestFlag)
                .addValue("needTransferFlag", needTransferFlag)
                .addValue("updatedAt", Timestamp.valueOf(LocalDateTime.now()))
        )
    }
}
