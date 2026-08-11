package org.sainm.psy.scale.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.common.exception.NotFoundBizException
import org.sainm.psy.scale.service.ScaleImportService
import org.sainm.psy.scale.service.ScalePackageImportPreviewService
import org.sainm.psy.scale.service.ScalePackageImportService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(ScaleImportController::class)
@Import(AuthSecurityConfiguration::class)
class ScaleImportControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockitoBean private lateinit var scaleImportService: ScaleImportService
    @MockitoBean private lateinit var packagePreviewService: ScalePackageImportPreviewService
    @MockitoBean private lateinit var packageImportService: ScalePackageImportService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `package confirmation rejects anonymous requests`() {
        mockMvc.post("/api/v1/scales/imports/package/99/confirm").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_401002") }
        }

        verifyNoInteractions(packageImportService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `package confirmation rejects respondent role`() {
        mockMvc.post("/api/v1/scales/imports/package/99/confirm").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(packageImportService)
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `package confirmation returns the new draft for an authorized administrator`() {
        `when`(packageImportService.confirm(99)).thenReturn(
            ConfirmScalePackageImportResponse(
                importId = 99,
                status = "SUCCESS",
                scaleId = 100,
                createdDimensionCount = 2,
                createdQuestionCount = 9,
                createdOptionCount = 36,
                createdResultRuleCount = 3,
                importedGoldenCaseRevisionCount = 4,
                discardedGoldenCaseRunCount = 4,
                discardedPublicationReviewCount = 2
            )
        )

        mockMvc.post("/api/v1/scales/imports/package/99/confirm").andExpect {
            status { isOk() }
            jsonPath("$.data.importId") { value(99) }
            jsonPath("$.data.scaleId") { value(100) }
            jsonPath("$.data.status") { value("SUCCESS") }
            jsonPath("$.data.importedGoldenCaseRevisionCount") { value(4) }
            jsonPath("$.data.discardedGoldenCaseRunCount") { value(4) }
            jsonPath("$.data.discardedPublicationReviewCount") { value(2) }
        }
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `package confirmation hides a cross tenant import as not found`() {
        `when`(packageImportService.confirm(99)).thenThrow(
            NotFoundBizException("SCALE_IMPORT_JOB_NOT_FOUND", "Scale import job not found")
        )

        mockMvc.post("/api/v1/scales/imports/package/99/confirm").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SCALE_IMPORT_JOB_NOT_FOUND") }
        }
    }
}
