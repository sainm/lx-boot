package org.sainm.psy.assessment.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class AnswerItemRequest(
    @field:NotNull(message = "{validation.question_id_required}")
    val questionId: Long,

    val optionId: Long? = null,
    val answerText: String? = null,
    val answerValue: BigDecimal? = null
)

data class SaveAnswerSheetRequest(
    @field:NotNull(message = "{validation.task_id_required}")
    val taskId: Long,

    @field:NotNull(message = "{validation.scale_id_required}")
    val scaleId: Long,

    val answerSheetId: Long? = null,
    val versionNo: Int? = null,

    @field:Valid
    val answers: List<AnswerItemRequest> = emptyList()
)

data class SubmitAnswerSheetRequest(
    @field:NotNull(message = "{validation.task_id_required}")
    val taskId: Long,

    @field:NotNull(message = "{validation.scale_id_required}")
    val scaleId: Long,

    val answerSheetId: Long? = null,
    val versionNo: Int? = null,
    val submitToken: String? = null,

    @field:NotEmpty(message = "{validation.answers_required}")
    @field:Valid
    val answers: List<AnswerItemRequest>
)
