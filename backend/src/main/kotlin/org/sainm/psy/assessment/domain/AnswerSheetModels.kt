package org.sainm.psy.assessment.domain

import java.math.BigDecimal

data class TaskQuestionOption(
    val optionId: Long,
    val optionCode: String,
    val optionLabel: String,
    val scoreValue: BigDecimal,
    val exclusiveFlag: Boolean
)

data class TaskQuestionItem(
    val questionId: Long,
    val questionNo: Int,
    val questionTitle: String,
    val questionType: String,
    val requiredFlag: Boolean,
    val optionSelectionLimit: Int?,
    val sliderMin: BigDecimal?,
    val sliderMax: BigDecimal?,
    val sliderStep: BigDecimal?,
    val textInputEnabled: Boolean = false,
    val textInputPlaceholder: String? = null,
    val matrixGroupCode: String? = null,
    val rowCode: String? = null,
    val columnCode: String? = null,
    val options: List<TaskQuestionOption>
)

data class TaskDraftAnswerItem(
    val questionId: Long,
    val optionId: Long? = null,
    val answerText: String? = null,
    val answerValue: BigDecimal? = null
)

data class TaskQuestionPayload(
    val taskId: Long,
    val scaleId: Long,
    val scaleName: String,
    val allowSaveFlag: Boolean,
    val allowRetakeFlag: Boolean = false,
    val completedFlag: Boolean = false,
    val completedReportId: Long? = null,
    val completedResultId: Long? = null,
    val completedRiskLevel: String? = null,
    val draftAnswerSheetId: Long? = null,
    val draftVersionNo: Int? = null,
    val draftAnswers: List<TaskDraftAnswerItem> = emptyList(),
    val questions: List<TaskQuestionItem>
)

data class AnswerSheetDraftSaveResult(
    val answerSheetId: Long,
    val status: String,
    val versionNo: Int
)

data class AnswerSubmitResult(
    val answerSheetId: Long,
    val resultId: Long,
    val reportId: Long,
    val riskLevel: String,
    val versionNo: Int? = null
)

data class AnswerSheetRescoreContext(
    val answerSheetId: Long,
    val taskId: Long,
    val scaleId: Long,
    val userId: Long,
    val resultId: Long,
    val previousRiskLevel: String
)

data class AnswerSheetRescoreResult(
    val answerSheetId: Long,
    val resultId: Long,
    val reportId: Long,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val previousRiskLevel: String
)
