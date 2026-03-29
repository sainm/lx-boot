package org.sainm.psy.counseling.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.counseling.domain.CounselingRecordActionResult
import org.sainm.psy.counseling.service.CounselingService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/counseling-records")
class CounselingRecordController(
    private val counselingService: CounselingService
) {

    @PostMapping
    @PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateCounselingRecordRequest): ApiResponse<CounselingRecordActionResult> =
        ApiResponse.ok(counselingService.create(request))
}
