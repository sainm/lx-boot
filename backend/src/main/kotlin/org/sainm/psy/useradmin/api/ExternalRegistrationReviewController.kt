package org.sainm.psy.useradmin.api

import org.sainm.auth.core.spi.MailSenderService
import org.sainm.auth.core.spi.UserRegistrationService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.springframework.beans.factory.annotation.Value
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
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val mailSenderService: MailSenderService?,
    @Value("\${psy.external-registration.approval-notify-subject:Your account has been approved}")
    private val approvalSubject: String,
    @Value("\${psy.external-registration.rejection-notify-subject:Your registration was not approved}")
    private val rejectionSubject: String
) {

    @GetMapping("/external-registrations/pending")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN','ORG_MANAGER','ADMIN','SYS_ADMIN','SUPER_ADMIN')")
    fun listPending(): ResponseEntity<List<Map<String, Any?>>> {
        val tenantId = scopedTenantId()
        val rows = jdbcTemplate.queryForList(
            """
            select id, username, display_name, email, register_source, created_at
            from sys_user
            where status = 4
              and deleted = 0
              ${if (tenantId == null) "" else "and tenant_id = ?"}
            order by created_at asc
            """.trimIndent(),
            *listOfNotNull(tenantId).toTypedArray()
        )
        return ResponseEntity.ok(rows)
    }

    @PostMapping("/external-registrations/{userId}/approve")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN','ORG_MANAGER','ADMIN','SYS_ADMIN','SUPER_ADMIN')")
    fun approve(@PathVariable userId: Long): ResponseEntity<Map<String, String>> {
        val email = requirePendingRegistration(userId)
        userRegistrationService.advanceUserStatus(userId, fromStatus = 4, toStatus = 1)
        email?.let { mailSenderService?.send(it, approvalSubject, buildApprovalEmail()) }
        return ResponseEntity.ok(mapOf("message" to "approved"))
    }

    @PostMapping("/external-registrations/{userId}/reject")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN','ORG_MANAGER','ADMIN','SYS_ADMIN','SUPER_ADMIN')")
    fun reject(@PathVariable userId: Long): ResponseEntity<Map<String, String>> {
        val email = requirePendingRegistration(userId)
        userRegistrationService.advanceUserStatus(userId, fromStatus = 4, toStatus = 5)
        email?.let { mailSenderService?.send(it, rejectionSubject, buildRejectionEmail()) }
        return ResponseEntity.ok(mapOf("message" to "rejected"))
    }

    private fun buildApprovalEmail() =
        """
        <html><body>
        <p>Your account registration has been approved. You may now sign in.</p>
        <p>Go to the assessment system to begin.</p>
        </body></html>
        """.trimIndent()

    private fun buildRejectionEmail() =
        """
        <html><body>
        <p>Your account registration was not approved. Please contact your administrator for details.</p>
        </body></html>
        """.trimIndent()

    private fun requirePendingRegistration(userId: Long): String? {
        val tenantId = scopedTenantId()
        val rows = jdbcTemplate.query(
            """
            select email
            from sys_user
            where id = ?
              and status = 4
              and deleted = 0
              ${if (tenantId == null) "" else "and tenant_id = ?"}
            """.trimIndent(),
            { rs, _ -> rs.getString("email") },
            *listOfNotNull(userId, tenantId).toTypedArray()
        )
        if (rows.isEmpty()) {
            throw BizException("EXTERNAL_REGISTRATION_NOT_FOUND", messages.get("external.registration.not_found"))
        }
        return rows.first()
    }

    private fun scopedTenantId(): Long? {
        val currentUser = currentUserFacade.requireCurrentUser()
        return if (currentUser.roles.any { it in setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN") }) {
            null
        } else {
            currentUser.tenantId
                ?: throw BizException("TENANT_CONTEXT_REQUIRED", messages.get("tenant.context.required"))
        }
    }
}
