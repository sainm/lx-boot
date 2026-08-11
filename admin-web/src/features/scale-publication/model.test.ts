import { describe, expect, it } from "vitest";
import { buildGoldenCaseRequest, buildHistoricRunEvidence, formatPublicationBlocker, type GoldenCaseDraft } from "./model";
import type { ScaleGoldenCaseRun } from "./api";

const draft: GoldenCaseDraft = {
  caseCode: " NORMAL_001 ",
  caseType: "NORMAL",
  sourceReference: " manual page 1 ",
  answers: [{ questionNo: 1, optionCodes: " A, B ,,", answerText: " note " }],
  valid: true,
  dimensionsJson: "{\"D1\":{\"score\":2}}"
};

describe("Golden Case request builder", () => {
  it("normalizes codes and parses expected dimension evidence", () => {
    const request = buildGoldenCaseRequest(draft);
    expect(request.caseCode).toBe("NORMAL_001");
    expect(request.sourceReference).toBe("manual page 1");
    expect(request.input.answers[0].optionCodes).toEqual(["A", "B"]);
    expect(request.input.answers[0].answerText).toBe("note");
    expect(request.expected.dimensions.D1.score).toBe(2);
  });

  it("does not send an empty norm context", () => {
    expect(buildGoldenCaseRequest(draft).input.norm).toBeNull();
  });

  it("rejects malformed dimension JSON", () => {
    expect(() => buildGoldenCaseRequest({ ...draft, dimensionsJson: "{" })).toThrow();
  });

  it("parses persisted append-only run evidence for display", () => {
    const run: ScaleGoldenCaseRun = {
      id: 4, goldenCaseId: 3, scaleContentHash: "a", caseContentHash: "b",
      algorithmCode: "GENERIC", algorithmVersion: "1", passed: false,
      actualJson: '{"totalScore":2}', differencesJson: '["totalScore mismatch"]',
      executedBy: 8, executedAt: "2026-08-08T12:00:00"
    };
    expect(buildHistoricRunEvidence(run)).toEqual({
      runId: 4, goldenCaseId: 3, passed: false,
      actual: { totalScore: 2 }, differences: ["totalScore mismatch"]
    });
  });

  it("rejects malformed persisted evidence without crashing the page", () => {
    const run = {
      id: 4, goldenCaseId: 3, scaleContentHash: "a", caseContentHash: "b",
      passed: false, actualJson: "[]", differencesJson: "{}",
      executedBy: 8, executedAt: "2026-08-08T12:00:00"
    } as ScaleGoldenCaseRun;
    expect(() => buildHistoricRunEvidence(run)).toThrow("INVALID_GOLDEN_CASE_RUN_EVIDENCE");
  });

  it("turns stale evidence and missing review blockers into localizable messages", () => {
    const translate = (key: string, params?: Record<string, string | number>) => `${key}:${params?.caseCode ?? ""}`;

    expect(formatPublicationBlocker("GOLDEN_CASE_STALE:NORMAL-1", translate))
      .toBe("scalePublication.blocker.goldenCaseStale:NORMAL-1");
    expect(formatPublicationBlocker("REVIEW_PROFESSIONAL_MISSING", translate))
      .toBe("scalePublication.blocker.professionalReviewMissing:");
    expect(formatPublicationBlocker("UNKNOWN:DETAIL", translate)).toBe("UNKNOWN:DETAIL");
  });
});
