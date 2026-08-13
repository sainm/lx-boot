import { describe, expect, it } from "vitest";
import type { TaskQuestionItem } from "./api";
import { answerSummary, countAnsweredQuestions, isQuestionAnswered, resolveSkippedQuestionNos } from "./answerProgress";

const question = (overrides: Partial<TaskQuestionItem>): TaskQuestionItem => ({
  questionId: 1,
  questionNo: 1,
  questionTitle: "Question",
  questionType: "SINGLE_CHOICE",
  requiredFlag: true,
  textInputEnabled: false,
  options: [],
  ...overrides
});

describe("assessment answer progress", () => {
  it("counts a multi-select answer as one answered question", () => {
    const questions = [question({ questionId: 10, questionType: "MULTI_SELECT" })];
    expect(countAnsweredQuestions(questions, { "question-10": [101, 102, 103] })).toBe(1);
  });

  it("does not count a text-with-option answer until required text is complete", () => {
    const item = question({ questionId: 20, questionType: "TEXT_WITH_OPTION", textInputEnabled: true });
    expect(isQuestionAnswered(item, { "question-20": 201 })).toBe(false);
    expect(isQuestionAnswered(item, { "question-20": 201, "question-20-text": "details" })).toBe(true);
  });

  it("builds a selected-option summary without exposing internal option ids", () => {
    const item = question({
      questionId: 30,
      questionType: "MULTI_SELECT",
      options: [
        { optionId: 301, optionCode: "A", optionLabel: "Alpha", scoreValue: 1 },
        { optionId: 302, optionCode: "B", optionLabel: "Beta", scoreValue: 2 }
      ]
    });
    expect(answerSummary(item, { "question-30": [301, 302] })).toBe("A. Alpha / B. Beta");
  });

  it("resolves declaration-only skip rules from the selected option code", () => {
    const questions = [
      question({
        questionId: 10,
        questionNo: 1,
        options: [{ optionId: 101, optionCode: "NO", optionLabel: "No", scoreValue: 0 }]
      }),
      question({ questionId: 20, questionNo: 2 })
    ];

    expect(resolveSkippedQuestionNos(
      questions,
      [{ whenQuestionNo: 1, whenOptionCode: "NO", skipQuestionNos: [2] }],
      { "question-10": 101 }
    )).toEqual(new Set([2]));
  });

  it("does not let a skipped trigger question cascade from a stale answer", () => {
    const questions = [
      question({
        questionId: 10,
        questionNo: 1,
        options: [{ optionId: 101, optionCode: "NO", optionLabel: "No", scoreValue: 0 }]
      }),
      question({
        questionId: 20,
        questionNo: 2,
        options: [{ optionId: 201, optionCode: "YES", optionLabel: "Yes", scoreValue: 1 }]
      }),
      question({ questionId: 30, questionNo: 3 })
    ];

    expect(resolveSkippedQuestionNos(
      questions,
      [
        { whenQuestionNo: 2, whenOptionCode: "YES", skipQuestionNos: [3] },
        { whenQuestionNo: 1, whenOptionCode: "NO", skipQuestionNos: [2] }
      ],
      { "question-10": 101, "question-20": 201 }
    )).toEqual(new Set([2]));
  });
});
