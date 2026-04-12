package org.sainm.psy.report.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.report.domain.ReportDetail
import org.sainm.psy.report.service.ReportService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@WebMvcTest(ReportController::class)
@Import(AuthSecurityConfiguration::class)
class ReportControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var reportService: ReportService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `findDetail rejects anonymous request`() {
        mockMvc.get("/api/v1/reports/10")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTH_401002") }
            }

        verifyNoInteractions(reportService)
    }

    @Test
    @WithMockUser
    fun `findDetail returns detail for authenticated request without exposing internal userId`() {
        `when`(reportService.findDetail(10L)).thenReturn(
            ReportDetail(
                reportId = 10L,
                resultId = 20L,
                userId = 5L,
                reportType = "SYSTEM",
                totalScore = BigDecimal("15"),
                riskLevel = "MODERATE",
                content = "report content"
            )
        )

        mockMvc.get("/api/v1/reports/10")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.reportId") { value(10) }
                jsonPath("$.data.resultId") { value(20) }
                jsonPath("$.data.userId") { doesNotExist() }
            }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `regenerate rejects USER role`() {
        mockMvc.post("/api/v1/reports/10/regenerate")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }

        verifyNoInteractions(reportService)
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `regenerate allows counselor role`() {
        `when`(reportService.regenerate(10L)).thenReturn(
            ReportDetail(
                reportId = 11L,
                resultId = 20L,
                userId = 5L,
                reportType = "SYSTEM",
                totalScore = BigDecimal("16"),
                riskLevel = "MODERATE",
                content = "regenerated content"
            )
        )

        mockMvc.post("/api/v1/reports/10/regenerate")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.reportId") { value(11) }
                jsonPath("$.data.userId") { doesNotExist() }
            }
    }
}
