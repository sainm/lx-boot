package org.sainm.psy.export.api

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.export.service.ExportJob
import org.sainm.psy.export.service.ExportJobStatus
import org.sainm.psy.export.service.ExportJobStore
import org.sainm.psy.export.service.ExportService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(ExportController::class)
@Import(AuthSecurityConfiguration::class)
class ExportControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var exportService: ExportService
    @MockitoBean private lateinit var exportJobStore: ExportJobStore
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher

    @Test
    fun `submitExportJob rejects anonymous request`() {
        mockMvc.post("/api/v1/exports/reports/jobs") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reportId":10,"exportFormat":"TEXT"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_401002") }
        }

        verifyNoInteractions(exportJobStore, exportService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `submitExportJob rejects USER role`() {
        mockMvc.post("/api/v1/exports/reports/jobs") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reportId":10,"exportFormat":"TEXT"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(exportJobStore, exportService)
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `submitExportJob allows staff role and starts async export`() {
        `when`(exportJobStore.create(anyString(), eq(10L), isNull(), eq("TEXT"), anyString())).thenAnswer { invocation ->
            ExportJob(id = invocation.getArgument(0), status = ExportJobStatus.PENDING)
        }

        mockMvc.post("/api/v1/exports/reports/jobs") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reportId":10,"exportFormat":"TEXT"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.status") { value("PENDING") }
            jsonPath("$.data.jobId") { isNotEmpty() }
        }

        verify(exportJobStore).create(anyString(), eq(10L), isNull(), eq("TEXT"), anyString())
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `submitExportJob returns business error when in-memory job limit is exceeded`() {
        `when`(exportJobStore.create(anyString(), eq(10L), isNull(), eq("TEXT"), anyString())).thenThrow(
            BizException("EXPORT_JOB_LIMIT_EXCEEDED", "Too many export jobs are waiting in memory")
        )

        mockMvc.post("/api/v1/exports/reports/jobs") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reportId":10,"exportFormat":"TEXT"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("EXPORT_JOB_LIMIT_EXCEEDED") }
        }

        verifyNoInteractions(exportService)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `retryExportJob rejects USER role`() {
        mockMvc.post("/api/v1/exports/reports/jobs/job-1/retry") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_403001") }
        }

        verifyNoInteractions(exportJobStore, exportService)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `retryExportJob allows admin role`() {
        `when`(exportJobStore.resetFailedForRetry("job-1")).thenReturn(
            ExportJob(
                id = "job-1",
                status = ExportJobStatus.PENDING,
                reportId = 10L,
                exportFormat = "TEXT",
                localeTag = "zh-CN"
            )
        )

        mockMvc.post("/api/v1/exports/reports/jobs/job-1/retry") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.jobId") { value("job-1") }
        }
    }
}
