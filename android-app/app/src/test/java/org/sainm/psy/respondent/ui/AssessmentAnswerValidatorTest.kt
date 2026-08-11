package org.sainm.psy.respondent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sainm.psy.respondent.data.model.AnswerItemRequest
import org.sainm.psy.respondent.data.model.TaskQuestionItem
import org.sainm.psy.respondent.data.model.TaskQuestionOption
import org.sainm.psy.respondent.data.model.TaskQuestionPayload

class AssessmentAnswerValidatorTest {
    @Test
    fun `required question is rejected only for final validation`() {
        val payload = payload(question(type = "SINGLE_CHOICE", required = true, options = listOf(option(11))))

        assertNull(AssessmentAnswerValidator.validate(payload, emptyList(), requireCompleteAnswers = false))
        assertEquals(
            AnswerValidationCode.REQUIRED_QUESTION,
            AssessmentAnswerValidator.validate(payload, emptyList(), requireCompleteAnswers = true)?.code
        )
    }

    @Test
    fun `exclusive multi select cannot be combined with another option`() {
        val payload = payload(
            question(
                type = "MULTI_SELECT",
                options = listOf(option(11, exclusive = true), option(12))
            )
        )

        val issue = AssessmentAnswerValidator.validate(
            payload,
            listOf(AnswerItemRequest(1, optionId = 11), AnswerItemRequest(1, optionId = 12))
        )

        assertEquals(AnswerValidationCode.EXCLUSIVE_CONFLICT, issue?.code)
    }

    @Test
    fun `slider value must match configured step`() {
        val payload = payload(
            question(type = "SLIDER", sliderMin = 0.0, sliderMax = 10.0, sliderStep = 2.0)
        )

        val issue = AssessmentAnswerValidator.validate(
            payload,
            listOf(AnswerItemRequest(1, answerValue = 3.0))
        )

        assertEquals(AnswerValidationCode.SLIDER_STEP, issue?.code)
    }

    @Test
    fun `unknown answered question type is rejected explicitly`() {
        val payload = payload(question(type = "RANKING"))

        val issue = AssessmentAnswerValidator.validate(
            payload,
            listOf(AnswerItemRequest(1, answerText = "fixture"))
        )

        assertEquals(AnswerValidationCode.UNSUPPORTED_TYPE, issue?.code)
    }

    @Test
    fun `valid single choice passes`() {
        val payload = payload(question(type = "SINGLE_CHOICE", options = listOf(option(11))))

        assertNull(
            AssessmentAnswerValidator.validate(
                payload,
                listOf(AnswerItemRequest(1, optionId = 11))
            )
        )
    }

    private fun payload(question: TaskQuestionItem) = TaskQuestionPayload(
        taskId = 10,
        scaleId = 20,
        scaleName = "fixture",
        allowSaveFlag = true,
        questions = listOf(question)
    )

    private fun question(
        type: String,
        required: Boolean = true,
        options: List<TaskQuestionOption> = emptyList(),
        sliderMin: Double? = null,
        sliderMax: Double? = null,
        sliderStep: Double? = null
    ) = TaskQuestionItem(
        questionId = 1,
        questionNo = 1,
        questionTitle = "fixture",
        questionType = type,
        requiredFlag = required,
        sliderMin = sliderMin,
        sliderMax = sliderMax,
        sliderStep = sliderStep,
        options = options
    )

    private fun option(id: Long, exclusive: Boolean = false) = TaskQuestionOption(
        optionId = id,
        optionCode = "O$id",
        optionLabel = "fixture",
        scoreValue = 1.0,
        exclusiveFlag = exclusive
    )
}
