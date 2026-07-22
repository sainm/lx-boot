package org.sainm.psy.auth

import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.core.spi.SocialAccountService
import org.sainm.auth.core.spi.SocialIdentity
import org.sainm.auth.core.spi.UserLookupService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SsoAccountMappingServiceTest {

    private fun principal(id: Long, username: String) = UserPrincipal(
        userId = id,
        username = username,
        displayName = username,
        status = UserStatus.ENABLED,
        groupId = null,
        tenantId = null,
        roles = setOf("ORG_MANAGER")
    )

    // ── Helper factories ──────────────────────────────────────────────────────

    private fun makeService(
        mappedUserId: Long? = null,
        existingByUsername: Long? = null,
        existingByEmail: Long? = null,
        userById: UserPrincipal? = null
    ): Triple<SsoAccountMappingService, SocialAccountService, JdbcTemplate> {
        val jdbcTemplate = mock<JdbcTemplate>()
        val userLookupService = mock<UserLookupService>()
        val delegate = mock<SocialAccountService>()

        // Simulate sys_auth lookup
        if (mappedUserId != null) {
            whenever(
                jdbcTemplate.queryForObject(any<String>(), eq(Long::class.java), any(), any())
            ).thenReturn(mappedUserId)
        } else {
            whenever(
                jdbcTemplate.queryForObject(any<String>(), eq(Long::class.java), any(), any())
            ).thenThrow(EmptyResultDataAccessException(1))
        }

        // Simulate sys_user lookups by column
        if (existingByUsername != null) {
            // username match (first call to matchBy)
            whenever(
                jdbcTemplate.queryForObject(any<String>(), eq(Long::class.java), eq("student123"))
            ).thenReturn(existingByUsername)
        } else {
            whenever(
                jdbcTemplate.queryForObject(any<String>(), eq(Long::class.java), eq("student123"))
            ).thenThrow(EmptyResultDataAccessException(1))
        }

        if (existingByEmail != null) {
            whenever(
                jdbcTemplate.queryForObject(any<String>(), eq(Long::class.java), eq("john@school.edu.cn"))
            ).thenReturn(existingByEmail)
        } else {
            whenever(
                jdbcTemplate.queryForObject(any<String>(), eq(Long::class.java), eq("john@school.edu.cn"))
            ).thenThrow(EmptyResultDataAccessException(1))
        }

        val targetId = mappedUserId ?: existingByUsername ?: existingByEmail
        if (targetId != null && userById != null) {
            whenever(userLookupService.findById(targetId)).thenReturn(userById)
        }

        val service = SsoAccountMappingService(jdbcTemplate, userLookupService, delegate)
        return Triple(service, delegate, jdbcTemplate)
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `SSO identity with existing sys_auth mapping returns mapped user`() {
        val expected = principal(42L, "existing")
        val (service, delegate, _) = makeService(mappedUserId = 42L, userById = expected)

        val identity = SocialIdentity(provider = "OIDC", externalId = "student123")
        val result = service.findOrCreate(identity)

        assertEquals(expected, result)
        verify(delegate, never()).findOrCreate(any())
    }

    @Test
    fun `SSO identity with no mapping matches existing user by username`() {
        val expected = principal(77L, "student123")
        val (service, delegate, jdbcTemplate) =
            makeService(mappedUserId = null, existingByUsername = 77L, userById = expected)

        val identity = SocialIdentity(provider = "OIDC", externalId = "student123")
        val result = service.findOrCreate(identity)

        assertEquals(expected, result)
        // Should have inserted a sys_auth binding
        verify(jdbcTemplate).update(any<String>(), eq(77L), eq("OIDC"), eq("student123"))
        verify(delegate, never()).findOrCreate(any())
    }

    @Test
    fun `SSO identity with no username match falls back to email`() {
        val expected = principal(88L, "john_in_db")
        val (service, delegate, jdbcTemplate) =
            makeService(mappedUserId = null, existingByUsername = null, existingByEmail = 88L, userById = expected)

        val identity = SocialIdentity(
            provider = "CAS",
            externalId = "student123",
            email = "john@school.edu.cn"
        )
        val result = service.findOrCreate(identity)

        assertEquals(expected, result)
        verify(jdbcTemplate).update(any<String>(), eq(88L), eq("CAS"), eq("student123"))
    }

    @Test
    fun `SSO identity with no match at all throws not-provisioned error`() {
        val (service, _, _) = makeService(mappedUserId = null, existingByUsername = null, existingByEmail = null)

        val identity = SocialIdentity(provider = "OIDC", externalId = "student123")
        val error = assertFailsWith<IllegalStateException> { service.findOrCreate(identity) }
        assertEquals("auth.sso.user.notProvisioned", error.message)
    }

    @Test
    fun `non-SSO provider delegates to default SocialAccountService`() {
        val expected = principal(99L, "wechat_user")
        val (service, delegate, _) = makeService(mappedUserId = null)
        val identity = SocialIdentity(provider = "WECHAT", externalId = "openid_abc")
        whenever(delegate.findOrCreate(identity)).thenReturn(expected)

        val result = service.findOrCreate(identity)

        assertEquals(expected, result)
        verify(delegate).findOrCreate(identity)
    }
}
