package org.sainm.psy.respondent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sainm.psy.respondent.R
import org.sainm.psy.respondent.data.model.AnswerItemRequest
import org.sainm.psy.respondent.data.model.TaskQuestionItem

@Composable
internal fun AssessmentReviewScreen(
    questions: List<TaskQuestionItem>,
    answers: List<AnswerItemRequest>,
    processing: Boolean,
    message: String?,
    onEdit: (Int) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                stringResource(R.string.review_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.review_description),
                color = Color(0xFF587082),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        item {
            ReviewCard(containerColor = Color(0xFFF1F7FB)) {
                Text(stringResource(R.string.review_privacy_notice), color = Color(0xFF31536B))
            }
        }
        if (message != null) {
            item {
                ReviewCard(containerColor = Color(0xFFFFF4F2)) {
                    Text(
                        message,
                        color = Color(0xFF9A3C2B),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                }
            }
        }
        itemsIndexed(questions, key = { _, question -> question.questionId }) { index, question ->
            val questionAnswers = answers.filter { it.questionId == question.questionId }
            ReviewCard {
                Text(
                    stringResource(R.string.question_title_format, question.questionNo, question.questionTitle),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    reviewAnswerText(question, questionAnswers)
                        ?: stringResource(R.string.review_answer_missing),
                    color = if (questionAnswers.isEmpty()) Color(0xFF9A3C2B) else Color(0xFF466173),
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(
                    onClick = { onEdit(index) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.action_edit_answer))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, enabled = !processing, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_back))
                }
                Button(onClick = onSubmit, enabled = !processing, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_submit))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    containerColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

private fun reviewAnswerText(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.isEmpty()) return null
    val optionsById = question.options.associateBy { it.optionId }
    return answers.mapNotNull { answer ->
        val option = answer.optionId?.let { optionsById[it] }
        listOfNotNull(
            option?.let { "${it.optionCode}. ${it.optionLabel}" },
            answer.answerText?.takeIf(String::isNotBlank),
            answer.answerValue?.toDisplayValue()
        ).joinToString(" · ").takeIf(String::isNotBlank)
    }.joinToString(", ").takeIf(String::isNotBlank)
}

private fun Double.toDisplayValue(): String =
    java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
