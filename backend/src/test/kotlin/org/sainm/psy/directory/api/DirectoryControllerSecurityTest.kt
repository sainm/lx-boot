package org.sainm.psy.directory.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.directory.domain.DirectoryUser
import org.sainm.psy.directory.service.DirectoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(DirectoryController::class)
@Import(AuthSecurityConfiguration::class)
class DirectoryControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var directoryService: DirectoryService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `users lookup rejects anonymous request`() {
        mockMvc.get("/api/v1/directory/users")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `users lookup rejects respondent role`() {
        mockMvc.get("/api/v1/directory/users")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `users lookup allows counselor role`() {
        `when`(directoryService.findUserPage(null, false, false, 1, 20)).thenReturn(
            PageResponse(
                list = listOf(DirectoryUser(userId = 7L, username = "respondent", displayName = "Respondent", status = "ENABLED")),
                page = 1,
                size = 20,
                total = 1
            )
        )

        mockMvc.get("/api/v1/directory/users")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.list[0].userId") { value(7) }
            }
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `users lookup forwards the active-only filter`() {
        `when`(directoryService.findUserPage(null, false, true, 1, 20)).thenReturn(
            PageResponse(
                list = listOf(DirectoryUser(userId = 7L, username = "respondent", displayName = "Respondent", status = "ENABLED")),
                page = 1,
                size = 20,
                total = 1
            )
        )

        mockMvc.get("/api/v1/directory/users?activeOnly=true")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.list[0].status") { value("ENABLED") }
            }
    }
}
