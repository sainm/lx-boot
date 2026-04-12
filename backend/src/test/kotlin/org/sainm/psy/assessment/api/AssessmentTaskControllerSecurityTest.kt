package org.sainm.psy.assessment.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.service.AssessmentTaskService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(AssessmentTaskController::class)
@Import(AuthSecurityConfiguration::class)
class AssessmentTaskControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var assessmentTaskService: AssessmentTaskService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `closeTask rejects anonymous request`() {
        mockMvc.post("/api/v1/tasks/10/close") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"abnormal task"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_401002") }
        }

        verifyNoInteractions(assessmentTaskService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `closeTask rejects USER role`() {
        mockMvc.post("/api/v1/tasks/10/close") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"abnormal task"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(assessmentTaskService)
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `closeTask allows assessment admin`() {
        `when`(assessmentTaskService.closeTask(10L, CloseAssessmentTaskRequest(reason = "abnormal task")))
            .thenReturn(
                AssessmentTaskDetail(
                    id = 10L,
                    taskName = "Spring Survey",
                    scaleId = 2L,
                    scaleName = "PHQ-9",
                    scaleVersionNo = "v1",
                    scaleVersionGroupId = 2L,
                    taskMode = "SCREENING",
                    anonymousFlag = false,
                    allowSaveFlag = true,
                    allowTimeoutSubmitFlag = false,
                    allowRetakeFlag = false,
                    startTime = LocalDateTime.now(),
                    endTime = LocalDateTime.now().plusDays(7),
                    status = "CLOSED",
                    createdBy = 1L,
                    createdAt = LocalDateTime.now(),
                    assignments = emptyList(),
                    closeReason = "abnormal task"
                )
            )

        mockMvc.post("/api/v1/tasks/10/close") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"abnormal task"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.status") { value("CLOSED") }
        }
    }
}
