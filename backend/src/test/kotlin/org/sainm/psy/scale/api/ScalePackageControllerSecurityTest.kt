package org.sainm.psy.scale.api

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.common.exception.NotFoundBizException
import org.sainm.psy.scale.service.ScalePackageExportArtifact
import org.sainm.psy.scale.service.ScalePackageExportService
import org.sainm.psy.scale.service.ScalePackageService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@WebMvcTest(ScalePackageController::class)
@Import(AuthSecurityConfiguration::class)
class ScalePackageControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockitoBean private lateinit var packageService: ScalePackageService
    @MockitoBean private lateinit var exportService: ScalePackageExportService
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `export rejects anonymous requests`() {
        mockMvc.get("/api/v1/scales/17/package/export").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_401002") }
        }

        verifyNoInteractions(exportService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `export rejects respondent role`() {
        mockMvc.get("/api/v1/scales/17/package/export").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(exportService)
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `export returns version and fingerprint headers for an authorized administrator`() {
        val body = """{"format":"PSY_SCALE_PACKAGE","schemaVersion":1}""".toByteArray()
        `when`(exportService.export(17)).thenReturn(
            ScalePackageExportArtifact(
                bytes = body,
                fileName = "TEST-v1-scale-package-v1.json",
                contentType = "application/vnd.psy-scale-package+json",
                exportId = "export-1",
                exportedAt = Instant.parse("2026-08-08T12:00:00Z"),
                scaleContentHash = "a".repeat(64),
                releaseFingerprint = "b".repeat(64),
                payloadHash = "c".repeat(64),
                schemaVersion = 1
            )
        )

        mockMvc.get("/api/v1/scales/17/package/export").andExpect {
            status { isOk() }
            content { contentType("application/vnd.psy-scale-package+json") }
            content { bytes(body) }
            header { string("X-Export-Id", "export-1") }
            header { string("X-Export-File-Name", "TEST-v1-scale-package-v1.json") }
            header { string("X-Scale-Package-Schema-Version", "1") }
            header { string("X-Scale-Content-Hash", "a".repeat(64)) }
            header { string("X-Release-Fingerprint", "b".repeat(64)) }
            header { string("X-Scale-Package-Payload-Hash", "c".repeat(64)) }
        }
    }

    @Test
    @WithMockUser(roles = ["ASSESSMENT_ADMIN"])
    fun `export hides a cross tenant scale as not found`() {
        `when`(exportService.export(17)).thenThrow(NotFoundBizException("SCALE_NOT_FOUND", "Scale not found"))

        mockMvc.get("/api/v1/scales/17/package/export").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SCALE_NOT_FOUND") }
        }
    }
}
