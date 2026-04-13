package org.sainm.psy.auth.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.auth.service.AuthSessionService
import org.sainm.psy.auth.service.LoginActivitySummary
import org.sainm.psy.auth.service.SecurityEventSummary
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(AuthProfileController::class)
@Import(AuthSecurityConfiguration::class)
class AuthProfileControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var currentUserFacade: CurrentUserFacade
    @MockitoBean private lateinit var authSessionService: AuthSessionService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `my login activities rejects anonymous request`() {
        mockMvc.get("/api/v1/auth/me/login-activities")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(currentUserFacade, authSessionService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `my login activities returns current user records`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(
            CurrentUser(
                userId = 7L,
                username = "alice",
                displayName = "Alice",
                tenantId = 1L,
                groupId = 2L,
                roles = setOf("USER"),
                permissions = emptySet()
            )
        )
        `when`(authSessionService.findRecentLoginActivities(7L, "alice")).thenReturn(
            listOf(
                LoginActivitySummary(
                    id = 1L,
                    userId = 7L,
                    principal = "alice",
                    loginType = "PASSWORD",
                    result = "SUCCESS",
                    ip = "127.0.0.1",
                    userAgent = null,
                    location = null,
                    reason = null,
                    createdAt = "2026-04-13T13:00:00Z"
                )
            )
        )

        mockMvc.get("/api/v1/auth/me/login-activities")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data[0].principal") { value("alice") }
                jsonPath("$.data[0].result") { value("SUCCESS") }
            }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `my security events returns current user records`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(
            CurrentUser(
                userId = 7L,
                username = "alice",
                displayName = "Alice",
                tenantId = 1L,
                groupId = 2L,
                roles = setOf("USER"),
                permissions = emptySet()
            )
        )
        `when`(authSessionService.findRecentSecurityEvents(7L)).thenReturn(
            listOf(
                SecurityEventSummary(
                    id = 2L,
                    eventType = "ACCESS_DENIED",
                    userId = 7L,
                    tenantId = 1L,
                    detail = mapOf("path" to "/api/v1/exports/reports"),
                    ip = "127.0.0.1",
                    createdAt = "2026-04-13T13:05:00Z"
                )
            )
        )

        mockMvc.get("/api/v1/auth/me/security-events")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data[0].eventType") { value("ACCESS_DENIED") }
                jsonPath("$.data[0].detail.path") { value("/api/v1/exports/reports") }
            }
    }
}
