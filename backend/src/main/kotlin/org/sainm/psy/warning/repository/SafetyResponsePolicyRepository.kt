package org.sainm.psy.warning.repository

import org.sainm.psy.warning.api.CreateSafetyResponsePolicyRequest
import org.sainm.psy.warning.domain.SafetyResponsePolicy
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDateTime

@Repository
class SafetyResponsePolicyRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock
) {
    fun findAll(tenantId: Long?): List<SafetyResponsePolicy> = jdbcTemplate.query(
        """
        select id, tenant_id, policy_code, version_no, risk_category,
               first_response_minutes, escalation_minutes, follow_up_minutes,
               responsible_role, backup_role, emergency_contact_text,
               status, active_flag, approved_by, professional_reviewer_id,
               approved_at, created_at
        from psy_safety_response_policy
        where ${if (tenantId == null) "tenant_id is null" else "(tenant_id = :tenantId or tenant_id is null)"}
        order by risk_category, active_flag desc, version_no desc, id desc
        """.trimIndent(),
        mapOf("tenantId" to tenantId),
        rowMapper
    )

    fun findById(id: Long, tenantId: Long?): SafetyResponsePolicy? = jdbcTemplate.query(
        """
        select id, tenant_id, policy_code, version_no, risk_category,
               first_response_minutes, escalation_minutes, follow_up_minutes,
               responsible_role, backup_role, emergency_contact_text,
               status, active_flag, approved_by, professional_reviewer_id,
               approved_at, created_at
        from psy_safety_response_policy
        where id = :id
          and ${if (tenantId == null) "tenant_id is null" else "tenant_id = :tenantId"}
        """.trimIndent(),
        mapOf("id" to id, "tenantId" to tenantId),
        rowMapper
    ).firstOrNull()

    fun create(tenantId: Long?, request: CreateSafetyResponsePolicyRequest, createdBy: Long): Long {
        val now = Timestamp.valueOf(LocalDateTime.now(clock))
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """
            insert into psy_safety_response_policy (
                tenant_id, policy_code, version_no, risk_category,
                first_response_minutes, escalation_minutes, follow_up_minutes,
                responsible_role, backup_role, emergency_contact_text,
                status, active_flag, created_by, created_at, updated_at
            ) values (
                :tenantId, :policyCode, :versionNo, :riskCategory,
                :firstResponseMinutes, :escalationMinutes, :followUpMinutes,
                :responsibleRole, :backupRole, :emergencyContactText,
                'DRAFT', false, :createdBy, :now, :now
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("policyCode", request.policyCode.trim())
                .addValue("versionNo", request.versionNo)
                .addValue("riskCategory", request.riskCategory.trim().uppercase())
                .addValue("firstResponseMinutes", request.firstResponseMinutes)
                .addValue("escalationMinutes", request.escalationMinutes)
                .addValue("followUpMinutes", request.followUpMinutes)
                .addValue("responsibleRole", request.responsibleRole.trim().uppercase())
                .addValue("backupRole", request.backupRole.trim().uppercase())
                .addValue("emergencyContactText", request.emergencyContactText.trim())
                .addValue("createdBy", createdBy)
                .addValue("now", now),
            keyHolder,
            arrayOf("id")
        )
        return keyHolder.key?.toLong() ?: error("failed to create safety response policy")
    }

    fun isCounselorInTenant(userId: Long, tenantId: Long?): Boolean =
        (jdbcTemplate.queryForObject(
            """
            select count(1)
            from sys_user user_account
            join sys_user_role user_role on user_role.user_id = user_account.id
            join sys_role role on role.id = user_role.role_id
            where user_account.id = :userId
              and user_account.status = 1
              and coalesce(user_account.deleted, 0) = 0
              and role.role_code = 'COUNSELOR'
              and ${if (tenantId == null) "user_account.tenant_id is null" else "user_account.tenant_id = :tenantId"}
            """.trimIndent(),
            mapOf("userId" to userId, "tenantId" to tenantId),
            Long::class.java
        ) ?: 0L) > 0

    fun approveAndActivate(id: Long, tenantId: Long?, approvedBy: Long, professionalReviewerId: Long): Boolean {
        val target = findDraftByIdForUpdate(id, tenantId) ?: return false
        val now = Timestamp.valueOf(LocalDateTime.now(clock))
        jdbcTemplate.update(
            """
            update psy_safety_response_policy
            set active_flag = false, status = 'RETIRED', updated_at = :now
            where risk_category = :riskCategory
              and active_flag = true
              and ${if (tenantId == null) "tenant_id is null" else "tenant_id = :tenantId"}
            """.trimIndent(),
            mapOf("riskCategory" to target.riskCategory, "tenantId" to tenantId, "now" to now)
        )
        return jdbcTemplate.update(
            """
            update psy_safety_response_policy
            set status = 'APPROVED', active_flag = true,
                approved_by = :approvedBy,
                professional_reviewer_id = :professionalReviewerId,
                approved_at = :now,
                updated_at = :now
            where id = :id and status = 'DRAFT' and active_flag = false
              and ${if (tenantId == null) "tenant_id is null" else "tenant_id = :tenantId"}
            """.trimIndent(),
            mapOf(
                "id" to id,
                "tenantId" to tenantId,
                "approvedBy" to approvedBy,
                "professionalReviewerId" to professionalReviewerId,
                "now" to now
            )
        ) == 1
    }

    /**
     * Serializes repeated approval of the same draft before the currently active
     * policy is retired. Without this lock, a concurrent second approval could
     * retire the first transaction's newly activated policy and then fail to
     * activate the already-approved target, leaving the risk category inactive.
     */
    private fun findDraftByIdForUpdate(id: Long, tenantId: Long?): SafetyResponsePolicy? = jdbcTemplate.query(
        """
        select id, tenant_id, policy_code, version_no, risk_category,
               first_response_minutes, escalation_minutes, follow_up_minutes,
               responsible_role, backup_role, emergency_contact_text,
               status, active_flag, approved_by, professional_reviewer_id,
               approved_at, created_at
        from psy_safety_response_policy
        where id = :id and status = 'DRAFT'
          and ${if (tenantId == null) "tenant_id is null" else "tenant_id = :tenantId"}
        for update
        """.trimIndent(),
        mapOf("id" to id, "tenantId" to tenantId),
        rowMapper
    ).firstOrNull()

    private val rowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
        SafetyResponsePolicy(
            id = rs.getLong("id"),
            tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong(),
            policyCode = rs.getString("policy_code"),
            versionNo = rs.getInt("version_no"),
            riskCategory = rs.getString("risk_category"),
            firstResponseMinutes = rs.getInt("first_response_minutes"),
            escalationMinutes = rs.getInt("escalation_minutes"),
            followUpMinutes = rs.getObject("follow_up_minutes", java.lang.Integer::class.java)?.toInt(),
            responsibleRole = rs.getString("responsible_role"),
            backupRole = rs.getString("backup_role"),
            emergencyContactText = rs.getString("emergency_contact_text"),
            status = rs.getString("status"),
            activeFlag = rs.getBoolean("active_flag"),
            approvedBy = rs.getObject("approved_by", java.lang.Long::class.java)?.toLong(),
            professionalReviewerId = rs.getObject("professional_reviewer_id", java.lang.Long::class.java)?.toLong(),
            approvedAt = rs.getTimestamp("approved_at")?.toLocalDateTime(),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }
}
