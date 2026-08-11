package org.sainm.psy.respondent.ui

import android.content.Context
import androidx.annotation.StringRes
import org.sainm.psy.respondent.R
import org.sainm.psy.respondent.data.model.AnswerItemRequest
import org.sainm.psy.respondent.data.model.TaskQuestionItem
import org.sainm.psy.respondent.data.model.TaskQuestionPayload
import java.math.BigDecimal

internal data class AnswerValidationIssue(
    val code: AnswerValidationCode,
    val arguments: List<Any>
) {
    fun localizedMessage(context: Context): String =
        context.getString(code.messageRes, *arguments.toTypedArray())
}

internal enum class AnswerValidationCode(@StringRes val messageRes: Int) {
    INVALID_QUESTION(R.string.validation_invalid_question),
    REQUIRED_QUESTION(R.string.validation_required_question),
    UNSUPPORTED_TYPE(R.string.validation_unsupported_type),
    ONE_OPTION(R.string.validation_one_option),
    INVALID_OPTION(R.string.validation_invalid_option),
    NO_NUMERIC(R.string.validation_no_numeric),
    NO_TEXT(R.string.validation_no_text),
    AT_LEAST_ONE_OPTION(R.string.validation_at_least_one_option),
    DUPLICATE_MULTI_SELECT(R.string.validation_duplicate_multi_select),
    SELECTION_LIMIT(R.string.validation_selection_limit),
    EXCLUSIVE_CONFLICT(R.string.validation_exclusive_conflict),
    ONE_SLIDER(R.string.validation_one_slider),
    SLIDER_REQUIRED(R.string.validation_slider_required),
    NO_OPTION(R.string.validation_no_option),
    SLIDER_RANGE(R.string.validation_slider_range),
    SLIDER_STEP(R.string.validation_slider_step),
    ONE_TEXT(R.string.validation_one_text),
    TEXT_REQUIRED(R.string.validation_text_required)
}

internal object AssessmentAnswerValidator {
    fun validate(
        payload: TaskQuestionPayload,
        answers: List<AnswerItemRequest>,
        requireCompleteAnswers: Boolean = true
    ): AnswerValidationIssue? {
        val questionMap = payload.questions.associateBy { it.questionId }
        val answersByQuestionId = answers.groupBy { it.questionId }
        answersByQuestionId.keys.firstOrNull { it !in questionMap }?.let {
            return issue(AnswerValidationCode.INVALID_QUESTION, it)
        }

        payload.questions.forEach { question ->
            val questionAnswers = answersByQuestionId[question.questionId].orEmpty()
            if (requireCompleteAnswers && question.requiredFlag && questionAnswers.isEmpty()) {
                return issue(AnswerValidationCode.REQUIRED_QUESTION, question.questionNo)
            }
            validateQuestion(question, questionAnswers)?.let { return it }
        }
        return null
    }

    private fun validateQuestion(
        question: TaskQuestionItem,
        answers: List<AnswerItemRequest>
    ): AnswerValidationIssue? {
        if (answers.isEmpty()) return null
        return when (question.questionType) {
            "SINGLE_CHOICE", "MATRIX" -> validateSingleChoice(question, answers)
            "MULTI_SELECT" -> validateMultiSelect(question, answers)
            "SLIDER" -> validateSlider(question, answers)
            "TEXT" -> validateText(question, answers)
            "TEXT_WITH_OPTION" -> validateTextWithOption(question, answers)
            else -> issue(AnswerValidationCode.UNSUPPORTED_TYPE, question.questionType)
        }
    }

