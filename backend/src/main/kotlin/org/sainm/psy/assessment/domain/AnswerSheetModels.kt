package org.sainm.psy.assessment.domain

import java.math.BigDecimal
import java.time.LocalDateTime

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

data class TaskSkipRule(
    val whenQuestionNo: Int,
    val whenOptionCode: String,
    val skipQuestionNos: List<Int>
)

data class TaskQuestionPayload(
    val taskId: Long,
    val scaleId: Long,
    val scaleName: String,
    val allowSaveFlag: Boolean,
    val allowRetakeFlag: Boolean = false,
    val anonymousFlag: Boolean = false,
    val allowTimeoutSubmitFlag: Boolean = false,
    val startTime: LocalDateTime = LocalDateTime.MIN,
    val endTime: LocalDateTime = LocalDateTime.MAX,
    val taskStatus: String = "IN_PROGRESS",
    val completedFlag: Boolean = false,
    val completedReportId: Long? = null,
    val completedResultId: Long? = null,
    val completedRiskLevel: String? = null,
    val draftAnswerSheetId: Long? = null,
    val draftVersionNo: Int? = null,
    val draftAnswers: List<TaskDraftAnswerItem> = emptyList(),
    val skipRules: List<TaskSkipRule> = emptyList(),
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
    val reportId: Long?,
    val riskLevel: String,
    val versionNo: Int? = null,
    val anonymous: Boolean = false
)

data class AnswerSheetRescoreContext(
    val answerSheetId: Long,
    val taskId: Long,
    val scaleId: Long,
    val userId: Long,
    val resultId: Long,
    val previousRiskLevel: String,
    val calculationVersion: Int,
    val responseLocaleCode: String? = null
)

data class AnswerSheetRescoreResult(
    val answerSheetId: Long,
    val resultId: Long,
    val reportId: Long,
    val totalScore: BigDecimal,
    val riskLevel: String,
    val previousRiskLevel: String,
    val previousResultId: Long? = null,
    val calculationVersion: Int? = null
)
