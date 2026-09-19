package org.sainm.psy.directory.repository

import org.sainm.psy.common.jdbc.addIfNotNull
import org.sainm.psy.common.jdbc.params
import org.sainm.psy.common.jdbc.whereClause
import org.sainm.psy.directory.domain.DirectoryGroup
import org.sainm.psy.directory.domain.DirectoryScale
import org.sainm.psy.directory.domain.DirectoryTask
import org.sainm.psy.directory.domain.DirectoryUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Read-only lookups shared by the operator-facing pickers.  These replace the
 * raw id text boxes that used to be spread over the appointment, warning,
 * report, publication and audit screens.
 */
@Repository
class DirectoryRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun findUserPage(
        keyword: String?,
        staffOnly: Boolean,
        activeOnly: Boolean,
        page: Int,
        size: Int,
        tenantId: Long?
    ): Pair<List<DirectoryUser>, Long> {
        val offset = (page - 1).coerceAtLeast(0) * size
        val like = keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { "%${it.lowercase()}%" }
        val params = params {
            addValue("limit", size)
            addValue("offset", offset)
            addIfNotNull("keyword", like)
            addIfNotNull("tenantId", tenantId)
        }
        val where = whereClause(
            like?.let { "(lower(u.username) like :keyword or lower(coalesce(u.display_name, '')) like :keyword)" },
            tenantId?.let { "u.tenant_id = :tenantId" },
            staffOnly.takeIf { it }?.let { STAFF_EXISTS_CLAUSE },
            // Booking pickers must not offer users the create API rejects.
            activeOnly.takeIf { it }?.let { "u.deleted = 0 and u.status = 1" }
        )
        val listSql = """
            select u.id,
                   u.username,
                   coalesce(nullif(u.display_name, ''), u.username) as display_name,
                   case when u.status = 1 then 'ENABLED' else 'DISABLED' end as status
            from sys_user u
            $where
            order by display_name asc, u.id asc
            limit :limit offset :offset
        """.trimIndent()
        val countSql = """
            select count(1)
            from sys_user u
            $where
        """.trimIndent()
        val list = jdbcTemplate.query(listSql, params) { rs, _ ->
            DirectoryUser(
                userId = rs.getLong("id"),
                username = rs.getString("username"),
                displayName = rs.getString("display_name"),
                status = rs.getString("status")
            )
        }
        val total = jdbcTemplate.queryForObject(countSql, params, Long::class.java) ?: 0L
        return list to total
    }

    fun findGroups(tenantId: Long?, limit: Int = 500): List<DirectoryGroup> =
        jdbcTemplate.query(
            """
            select id, group_code, group_name
            from sys_group
            ${if (tenantId == null) "" else "where tenant_id = :tenantId"}
            order by group_name asc, id asc
            limit $limit
            """.trimIndent(),
            mapOf("tenantId" to tenantId)
        ) { rs, _ ->
            DirectoryGroup(
                groupId = rs.getLong("id"),
                groupCode = rs.getString("group_code"),
                groupName = rs.getString("group_name")
            )
        }

    fun findTasks(keyword: String?, limit: Int, tenantId: Long?): List<DirectoryTask> {
        val like = keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { "%${it.lowercase()}%" }
        val params = params {
            addValue("limit", limit)
            addIfNotNull("keyword", like)
            addIfNotNull("tenantId", tenantId)
        }
        val where = whereClause(
            like?.let { "lower(t.task_name) like :keyword" },
            tenantId?.let { "t.tenant_id = :tenantId" }
        )
        return jdbcTemplate.query(
            """
            select t.id, t.task_name, t.scale_id, s.scale_name
            from psy_assessment_task t
            join psy_scale s on s.id = t.scale_id
            $where
            order by t.id desc
            limit :limit
            """.trimIndent(),
            params
        ) { rs, _ ->
            DirectoryTask(
                taskId = rs.getLong("id"),
                taskName = rs.getString("task_name"),
                scaleId = rs.getLong("scale_id"),
                scaleName = rs.getString("scale_name")
            )
        }
    }

    fun findScales(keyword: String?, limit: Int, tenantId: Long?): List<DirectoryScale> {
        val like = keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { "%${it.lowercase()}%" }
        val params = params {
            addValue("limit", limit)
            addIfNotNull("keyword", like)
            addIfNotNull("tenantId", tenantId)
        }
        val where = whereClause(
            like?.let { "(lower(scale_name) like :keyword or lower(scale_code) like :keyword)" },
            tenantId?.let { "tenant_id = :tenantId" }
        )
        return jdbcTemplate.query(
            """
            select id, scale_code, scale_name, status
            from psy_scale
            $where
            order by id desc
            limit :limit
            """.trimIndent(),
            params
        ) { rs, _ ->
            DirectoryScale(
                scaleId = rs.getLong("id"),
                scaleCode = rs.getString("scale_code"),
                scaleName = rs.getString("scale_name"),
                status = rs.getString("status")
            )
        }
    }

    private companion object {
        private const val STAFF_EXISTS_CLAUSE = """
            (
                exists (
                    select 1
                    from sys_user_role ur
                    join sys_role r on r.id = ur.role_id
                    where ur.user_id = u.id
                      and r.enabled = 1
                      and r.role_code in ('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'SYS_ADMIN', 'ADMIN', 'SUPER_ADMIN')
                )
                or exists (
                    select 1
                    from sys_group_role gr
                    join sys_role r on r.id = gr.role_id
                    where gr.group_id = u.group_id
                      and r.enabled = 1
                      and r.role_code in ('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'SYS_ADMIN', 'ADMIN', 'SUPER_ADMIN')
                )
            )
        """
    }
}
