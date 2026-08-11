import type { TaskQuestionItem } from "./api";

export type AssessmentFormValues = Record<string, string | number | number[] | undefined>;

export function isQuestionAnswered(question: TaskQuestionItem, values: AssessmentFormValues): boolean {
  const key = `question-${question.questionId}`;
  const value = values[key];
  if (question.questionType === "MULTI_SELECT") {
    return Array.isArray(value) && value.length > 0;
  }
  if (question.questionType === "TEXT") {
    return typeof value === "string" && value.trim().length > 0;
  }
  if (value === undefined || value === null || value === "") {
    return false;
  }
  if (question.questionType === "TEXT_WITH_OPTION" && question.textInputEnabled) {
    const textValue = values[`${key}-text`];
    return typeof textValue === "string" && textValue.trim().length > 0;
  }
  return true;
}

export function countAnsweredQuestions(questions: TaskQuestionItem[], values: AssessmentFormValues): number {
  return questions.filter((question) => isQuestionAnswered(question, values)).length;
}

export function answerSummary(question: TaskQuestionItem, values: AssessmentFormValues): string | null {
  if (!isQuestionAnswered(question, values)) return null;
  const key = `question-${question.questionId}`;
  const value = values[key];
  if (question.questionType === "TEXT" || question.questionType === "SLIDER") {
    return String(value);
  }
  const selectedIds = Array.isArray(value) ? value.map(Number) : [Number(value)];
  const labels = question.options
    .filter((option) => selectedIds.includes(option.optionId))
    .map((option) => `${option.optionCode}. ${option.optionLabel}`);
  if (question.questionType === "TEXT_WITH_OPTION") {
    const text = values[`${key}-text`];
    if (typeof text === "string" && text.trim()) labels.push(text.trim());
  }
  return labels.join(" / ");
}
