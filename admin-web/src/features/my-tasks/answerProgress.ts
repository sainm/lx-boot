import type { TaskQuestionItem, TaskSkipRule } from "./api";
import dayjs, { type Dayjs } from "dayjs";

export type AssessmentFormValues = Record<string, string | number | number[] | Dayjs | undefined>;

export function isQuestionAnswered(question: TaskQuestionItem, values: AssessmentFormValues): boolean {
  const key = `question-${question.questionId}`;
  const value = values[key];
  if (question.questionType === "MULTI_SELECT") {
    return Array.isArray(value) && value.length > 0;
  }
  if (question.questionType === "TEXT") {
    return typeof value === "string" && value.trim().length > 0;
  }
  if (question.questionType === "TIME") {
    return dayjs.isDayjs(value) || (typeof value === "string" && value.trim().length > 0);
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

export function resolveSkippedQuestionNos(
  questions: TaskQuestionItem[],
  skipRules: TaskSkipRule[],
  values: AssessmentFormValues
): Set<number> {
  const questionByNo = new Map(questions.map((question) => [question.questionNo, question]));
  const skipped = new Set<number>();
  for (const rule of [...skipRules].sort((left, right) => left.whenQuestionNo - right.whenQuestionNo)) {
    if (skipped.has(rule.whenQuestionNo)) continue;
    const triggerQuestion = questionByNo.get(rule.whenQuestionNo);
    if (!triggerQuestion) continue;
    const triggerOption = triggerQuestion.options.find((option) => option.optionCode === rule.whenOptionCode);
    if (!triggerOption) continue;
    const triggerValue = values[`question-${triggerQuestion.questionId}`];
    const selected = Array.isArray(triggerValue) ? triggerValue : [triggerValue];
    if (selected.includes(triggerOption.optionId)) {
      rule.skipQuestionNos.forEach((questionNo) => skipped.add(questionNo));
    }
  }
  return skipped;
}

export function answerSummary(question: TaskQuestionItem, values: AssessmentFormValues): string | null {
  if (!isQuestionAnswered(question, values)) return null;
  const key = `question-${question.questionId}`;
  const value = values[key];
  if (question.questionType === "TIME") {
    return dayjs.isDayjs(value) ? value.format("HH:mm") : String(value ?? "");
  }
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
