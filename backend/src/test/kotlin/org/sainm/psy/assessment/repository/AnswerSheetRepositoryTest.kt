package org.sainm.psy.assessment.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.sainm.psy.assessment.domain.TaskQuestionItem
import org.sainm.psy.assessment.domain.TaskQuestionOption
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.math.BigDecimal

class AnswerSheetRepositoryTest {
    private val repository = AnswerSheetRepository(
        mock<NamedParameterJdbcTemplate>(),
        jacksonObjectMapper()
    )

    @Test
    fun `strictly parses valid skip rules against the task question set`() {
        val rules = repository.parseSkipRules(
            """[{"whenQuestionNo":1,"whenOptionCode":"YES","skipQuestionNos":[2]}]""",
            questions()
        )

        assertEquals(1, rules.size)
        assertEquals(1, rules.single().whenQuestionNo)
        assertEquals(listOf(2), rules.single().skipQuestionNos)
    }

    @Test
    fun `rejects malformed or out of scope skip rules instead of dropping them`() {
        val malformed = assertThrows<IllegalArgumentException> {
            repository.parseSkipRules("{\"whenQuestionNo\":1}", questions())
        }
        assertEquals("TASK_SKIP_RULES_INVALID", malformed.message)

        val unknownReference = assertThrows<IllegalArgumentException> {
            repository.parseSkipRules(
                """[{"whenQuestionNo":1,"whenOptionCode":"MISSING","skipQuestionNos":[2]}]""",
                questions()
            )
        }
        assertEquals("TASK_SKIP_RULES_INVALID", unknownReference.message)

        val integerOverflow = assertThrows<IllegalArgumentException> {
            repository.parseSkipRules(
                """[{"whenQuestionNo":1,"whenOptionCode":"YES","skipQuestionNos":[2147483648]}]""",
                questions()
            )
        }
        assertEquals("TASK_SKIP_RULES_INVALID", integerOverflow.message)
    }

    private fun questions() = listOf(
        TaskQuestionItem(
            questionId = 101,
            questionNo = 1,
            questionTitle = "Trigger",
            questionType = "SINGLE_CHOICE",
            requiredFlag = true,
            optionSelectionLimit = null,
            sliderMin = null,
            sliderMax = null,
            sliderStep = null,
            options = listOf(
                TaskQuestionOption(1001, "YES", "Yes", BigDecimal.ONE, false),
                TaskQuestionOption(1002, "NO", "No", BigDecimal.ZERO, false)
            )
        ),
        TaskQuestionItem(
            questionId = 102,
            questionNo = 2,
            questionTitle = "Skipped",
            questionType = "TEXT",
            requiredFlag = true,
            optionSelectionLimit = null,
            sliderMin = null,
            sliderMax = null,
            sliderStep = null,
            options = emptyList()
        )
    )
}
