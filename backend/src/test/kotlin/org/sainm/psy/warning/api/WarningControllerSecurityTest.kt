package org.sainm.psy.warning.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.service.WarningService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(WarningController::class)
@Import(AuthSecurityConfiguration::class)
class WarningControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var warningService: WarningService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `findPage rejects anonymous request`() {
        mockMvc.get("/api/v1/warnings")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(warningService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `findPage rejects USER role`() {
        mockMvc.get("/api/v1/warnings")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }

        verifyNoInteractions(warningService)
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `findPage allows counselor role`() {
        `when`(warningService.findPage(WarningListQuery(page = 1, size = 20))).thenReturn(
            PageResponse(list = emptyList(), page = 1, size = 20, total = 0)
        )

        mockMvc.get("/api/v1/warnings")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.total") { value(0) }
            }
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `assign rejects counselor role`() {
        mockMvc.post("/api/v1/warnings/10/assign") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"assigneeUserId":20}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(warningService)
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `assign allows assessment admin role`() {
        `when`(warningService.assign(10L, AssignWarningRequest(assigneeUserId = 20L))).thenReturn(
            WarningActionResult(warningId = 10L, status = "PROCESSING", assigneeUserId = 20L)
        )

        mockMvc.post("/api/v1/warnings/10/assign") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"assigneeUserId":20}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.warningId") { value(10) }
            jsonPath("$.data.assigneeUserId") { value(20) }
        }
    }
}
