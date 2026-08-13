import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../../services/http";
import {
  approveScaleGoldenCase,
  fetchScaleGoldenCases,
  fetchScalePublicationHistory,
  fetchScalePublicationReadiness,
  runScaleGoldenCase,
  saveScaleGoldenCase,
  submitScalePublicationReview
} from "./api";

vi.mock("../../services/http", () => ({
  http: { get: vi.fn(), post: vi.fn() }
}));

const response = { data: { data: { id: 1 } } };

describe("scale publication API", () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset();
    vi.mocked(http.post).mockReset();
    vi.mocked(http.get).mockResolvedValue(response);
    vi.mocked(http.post).mockResolvedValue(response);
  });

  it("loads tenant-scoped readiness for one scale version", async () => {
    await fetchScalePublicationReadiness(17);
    expect(http.get).toHaveBeenCalledWith("/scales/17/publication/readiness");
  });

  it("runs and approves a concrete Golden Case revision", async () => {
    await runScaleGoldenCase(17, 23);
    await approveScaleGoldenCase(17, 23);
    expect(http.post).toHaveBeenNthCalledWith(1, "/scales/17/publication/golden-cases/23/run");
    expect(http.post).toHaveBeenNthCalledWith(2, "/scales/17/publication/golden-cases/23/approve");
  });

  it("lists and creates a Golden Case revision", async () => {
    const payload = {
      caseCode: "NORMAL_001",
      caseType: "NORMAL" as const,
      sourceReference: "manual section 1",
      input: { answers: [{ questionNo: 1, optionCodes: ["A"] }] },
      expected: { valid: true, totalScore: 1, dimensions: {} }
    };
    await fetchScaleGoldenCases(17);
    await saveScaleGoldenCase(17, payload);
    expect(http.get).toHaveBeenCalledWith("/scales/17/publication/golden-cases");
    expect(http.post).toHaveBeenCalledWith("/scales/17/publication/golden-cases", payload);
  });

  it("loads the append-only Golden Case run and publication review history", async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce({ data: { data: { list: [], nextCursor: null, limit: 50 } } })
      .mockResolvedValueOnce({ data: { data: { list: [], nextCursor: null, limit: 50 } } })
      .mockResolvedValueOnce({ data: { data: { list: [], nextCursor: null, limit: 50 } } });
    await fetchScalePublicationHistory(17);
    expect(http.get).toHaveBeenNthCalledWith(1, "/scales/17/publication/history/cases?limit=50");
    expect(http.get).toHaveBeenNthCalledWith(2, "/scales/17/publication/history/runs?limit=50");
    expect(http.get).toHaveBeenNthCalledWith(3, "/scales/17/publication/history/reviews?limit=50");
  });

  it("submits a review with a stable idempotency token", async () => {
    const payload = {
      decision: "APPROVED" as const,
      reviewToken: "review-17",
      comment: "checked",
      qualificationReference: "credential-register:reviewer-5",
      evidenceReference: "controlled-review:K6-v1",
      reviewScope: "Reviewed source, three languages, scoring boundary, interpretation, and reports."
    };
    await submitScalePublicationReview(17, "PROFESSIONAL", payload);
    expect(http.post).toHaveBeenCalledWith("/scales/17/publication/reviews/PROFESSIONAL", payload);
  });
});
