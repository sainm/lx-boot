package org.sainm.psy.scale.repository

import org.sainm.psy.scale.api.ScaleImportListItemResponse
import org.sainm.psy.scale.api.ScaleImportListQuery
import org.sainm.psy.common.jdbc.addIfNotNull
import org.sainm.psy.common.jdbc.params
import org.sainm.psy.common.jdbc.whereClause
import org.sainm.psy.scale.domain.ScaleImportIssue
import org.sainm.psy.scale.domain.ScaleImportJobRecord
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class ScaleImportRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun createJob(
        fileName: String,
        importMode: String,
        draftFlag: Boolean,
        operatorUserId: Long
    ): Long {
        val now = LocalDateTime.now()
        val sql = """
            insert into psy_scale_import_job (
                file_name, import_mode, draft_flag, status, operator_user_id, created_at, updated_at
            ) values (
                :fileName, :importMode, :draftFlag, :status, :operatorUserId, :createdAt, :updatedAt
            )
        """.trimIndent()
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            sql,
            params {
                addValue("fileName", fileName)
                addValue("importMode", importMode)
                addValue("draftFlag", draftFlag)
                addValue("status", "UPLOADED")
                addValue("operatorUserId", operatorUserId)
                addValue("createdAt", Timestamp.valueOf(now))
                addValue("updatedAt", Timestamp.valueOf(now))
            },
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create scale import job")
    }

    fun updateParsedResult(
        jobId: Long,
        status: String,
        summaryJson: String?,
        previewJson: String?,
        errorCount: Int,
        warningCount: Int
    ) {
        val now = LocalDateTime.now()
        val sql = """
            update psy_scale_import_job
            set status = :status,
                summary_json = :summaryJson,
                preview_json = :previewJson,
                error_count = :errorCount,
                warning_count = :warningCount,
                parsed_at = :parsedAt,
                updated_at = :updatedAt
            where id = :id
        """.trimIndent()
        jdbcTemplate.update(
            sql,
            params {
                addValue("id", jobId)
                addValue("status", status)
                addValue("summaryJson", summaryJson)
                addValue("previewJson", previewJson)
                addValue("errorCount", errorCount)
                addValue("warningCount", warningCount)
                addValue("parsedAt", Timestamp.valueOf(now))
                addValue("updatedAt", Timestamp.valueOf(now))
            }
        )
    }

    fun replaceIssues(jobId: Long, issues: List<ScaleImportIssue>) {
        jdbcTemplate.update("delete from psy_scale_import_issue where import_job_id = :jobId", mapOf("jobId" to jobId))
        if (issues.isEmpty()) return
        val now = Timestamp.valueOf(LocalDateTime.now())
        val sql = """
            insert into psy_scale_import_issue (
                import_job_id, severity, sheet_name, row_no, column_name, error_code, message, created_at
            ) values (
                :importJobId, :severity, :sheetName, :rowNo, :columnName, :errorCode, :message, :createdAt
            )
        """.trimIndent()
        val batch = issues.map { issue ->
            params {
                addValue("importJobId", jobId)
                addValue("severity", issue.severity)
                addValue("sheetName", issue.sheetName)
                addValue("rowNo", issue.rowNo)
                addValue("columnName", issue.columnName)
                addValue("errorCode", issue.errorCode)
                addValue("message", issue.message)
                addValue("createdAt", now)
            }
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, batch)
    }

    fun findJobById(id: Long): ScaleImportJobRecord? {
        val sql = """
            select id, file_name, import_mode, draft_flag, status, summary_json, preview_json,
                   error_count, warning_count, created_scale_id, operator_user_id,
                   parsed_at, confirmed_at, finished_at, created_at, updated_at
            from psy_scale_import_job
            where id = :id
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("id" to id)) { rs, _ ->
            ScaleImportJobRecord(
                id = rs.getLong("id"),
                fileName = rs.getString("file_name"),
                importMode = rs.getString("import_mode"),
                draftFlag = rs.getBoolean("draft_flag"),
                status = rs.getString("status"),
                summaryJson = rs.getString("summary_json"),
                previewJson = rs.getString("preview_json"),
                errorCount = rs.getInt("error_count"),
                warningCount = rs.getInt("warning_count"),
                createdScaleId = rs.getObject("created_scale_id", java.lang.Long::class.java)?.toLong(),
                operatorUserId = rs.getLong("operator_user_id"),
                parsedAt = rs.getTimestamp("parsed_at")?.toLocalDateTime(),
                confirmedAt = rs.getTimestamp("confirmed_at")?.toLocalDateTime(),
                finishedAt = rs.getTimestamp("finished_at")?.toLocalDateTime(),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
            )
        }.firstOrNull()
    }

    fun findIssuesByJobId(jobId: Long): List<ScaleImportIssue> {
        val sql = """
            select severity, sheet_name, row_no, column_name, error_code, message
            from psy_scale_import_issue
            where import_job_id = :jobId
            order by id asc
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("jobId" to jobId)) { rs, _ ->
            ScaleImportIssue(
                severity = rs.getString("severity"),
                sheetName = rs.getString("sheet_name"),
                rowNo = rs.getObject("row_no", Integer::class.java)?.toInt(),
                columnName = rs.getString("column_name"),
                errorCode = rs.getString("error_code"),
                message = rs.getString("message")
            )
        }
    }

    fun markConfirmed(jobId: Long) {
        updateStatus(jobId, "CONFIRMED", confirmed = true)
    }

    fun markSuccess(jobId: Long, scaleId: Long) {
        val now = LocalDateTime.now()
        jdbcTemplate.update(
            """
            update psy_scale_import_job
            set status = :status,
                created_scale_id = :createdScaleId,
                finished_at = :finishedAt,
                updated_at = :updatedAt
            where id = :id
            """.trimIndent(),
            params {
                addValue("id", jobId)
                addValue("status", "SUCCESS")
                addValue("createdScaleId", scaleId)
                addValue("finishedAt", Timestamp.valueOf(now))
                addValue("updatedAt", Timestamp.valueOf(now))
            }
        )
    }

    fun markFailed(jobId: Long) {
        updateStatus(jobId, "FAILED", finished = true)
    }

    fun findPage(query: ScaleImportListQuery): Pair<List<ScaleImportListItemResponse>, Long> {
        val offset = (query.page - 1).coerceAtLeast(0) * query.size
        val fileName = query.fileName?.trim()?.takeIf(String::isNotEmpty)?.let { "%$it%" }
        val status = query.status?.trim()?.takeIf(String::isNotEmpty)
        val params = params {
            addValue("limit", query.size)
            addValue("offset", offset)
            addIfNotNull("fileName", fileName)
            addIfNotNull("status", status)
        }

        val whereClause = whereClause(
            fileName?.let { "file_name like :fileName" },
            status?.let { "status = :status" }
        )

        val listSql = """
            select id, file_name, import_mode, draft_flag, status, error_count, warning_count,
                   created_scale_id, operator_user_id, created_at, finished_at
            from psy_scale_import_job
            $whereClause
            order by id desc
            limit :limit offset :offset
        """.trimIndent()
        val countSql = "select count(1) from psy_scale_import_job $whereClause"
        val list = jdbcTemplate.query(listSql, params) { rs, _ ->
            ScaleImportListItemResponse(
                id = rs.getLong("id"),
                fileName = rs.getString("file_name"),
                importMode = rs.getString("import_mode"),
                draftFlag = rs.getBoolean("draft_flag"),
                status = rs.getString("status"),
                errorCount = rs.getInt("error_count"),
                warningCount = rs.getInt("warning_count"),
                createdScaleId = rs.getObject("created_scale_id", java.lang.Long::class.java)?.toLong(),
                operatorUserId = rs.getLong("operator_user_id"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                finishedAt = rs.getTimestamp("finished_at")?.toLocalDateTime()
            )
        }
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    private fun updateStatus(jobId: Long, status: String, confirmed: Boolean = false, finished: Boolean = false) {
        val now = LocalDateTime.now()
        jdbcTemplate.update(
            """
            update psy_scale_import_job
            set status = :status,
                confirmed_at = case when :confirmed then :now else confirmed_at end,
                finished_at = case when :finished then :now else finished_at end,
                updated_at = :updatedAt
            where id = :id
            """.trimIndent(),
            params {
                addValue("id", jobId)
                addValue("status", status)
                addValue("confirmed", confirmed)
                addValue("finished", finished)
                addValue("now", Timestamp.valueOf(now))
                addValue("updatedAt", Timestamp.valueOf(now))
            }
        )
    }
}
