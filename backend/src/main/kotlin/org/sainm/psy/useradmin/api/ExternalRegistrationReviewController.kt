package org.sainm.psy.useradmin.api

import org.sainm.auth.core.spi.MailSenderService
import org.sainm.auth.core.spi.UserRegistrationService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Admin endpoints for the overseas-student external-registration review workflow.
 * Accounts in PENDING_APPROVAL (status=4) are visible here; admins can approve
 * (→ status=1 ENABLED) or reject (→ status=5 REJECTED).
 */
@RestController
@RequestMapping("/api/v1/admin")
class ExternalRegistrationReviewController(
    private val jdbcTemplate: JdbcTemplate,
    private val userRegistrationService: UserRegistrationService,
    private val mailSenderService: MailSenderService?,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages
) {

    @GetMapping("/external-registrations/pending")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN','ORG_MANAGER','ADMIN','SYS_ADMIN','SUPER_ADMIN')")
    fun listPending(): ResponseEntity<List<Map<String, Any?>>> {
        val tenantId = currentUserFacade.requireCurrentUser().scopedTenantId()
        val tenantClause = if (tenantId == null) "" else "and tenant_id = ?"
        val rows = jdbcTemplate.queryForList(
            """
            select id, username, display_name, email, register_source, created_at
            from sys_user
            where status = 4
              and deleted = 0
              $tenantClause
            order by created_at asc
            """.trimIndent(),
            *if (tenantId == null) emptyArray() else arrayOf(tenantId)
        )
        return ResponseEntity.ok(rows)
    }

    @PostMapping("/external-registrations/{userId}/approve")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN','ORG_MANAGER','ADMIN','SYS_ADMIN','SUPER_ADMIN')")
    fun approve(@PathVariable userId: Long): ResponseEntity<Map<String, String>> {
        val target = requireAccessibleTarget(userId)
        userRegistrationService.advanceUserStatus(userId, fromStatus = 4, toStatus = 1)
        target.email?.let {
            mailSenderService?.send(
                it,
                messages.get("external_registration.approved.subject"),
                messages.get("external_registration.approved.body_html")
            )
        }
        return ResponseEntity.ok(mapOf("message" to messages.get("external_registration.approved.response")))
    }

    @PostMapping("/external-registrations/{userId}/reject")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN','ORG_MANAGER','ADMIN','SYS_ADMIN','SUPER_ADMIN')")
    fun reject(@PathVariable userId: Long): ResponseEntity<Map<String, String>> {
        val target = requireAccessibleTarget(userId)
        userRegistrationService.advanceUserStatus(userId, fromStatus = 4, toStatus = 5)
        target.email?.let {
            mailSenderService?.send(
                it,
                messages.get("external_registration.rejected.subject"),
                messages.get("external_registration.rejected.body_html")
            )
        }
        return ResponseEntity.ok(mapOf("message" to messages.get("external_registration.rejected.response")))
    }

    private fun requireAccessibleTarget(userId: Long): RegistrationTarget {
        val target = jdbcTemplate.query(
            "select id, email, tenant_id from sys_user where id = ? and deleted = 0 and status = 4",
            { rs, _ ->
                RegistrationTarget(
                    id = rs.getLong("id"),
                    email = rs.getString("email"),
                    tenantId = rs.getObject("tenant_id", java.lang.Long::class.java)?.toLong()
                )
            },
            userId
        ).firstOrNull() ?: throw BizException("REGISTRATION_NOT_FOUND", messages.get("error.external_registration_not_found"))
        val currentUser = currentUserFacade.requireCurrentUser()
        if (!currentUser.isGlobalAdmin() && currentUser.tenantId != target.tenantId) {
            throw BizException("REGISTRATION_FORBIDDEN", messages.get("error.external_registration_forbidden"))
        }
        return target
    }

    private fun UserPrincipal.scopedTenantId(): Long? {
        if (isGlobalAdmin()) return null
        return tenantId ?: throw BizException("REGISTRATION_TENANT_REQUIRED", messages.get("error.external_registration_tenant_required"))
    }

    private fun UserPrincipal.isGlobalAdmin(): Boolean =
        tenantId == null && roles.any { it in setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN") }

    private data class RegistrationTarget(val id: Long, val email: String?, val tenantId: Long?)
}
