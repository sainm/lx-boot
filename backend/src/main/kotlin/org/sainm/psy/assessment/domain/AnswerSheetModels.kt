package org.sainm.psy.assessment.domain

import java.math.BigDecimal

data class TaskQuestionOption(
    val optionId: Long,
    val optionCode: String,
    val optionLabel: String,
    val scoreValue: BigDecimal
)

data class TaskQuestionItem(
    val questionId: Long,
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val requiredFlag: Boolean,
    val options: List<TaskQuestionOption>
)

data class TaskQuestionPayload(
    val taskId: Long,
    val scaleId: Long,
    val scaleName: String,
    val questions: List<TaskQuestionItem>
)

data class AnswerSubmitResult(
    val answerSheetId: Long,
    val resultId: Long,
    val reportId: Long,
    val riskLevel: String
)
