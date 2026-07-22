package org.sainm.psy.auth

import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.spi.SocialAccountService
import org.sainm.auth.core.spi.SocialIdentity
import org.sainm.auth.core.spi.UserLookupService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

/**
 * Custom [SocialAccountService] for lx-boot.
 *
 * Unlike the starter default (which always auto-creates a guest USER account),
 * SSO providers (CAS/OIDC) are meant to log in users that ALREADY exist in the
 * system (e.g. school leaders provisioned with their school id), so we must map
 * to the existing [org.sainm.auth.core.domain.UserPrincipal] and keep its roles.
 *
 * Resolution order for an incoming [SocialIdentity]:
 *  1. Existing `sys_auth(identity_type, principal_key)` mapping -> that user.
 *  2. For SSO providers, match an existing `sys_user` by school id
 *     (username == externalId) or by email, then bind a `sys_auth` row so the
 *     next login is a direct hit. Roles are preserved.
 *  3. SSO with no match: reject (must be provisioned by an admin first).
 *  4. Non-SSO providers (e.g. WECHAT) with no match: delegate to the default
 *     behaviour of creating a new respondent (USER) account.
 */
open class SsoAccountMappingService(
    private val jdbcTemplate: JdbcTemplate,
    private val userLookupService: UserLookupService,
    private val delegate: SocialAccountService,
    private val ssoProviders: Set<String> = setOf("CAS", "OIDC")
) : SocialAccountService {

    @Transactional
    override fun findOrCreate(identity: SocialIdentity): UserPrincipal {
        val provider = identity.provider.uppercase()
        val externalId = identity.externalId.trim()
        require(externalId.isNotBlank()) { "auth.social.externalId.blank" }

        findMappedUserId(provider, externalId)?.let { userId ->
            return userLookupService.findById(userId)
                ?: error("auth.social.user.notFound")
        }

        if (provider !in ssoProviders) {
            // WeChat / other self-service social logins keep default create-if-absent.
            return delegate.findOrCreate(identity)
        }

        val existingUserId = matchExistingUser(externalId, identity.email)
            ?: throw IllegalStateException("auth.sso.user.notProvisioned")

        bindIdentity(existingUserId, provider, externalId)
        return userLookupService.findById(existingUserId)
            ?: error("auth.social.user.notFound")
    }

    private fun findMappedUserId(provider: String, externalId: String): Long? =
        try {
            jdbcTemplate.queryForObject(
                """
                select user_id
                from sys_auth
                where identity_type = ?
                  and principal_key = ?
                  and enabled = 1
                limit 1
                """.trimIndent(),
                Long::class.java,
                provider,
                externalId
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    private fun matchExistingUser(schoolId: String, email: String?): Long? {
        matchBy("username", schoolId)?.let { return it }
        email?.trim()?.takeIf { it.isNotBlank() }?.let { matchBy("email", it)?.let { id -> return id } }
        return null
    }

    private fun matchBy(column: String, value: String): Long? =
        try {
            jdbcTemplate.queryForObject(
                """
                select id
                from sys_user
                where $column = ?
                  and deleted = 0
                order by id
                limit 1
                """.trimIndent(),
                Long::class.java,
                value
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    private fun bindIdentity(userId: Long, provider: String, externalId: String) {
        jdbcTemplate.update(
            """
            insert into sys_auth (user_id, identity_type, principal_key, credential_hash, metadata_json, enabled)
            values (?, ?, ?, null, '{}'::jsonb, 1)
            on conflict (identity_type, principal_key) do nothing
            """.trimIndent(),
            userId,
            provider,
            externalId
        )
    }
}
