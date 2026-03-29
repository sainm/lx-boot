package org.sainm.psy.assessment.api

import jakarta.validation.Valid
import org.sainm.psy.assessment.domain.AnswerSubmitResult
import org.sainm.psy.assessment.domain.TaskQuestionPayload
import org.sainm.psy.assessment.service.AnswerSheetService
import org.sainm.psy.common.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AnswerSheetController(
    private val answerSheetService: AnswerSheetService
) {

    @GetMapping("/my/tasks/{taskId}/questions")
    fun getTaskQuestions(@PathVariable taskId: Long): ApiResponse<TaskQuestionPayload> =
        ApiResponse.ok(answerSheetService.getTaskQuestions(taskId))

    @PostMapping("/answer-sheets/save")
    fun save(@Valid @RequestBody request: SaveAnswerSheetRequest): ApiResponse<Map<String, Any>> =
        ApiResponse.ok(answerSheetService.save(request))

    @PostMapping("/answer-sheets/submit")
    fun submit(@Valid @RequestBody request: SubmitAnswerSheetRequest): ApiResponse<AnswerSubmitResult> =
        ApiResponse.ok(answerSheetService.submit(request))
}
