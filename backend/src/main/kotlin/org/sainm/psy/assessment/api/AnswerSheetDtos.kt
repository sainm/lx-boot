package org.sainm.psy.assessment.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class AnswerItemRequest(
    @field:NotNull(message = "题目不能为空")
    val questionId: Long,

    val optionId: Long? = null,
    val answerText: String? = null
)

data class SaveAnswerSheetRequest(
    @field:NotNull(message = "任务不能为空")
    val taskId: Long,

    @field:NotNull(message = "量表不能为空")
    val scaleId: Long,

    @field:Valid
    val answers: List<AnswerItemRequest> = emptyList()
)

data class SubmitAnswerSheetRequest(
    @field:NotNull(message = "任务不能为空")
    val taskId: Long,

    @field:NotNull(message = "量表不能为空")
    val scaleId: Long,

    @field:NotEmpty(message = "答题内容不能为空")
    @field:Valid
    val answers: List<AnswerItemRequest>
)
