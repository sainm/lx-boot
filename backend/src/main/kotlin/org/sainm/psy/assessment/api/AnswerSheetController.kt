package org.sainm.psy.assessment.api

import jakarta.validation.Valid
import org.sainm.psy.assessment.domain.AnswerSheetDraftSaveResult
import org.sainm.psy.assessment.domain.AnswerSheetRescoreResult
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.service.AnswerSheetService
import org.sainm.psy.common.api.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AnswerSheetController(
    private val answerSheetService: AnswerSheetService
) {

    @GetMapping("/my/tasks/{taskId}/questions")
    @PreAuthorize("hasRole('USER')")
    fun getTaskQuestions(@PathVariable taskId: Long): ApiResponse<TaskQuestionPayload> =
        ApiResponse.ok(answerSheetService.getTaskQuestions(taskId))

    @PostMapping("/answer-sheets/save")
    @PreAuthorize("hasRole('USER')")
    fun save(@Valid @RequestBody request: SaveAnswerSheetRequest): ApiResponse<AnswerSheetDraftSaveResult> =
        ApiResponse.ok(answerSheetService.save(request))

    @PostMapping("/answer-sheets/submit")
    @PreAuthorize("hasRole('USER')")
    fun submit(
        @Valid @RequestBody request: SubmitAnswerSheetRequest,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
    ): ApiResponse<AnswerSubmitResult> =
        ApiResponse.ok(
            answerSheetService.submit(
                request.copy(submitToken = request.submitToken ?: idempotencyKey?.takeIf { it.isNotBlank() })
            )
        )

    @PostMapping("/results/{resultId}/rescore")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun rescoreResult(@PathVariable resultId: Long): ApiResponse<AnswerSheetRescoreResult> =
        ApiResponse.ok(answerSheetService.rescoreResult(resultId))
}
