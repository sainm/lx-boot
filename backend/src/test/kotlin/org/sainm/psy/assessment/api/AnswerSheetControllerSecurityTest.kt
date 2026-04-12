package org.sainm.psy.assessment.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.domain.AnswerSheetRescoreResult
import org.sainm.psy.assessment.service.AnswerSheetService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@WebMvcTest(AnswerSheetController::class)
@Import(AuthSecurityConfiguration::class)
class AnswerSheetControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var answerSheetService: AnswerSheetService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `getTaskQuestions rejects anonymous request`() {
        mockMvc.get("/api/v1/my/tasks/10/questions")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(answerSheetService)
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `getTaskQuestions rejects non USER role`() {
        mockMvc.get("/api/v1/my/tasks/10/questions")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }

        verifyNoInteractions(answerSheetService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `getTaskQuestions allows USER role`() {
        `when`(answerSheetService.getTaskQuestions(10L)).thenReturn(
            TaskQuestionPayload(
                taskId = 10L,
                scaleId = 20L,
                scaleName = "PHQ-9",
                allowSaveFlag = true,
                questions = emptyList()
            )
        )

        mockMvc.get("/api/v1/my/tasks/10/questions")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.taskId") { value(10) }
                jsonPath("$.data.scaleId") { value(20) }
            }
    }

    @Test
    fun `rescoreResult rejects anonymous request`() {
        mockMvc.post("/api/v1/results/201/rescore")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(answerSheetService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `rescoreResult rejects USER role`() {
        mockMvc.post("/api/v1/results/201/rescore")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }

        verifyNoInteractions(answerSheetService)
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `rescoreResult allows assessment admin`() {
        `when`(answerSheetService.rescoreResult(201L)).thenReturn(
            AnswerSheetRescoreResult(
                answerSheetId = 88L,
                resultId = 201L,
                reportId = 301L,
                totalScore = BigDecimal("12"),
                riskLevel = "MODERATE",
                previousRiskLevel = "NORMAL"
            )
        )

        mockMvc.post("/api/v1/results/201/rescore")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.resultId") { value(201) }
                jsonPath("$.data.reportId") { value(301) }
            }
    }
}
