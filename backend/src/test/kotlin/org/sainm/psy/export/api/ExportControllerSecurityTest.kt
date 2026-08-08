package org.sainm.psy.export.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.sainm.auth.core.spi.AuditEventPublisher
import org.sainm.auth.core.spi.TokenService
import org.sainm.auth.security.config.AuthSecurityConfiguration
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.export.service.ExportArtifactStorageMode
import org.sainm.psy.export.service.ExportArtifactStorageProperties
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(ExportController::class)
@Import(AuthSecurityConfiguration::class)
class ExportControllerSecurityTest(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean private lateinit var exportService: ExportService
    @MockitoBean private lateinit var exportJobStore: ExportJobStore
    @MockitoBean private lateinit var exportArtifactStorageProperties: ExportArtifactStorageProperties
    @MockitoBean private lateinit var tokenService: TokenService
    @MockitoBean private lateinit var auditEventPublisher: AuditEventPublisher
    @MockitoBean private lateinit var currentUserFacade: CurrentUserFacade

    private val currentUser = UserPrincipal(
        userId = 42L,
        username = "counselor",
        displayName = "Counselor",
        status = UserStatus.ENABLED,
        tenantId = 7L,
        groupId = null,
        roles = setOf("COUNSELOR"),
        permissions = emptySet()
    )

    @BeforeEach
    fun setUpCurrentUser() {
        lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser)
    }

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
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser)
        `when`(exportJobStore.create(anyString(), eq(10L), isNull(), eq("TEXT"), anyString(), eq(true), eq(42L), eq(7L))).thenAnswer { invocation ->
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

        verify(exportJobStore).create(anyString(), eq(10L), isNull(), eq("TEXT"), anyString(), eq(true), eq(42L), eq(7L))
        verify(exportService).validateExportRequest(anyExportReportRequest())
        verify(exportService).processExportJob(anyString(), anyExportReportRequest(), anyString())
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `submitExportJob returns business error when in-memory job limit is exceeded`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser)
        `when`(exportJobStore.create(anyString(), eq(10L), isNull(), eq("TEXT"), anyString(), eq(true), eq(42L), eq(7L))).thenThrow(
            BizException("EXPORT_JOB_LIMIT_EXCEEDED", "Too many export jobs are waiting in memory")
        )

        mockMvc.post("/api/v1/exports/reports/jobs") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"reportId":10,"exportFormat":"TEXT"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("EXPORT_JOB_LIMIT_EXCEEDED") }
        }

        verify(exportService).validateExportRequest(anyExportReportRequest())
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
        val job = ExportJob(
            id = "job-1",
            status = ExportJobStatus.PENDING,
            reportId = 10L,
            exportFormat = "TEXT",
            localeTag = "zh-CN",
            tenantId = 7L
        )
        `when`(exportJobStore.find("job-1")).thenReturn(job)
        `when`(exportJobStore.resetFailedForRetry("job-1")).thenReturn(job)

        mockMvc.post("/api/v1/exports/reports/jobs/job-1/retry") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("0") }
            jsonPath("$.data.jobId") { value("job-1") }
        }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `getExportArtifactStorageInfo rejects USER role`() {
        mockMvc.get("/api/v1/exports/reports/storage")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `getExportArtifactStorageInfo allows admin role`() {
        `when`(exportArtifactStorageProperties.mode).thenReturn(ExportArtifactStorageMode.HTTP_OBJECT_STORAGE)
        `when`(exportArtifactStorageProperties.baseDir).thenReturn("D:/data/exports")
        `when`(exportArtifactStorageProperties.keyPrefix).thenReturn("reports/async")
        `when`(exportArtifactStorageProperties.bucket).thenReturn("psy-exports")
        `when`(exportArtifactStorageProperties.endpointUrl).thenReturn("https://storage-gateway.example.com/objects")

        mockMvc.get("/api/v1/exports/reports/storage")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data.mode") { value("HTTP_OBJECT_STORAGE") }
                jsonPath("$.data.bucket") { value("psy-exports") }
                jsonPath("$.data.endpointUrl") { value("https://storage-gateway.example.com/objects") }
            }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `listRecentExportJobs rejects USER role`() {
        mockMvc.get("/api/v1/exports/reports/jobs")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("AUTH_403001") }
            }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `listRecentExportJobs allows admin role`() {
        `when`(exportJobStore.listRecent(12, null, 7L)).thenReturn(
            listOf(
                ExportJob(
                    id = "job-1",
                    status = ExportJobStatus.FAILED,
                    reportId = 10L,
                    exportFormat = "PDF",
                    localeTag = "zh-CN",
                    filePath = "s3://psy-exports/reports/job-1.pdf",
                    error = "gateway timeout"
                )
            )
        )

        mockMvc.get("/api/v1/exports/reports/jobs")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("0") }
                jsonPath("$.data[0].jobId") { value("job-1") }
                jsonPath("$.data[0].status") { value("FAILED") }
                jsonPath("$.data[0].storageLocation") { doesNotExist() }
            }
    }

    @Test
    @WithMockUser(roles = ["COUNSELOR"])
    fun `getExportJobStatus hides job owned by another tenant`() {
        `when`(exportJobStore.find("job-other")).thenReturn(
            ExportJob(
                id = "job-other",
                status = ExportJobStatus.DONE,
                tenantId = 8L
            )
        )

        mockMvc.get("/api/v1/exports/reports/jobs/job-other")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("JOB_NOT_FOUND") }
            }
    }

    private fun anyExportReportRequest(): ExportReportRequest =
        any(ExportReportRequest::class.java) ?: ExportReportRequest()
}
