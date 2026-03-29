package org.sainm.psy.warning.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.warning.domain.WarningActionResult
import org.sainm.psy.warning.domain.WarningSummary
import org.sainm.psy.warning.service.WarningService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/warnings")
class WarningController(
    private val warningService: WarningService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun findPage(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) warningLevel: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<WarningSummary>> =
        ApiResponse.ok(
            warningService.findPage(
                WarningListQuery(
                    status = status,
                    warningLevel = warningLevel,
                    page = page,
                    size = size
                )
            )
        )

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun claim(@PathVariable id: Long): ApiResponse<WarningActionResult> =
        ApiResponse.ok(warningService.claim(id))

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun assign(
        @PathVariable id: Long,
        @Valid @RequestBody request: AssignWarningRequest
    ): ApiResponse<WarningActionResult> =
        ApiResponse.ok(warningService.assign(id, request))
}
