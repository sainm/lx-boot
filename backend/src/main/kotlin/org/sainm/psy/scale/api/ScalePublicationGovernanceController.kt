package org.sainm.psy.scale.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.CursorPage
import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScalePublicationHistory
import org.sainm.psy.scale.domain.ScalePublicationReadiness
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.scale.service.ScalePublicationGovernanceService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/scales/{scaleId}/publication")
@PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
class ScalePublicationGovernanceController(private val service: ScalePublicationGovernanceService) {
    @GetMapping("/golden-cases")
    fun listGoldenCases(@PathVariable scaleId: Long): ApiResponse<List<ScaleGoldenCase>> =
        ApiResponse.ok(service.listGoldenCases(scaleId))

    @GetMapping("/history")
    fun history(@PathVariable scaleId: Long): ApiResponse<ScalePublicationHistory> =
        ApiResponse.ok(service.history(scaleId))

    /**
     * Bounded keyset endpoints for append-only evidence. The legacy history
     * endpoint remains unchanged for existing clients and package export;
     * management UIs can use these endpoints when history grows large.
     */
    @GetMapping("/history/cases")
    fun historyCases(
        @PathVariable scaleId: Long,
        @RequestParam(required = false) afterId: Long?,
        @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<CursorPage<ScaleGoldenCase>> =
        ApiResponse.ok(service.historyCases(scaleId, afterId, limit))

    @GetMapping("/history/runs")
    fun historyRuns(
        @PathVariable scaleId: Long,
        @RequestParam(required = false) afterId: Long?,
        @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<CursorPage<ScaleGoldenCaseRun>> =
        ApiResponse.ok(service.historyRuns(scaleId, afterId, limit))

    @GetMapping("/history/reviews")
    fun historyReviews(
        @PathVariable scaleId: Long,
        @RequestParam(required = false) afterId: Long?,
        @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<CursorPage<ScalePublicationReview>> =
        ApiResponse.ok(service.historyReviews(scaleId, afterId, limit))

    @PostMapping("/golden-cases")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun saveGoldenCase(
        @PathVariable scaleId: Long,
        @Valid @RequestBody request: CreateScaleGoldenCaseRequest
    ): ApiResponse<ScaleGoldenCase> = ApiResponse.ok(service.saveGoldenCase(scaleId, request))

    @PostMapping("/golden-cases/{caseId}/run")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun runGoldenCase(@PathVariable scaleId: Long, @PathVariable caseId: Long): ApiResponse<GoldenCaseRunResponse> =
        ApiResponse.ok(service.runGoldenCase(scaleId, caseId))

    @PostMapping("/golden-cases/{caseId}/approve")
    @PreAuthorize("hasRole('COUNSELOR')")
    fun approveGoldenCase(@PathVariable scaleId: Long, @PathVariable caseId: Long): ApiResponse<ScaleGoldenCase> =
        ApiResponse.ok(service.approveGoldenCase(scaleId, caseId))

    @GetMapping("/readiness")
    fun readiness(@PathVariable scaleId: Long): ApiResponse<ScalePublicationReadiness> =
        ApiResponse.ok(service.readiness(scaleId))

    @PostMapping("/reviews/{reviewType}")
    fun review(
        @PathVariable scaleId: Long,
        @PathVariable reviewType: String,
        @Valid @RequestBody request: ScalePublicationReviewRequest
    ): ApiResponse<ScalePublicationReview> = ApiResponse.ok(service.review(scaleId, reviewType, request))
}
