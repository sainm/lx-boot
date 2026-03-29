package org.sainm.psy.scale.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleSummary
import org.sainm.psy.scale.service.ScaleService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/scales")
class ScaleController(
    private val scaleService: ScaleService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun findPage(
        @RequestParam(required = false) scaleName: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<ScaleSummary>> =
        ApiResponse.ok(
            scaleService.findPage(
                ScaleListQuery(
                    scaleName = scaleName,
                    status = status,
                    page = page,
                    size = size
                )
            )
        )

    @PostMapping
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateScaleRequest): ApiResponse<CreateScaleResponse> =
        ApiResponse.ok(scaleService.create(request))

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun findDetail(@PathVariable id: Long): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.findDetail(id))

    @PostMapping("/{id}/dimensions/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun batchCreateDimensions(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleDimensionsRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateDimensions(id, request))

    @PostMapping("/{id}/questions/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun batchCreateQuestions(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleQuestionsRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateQuestions(id, request))

    @PostMapping("/{id}/result-rules/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun batchCreateResultRules(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleResultRulesRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateResultRules(id, request))
}
