package org.sainm.psy.statistics.api

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.statistics.domain.DashboardStatisticsResponse
import org.sainm.psy.statistics.domain.GroupReportSummary
import org.sainm.psy.statistics.service.StatisticsService
import org.sainm.psy.statistics.service.GroupReportExportJobProcessor
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.export.api.ExportJobSubmitResponse
import org.sainm.psy.export.service.ExportJobStatus
import org.sainm.psy.export.service.ExportJobStore
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/statistics")
class StatisticsController(
    private val statisticsService: StatisticsService,
    private val exportJobStore: ExportJobStore,
    private val currentUserFacade: CurrentUserFacade,
    private val objectMapper: ObjectMapper,
    private val groupReportExportJobProcessor: GroupReportExportJobProcessor,
    private val securityAuditService: SecurityAuditService
) {

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'SCHOOL_LEADER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun dashboard(): ApiResponse<DashboardStatisticsResponse> =
        ApiResponse.ok(statisticsService.dashboard())

    @GetMapping("/group-reports")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'SCHOOL_LEADER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun groupReports(
        @RequestParam(required = false) taskId: Long?,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(required = false) scaleId: Long?,
        @RequestParam(required = false) compareUserId: Long?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<GroupReportSummary>> =
        ApiResponse.ok(
            statisticsService.groupReports(
                GroupReportListQuery(
                    taskId = taskId,
                    groupId = groupId,
                    scaleId = scaleId,
                    compareUserId = compareUserId,
                    startDate = startDate,
                    endDate = endDate,
                    page = page,
                    size = size
                )
            )
        )

    @GetMapping("/group-reports/download")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'SCHOOL_LEADER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun downloadGroupReports(
        @RequestParam(required = false) taskId: Long?,
        @RequestParam(required = false) groupId: Long?,
        @RequestParam(required = false) scaleId: Long?,
        @RequestParam(required = false) compareUserId: Long?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(defaultValue = "PDF") format: String,
        @RequestParam(required = false) exportFormat: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "200") size: Int
    ): ResponseEntity<ByteArrayResource> {
        val artifact = statisticsService.exportGroupReports(
            GroupReportListQuery(
                taskId = taskId,
                groupId = groupId,
                scaleId = scaleId,
                compareUserId = compareUserId,
                startDate = startDate,
                endDate = endDate,
                page = page,
                size = size
            ),
            exportFormat ?: format
        )
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(artifact.fileName, StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(artifact.contentType))
            .contentLength(artifact.bytes.size.toLong())
            .body(ByteArrayResource(artifact.bytes))
    }

    @PostMapping("/group-reports/jobs")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'SCHOOL_LEADER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun submitGroupReportExportJob(
        @Valid @RequestBody request: GroupReportExportJobRequest
    ): ApiResponse<ExportJobSubmitResponse> {
        val query = request.toQuery()
        statisticsService.validateGroupExportQuery(query, request.format)
        val currentUser = currentUserFacade.requireCurrentUser()
        val jobId = UUID.randomUUID().toString()
        exportJobStore.create(
            id = jobId,
            exportFormat = request.format.trim().uppercase(),
            localeTag = LocaleContextHolder.getLocale().toLanguageTag(),
            createdBy = currentUser.userId,
            tenantId = currentUser.tenantId,
            sourceType = "GROUP_REPORT",
            requestJson = objectMapper.writeValueAsString(request)
        )
        securityAuditService.recordGroupReportExportRequested(jobId, request.taskId, request.groupId, request.format.trim().uppercase())
        groupReportExportJobProcessor.process(jobId)
        return ApiResponse.ok(ExportJobSubmitResponse(jobId, ExportJobStatus.PENDING.name))
    }
}