    private fun validateSingleChoice(
        question: TaskQuestionItem,
        answers: List<AnswerItemRequest>
    ): AnswerValidationIssue? {
        if (answers.size != 1) return issue(AnswerValidationCode.ONE_OPTION, question.questionNo)
        val answer = answers.first()
        if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
            return issue(AnswerValidationCode.INVALID_OPTION, question.questionNo)
        }
        if (answer.answerValue != null) return issue(AnswerValidationCode.NO_NUMERIC, question.questionNo)
        if (!answer.answerText.isNullOrBlank()) return issue(AnswerValidationCode.NO_TEXT, question.questionNo)
        return null
    }

    private fun validateMultiSelect(
        question: TaskQuestionItem,
        answers: List<AnswerItemRequest>
    ): AnswerValidationIssue? {
        if (answers.isEmpty()) return issue(AnswerValidationCode.AT_LEAST_ONE_OPTION, question.questionNo)
        val optionIds = answers.mapNotNull { it.optionId }
        if (optionIds.size != answers.size || optionIds.distinct().size != optionIds.size) {
            return issue(AnswerValidationCode.DUPLICATE_MULTI_SELECT, question.questionNo)
        }
        val optionMap = question.options.associateBy { it.optionId }
        if (optionIds.any { it !in optionMap }) return issue(AnswerValidationCode.INVALID_OPTION, question.questionNo)
        if (question.optionSelectionLimit != null && optionIds.size > question.optionSelectionLimit) {
            return issue(AnswerValidationCode.SELECTION_LIMIT, question.questionNo, question.optionSelectionLimit)
        }
        val exclusiveSelected = optionIds.count { optionMap.getValue(it).exclusiveFlag }
        if (exclusiveSelected > 1 || (exclusiveSelected == 1 && optionIds.size > 1)) {
            return issue(AnswerValidationCode.EXCLUSIVE_CONFLICT, question.questionNo)
        }
        if (answers.any { it.answerValue != null }) return issue(AnswerValidationCode.NO_NUMERIC, question.questionNo)
        if (answers.any { !it.answerText.isNullOrBlank() }) return issue(AnswerValidationCode.NO_TEXT, question.questionNo)
        return null
    }

    private fun validateSlider(
        question: TaskQuestionItem,
        answers: List<AnswerItemRequest>
    ): AnswerValidationIssue? {
        if (answers.size != 1) return issue(AnswerValidationCode.ONE_SLIDER, question.questionNo)
        val answer = answers.first()
        val value = answer.answerValue ?: return issue(AnswerValidationCode.SLIDER_REQUIRED, question.questionNo)
        if (answer.optionId != null) return issue(AnswerValidationCode.NO_OPTION, question.questionNo)
        if (!answer.answerText.isNullOrBlank()) return issue(AnswerValidationCode.NO_TEXT, question.questionNo)
        val min = question.sliderMin
        val max = question.sliderMax
        if (min == null || max == null || value < min || value > max) {
            return issue(AnswerValidationCode.SLIDER_RANGE, question.questionNo)
        }
        question.sliderStep?.takeIf { it > 0.0 }?.let { step ->
            val offset = BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(min))
            if (offset.remainder(BigDecimal.valueOf(step)).compareTo(BigDecimal.ZERO) != 0) {
                return issue(AnswerValidationCode.SLIDER_STEP, question.questionNo, step.toDisplayValue())
            }
        }
        return null
    }

    private fun validateText(
        question: TaskQuestionItem,
        answers: List<AnswerItemRequest>
    ): AnswerValidationIssue? {
        if (answers.size != 1) return issue(AnswerValidationCode.ONE_TEXT, question.questionNo)
        val answer = answers.first()
        if (answer.optionId != null || answer.answerValue != null) return issue(AnswerValidationCode.NO_OPTION, question.questionNo)
        if (answer.answerText.isNullOrBlank()) return issue(AnswerValidationCode.TEXT_REQUIRED, question.questionNo)
        return null
    }

    private fun validateTextWithOption(
        question: TaskQuestionItem,
        answers: List<AnswerItemRequest>
    ): AnswerValidationIssue? {
        if (answers.size != 1) return issue(AnswerValidationCode.ONE_OPTION, question.questionNo)
        val answer = answers.first()
        if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
            return issue(AnswerValidationCode.INVALID_OPTION, question.questionNo)
        }
        if (answer.answerValue != null) return issue(AnswerValidationCode.NO_NUMERIC, question.questionNo)
        if (question.textInputEnabled == true && answer.answerText.isNullOrBlank()) {
            return issue(AnswerValidationCode.TEXT_REQUIRED, question.questionNo)
        }
        if (question.textInputEnabled != true && !answer.answerText.isNullOrBlank()) {
            return issue(AnswerValidationCode.NO_TEXT, question.questionNo)
        }
        return null
    }

    private fun issue(code: AnswerValidationCode, vararg arguments: Any) =
        AnswerValidationIssue(code, arguments.toList())
}

private fun Double.toDisplayValue(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
