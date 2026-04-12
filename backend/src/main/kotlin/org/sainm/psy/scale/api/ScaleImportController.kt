package org.sainm.psy.scale.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.scale.service.ScaleImportService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/scales")
class ScaleImportController(
    private val scaleImportService: ScaleImportService
) {

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun downloadTemplate(): ResponseEntity<ByteArrayResource> =
        scaleImportService.downloadTemplate()

    @PostMapping("/imports/parse")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun parse(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(defaultValue = "CREATE_ONLY") importMode: String,
        @RequestParam(defaultValue = "true") draftFlag: Boolean
    ): ApiResponse<ParseScaleImportResponse> =
        ApiResponse.ok(scaleImportService.parse(file, importMode, draftFlag))

    @PostMapping("/imports/{id}/confirm")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun confirm(
        @PathVariable id: Long,
        @Valid @RequestBody request: ConfirmScaleImportRequest
    ): ApiResponse<ConfirmScaleImportResponse> =
        ApiResponse.ok(scaleImportService.confirm(id, request))

    @GetMapping("/imports/{id}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun findDetail(@PathVariable id: Long): ApiResponse<ScaleImportDetailResponse> =
        ApiResponse.ok(scaleImportService.findDetail(id))

    @GetMapping("/imports")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun findPage(
        @RequestParam(required = false) fileName: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<ScaleImportListItemResponse>> =
        ApiResponse.ok(scaleImportService.findPage(ScaleImportListQuery(fileName, status, page, size)))
}
