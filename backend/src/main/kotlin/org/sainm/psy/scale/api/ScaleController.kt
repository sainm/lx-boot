package org.sainm.psy.scale.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleNormCoverage
import org.sainm.psy.scale.domain.ScaleSummary
import org.sainm.psy.scale.domain.ScaleVersionDiff
import org.sainm.psy.scale.service.ScaleService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
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
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
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
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateScaleRequest): ApiResponse<CreateScaleResponse> =
        ApiResponse.ok(scaleService.create(request))

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun createVersion(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateScaleVersionRequest
    ): ApiResponse<CreateScaleVersionResponse> =
        ApiResponse.ok(scaleService.createVersion(id, request))

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun publishVersion(@PathVariable id: Long): ApiResponse<PublishScaleVersionResponse> =
        ApiResponse.ok(scaleService.publishVersion(id))

    @GetMapping("/{id}/versions/{targetId}/diff")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun compareVersions(
        @PathVariable id: Long,
        @PathVariable targetId: Long
    ): ApiResponse<ScaleVersionDiff> =
        ApiResponse.ok(scaleService.compareVersions(id, targetId))

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun listVersions(@PathVariable id: Long): ApiResponse<List<ScaleSummary>> =
        ApiResponse.ok(scaleService.findVersions(id))

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findDetail(@PathVariable id: Long): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.findDetail(id))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun delete(@PathVariable id: Long): ApiResponse<Map<String, Any>> {
        scaleService.delete(id)
        return ApiResponse.ok(mapOf("success" to true))
    }

    @PostMapping("/{id}/basic")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun updateBasic(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateScaleBasicRequest
    ): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.updateBasic(id, request))

    @PostMapping("/{id}/dimensions/{dimensionId}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun updateDimension(
        @PathVariable id: Long,
        @PathVariable dimensionId: Long,
        @Valid @RequestBody request: UpdateScaleDimensionRequest
    ): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.updateDimension(id, dimensionId, request))

    @PostMapping("/{id}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun updateQuestion(
        @PathVariable id: Long,
        @PathVariable questionId: Long,
        @Valid @RequestBody request: UpdateScaleQuestionRequest
    ): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.updateQuestion(id, questionId, request))

    @PostMapping("/{id}/options/{optionId}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun updateOption(
        @PathVariable id: Long,
        @PathVariable optionId: Long,
        @Valid @RequestBody request: UpdateScaleOptionRequest
    ): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.updateOption(id, optionId, request))

    @PostMapping("/{id}/visualizations")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun updateVisualizations(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateScaleVisualizationsRequest
    ): ApiResponse<ScaleDetail> =
        ApiResponse.ok(scaleService.updateVisualizations(id, request))

    @PostMapping("/{id}/dimensions/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun batchCreateDimensions(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleDimensionsRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateDimensions(id, request))

    @PostMapping("/{id}/questions/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun batchCreateQuestions(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleQuestionsRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateQuestions(id, request))

    @PostMapping("/{id}/result-rules/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun batchCreateResultRules(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleResultRulesRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateResultRules(id, request))

    @PostMapping("/{id}/norms/batch")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun batchCreateNorms(
        @PathVariable id: Long,
        @Valid @RequestBody request: BatchCreateScaleNormsRequest
    ): ApiResponse<BatchCreateResponse> =
        ApiResponse.ok(scaleService.batchCreateNorms(id, request))

    @GetMapping("/{id}/norm-coverage")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun getNormCoverage(@PathVariable id: Long): ApiResponse<ScaleNormCoverage> =
        ApiResponse.ok(scaleService.getNormCoverage(id))
}
