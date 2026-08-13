import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type ScaleGoldenCase = {
  id: number;
  scaleId: number;
  caseCode: string;
  revisionNo: number;
  caseType: string;
  sourceReference: string;
  scaleContentHash: string;
  caseContentHash: string;
  inputJson: string;
  expectedJson: string;
  createdBy: number;
  createdAt: string;
  approvedBy?: number | null;
  approvedAt?: string | null;
};

export type ScaleGoldenCaseReadiness = {
  id: number;
  caseCode: string;
  revisionNo: number;
  caseType: string;
  currentContent: boolean;
  approved: boolean;
  latestRunPassed: boolean;
};

export type ScaleGoldenCaseRun = {
  id: number;
  goldenCaseId: number;
  scaleContentHash: string;
  caseContentHash: string;
  algorithmCode?: string | null;
  algorithmVersion?: string | null;
  passed: boolean;
  actualJson: string;
  differencesJson: string;
  executedBy: number;
  executedAt: string;
};

export type ScaleGoldenCaseHistory = {
  goldenCase: ScaleGoldenCase;
  runs: ScaleGoldenCaseRun[];
};

export type ScalePublicationReview = {
  id: number;
  reviewType: "PROFESSIONAL" | "BUSINESS";
  decision: "APPROVED" | "REJECTED";
  reviewerId: number;
  reviewerRoleSnapshot: string;
  reviewerNameSnapshot?: string | null;
  qualificationReference?: string | null;
  evidenceReference?: string | null;
  reviewScope?: string | null;
  scaleContentHash: string;
  releaseFingerprint: string;
  commentText?: string | null;
  createdAt: string;
};

export type ScalePublicationReadiness = {
  scaleId: number;
  scaleContentHash: string;
  releaseFingerprint: string;
  ready: boolean;
  requiredCaseTypes: string[];
  cases: ScaleGoldenCaseReadiness[];
  professionalReview?: ScalePublicationReview | null;
  businessReview?: ScalePublicationReview | null;
  blockers: string[];
};

export type ScalePublicationHistory = {
  cases: ScaleGoldenCaseHistory[];
  reviews: ScalePublicationReview[];
  caseNextCursor?: number | null;
  runNextCursor?: number | null;
  reviewNextCursor?: number | null;
};

export type CursorPage<T> = {
  list: T[];
  nextCursor?: number | null;
  limit: number;
};

export type GoldenCaseRunResponse = {
  runId: number;
  goldenCaseId: number;
  passed: boolean;
  actual: Record<string, unknown>;
  differences: string[];
};

export type GoldenCaseAnswerInput = {
  questionNo: number;
  optionCodes: string[];
  answerValue?: number | null;
  answerText?: string | null;
};

export type CreateScaleGoldenCaseRequest = {
  caseCode: string;
  caseType: "NORMAL" | "BOUNDARY" | "REVERSE" | "MISSING" | "INVALID" | "HIGH_RISK";
  sourceReference: string;
  input: {
    answers: GoldenCaseAnswerInput[];
    durationSeconds?: number | null;
    norm?: {
      age?: number | null;
      gender?: string | null;
      orgType?: string | null;
      applicableTarget?: string | null;
      preferredNormCode?: string | null;
    } | null;
  };
  expected: {
    valid: boolean;
    errorCode?: string | null;
    totalScore?: number | null;
    riskLevel?: string | null;
    highRiskTriggered?: boolean | null;
    highRiskRuleCode?: string | null;
    normCode?: string | null;
    dimensions: Record<string, { score: number; riskLevel?: string | null; normCode?: string | null }>;
    metrics?: Record<string, number>;
  };
};

export type PublicationReviewRequest = {
  decision: "APPROVED" | "REJECTED";
  reviewToken: string;
  comment?: string;
  qualificationReference?: string;
  evidenceReference?: string;
  reviewScope?: string;
};

export async function fetchScalePublicationReadiness(scaleId: number) {
  const response = await http.get<ApiResponse<ScalePublicationReadiness>>(`/scales/${scaleId}/publication/readiness`);
  return response.data.data;
}

export async function fetchScaleGoldenCases(scaleId: number) {
  const response = await http.get<ApiResponse<ScaleGoldenCase[]>>(`/scales/${scaleId}/publication/golden-cases`);
  return response.data.data;
}

export async function fetchScalePublicationHistory(scaleId: number) {
  const [casesResponse, runsResponse, reviewsResponse] = await Promise.all([
    http.get<ApiResponse<CursorPage<ScaleGoldenCase>>>(`/scales/${scaleId}/publication/history/cases?limit=50`),
    http.get<ApiResponse<CursorPage<ScaleGoldenCaseRun>>>(`/scales/${scaleId}/publication/history/runs?limit=50`),
    http.get<ApiResponse<CursorPage<ScalePublicationReview>>>(`/scales/${scaleId}/publication/history/reviews?limit=50`)
  ]);
  const casesPage = casesResponse.data.data;
  const runsPage = runsResponse.data.data;
  const reviewsPage = reviewsResponse.data.data;
  const runsByCase = new Map<number, ScaleGoldenCaseRun[]>();
  for (const run of runsPage?.list ?? []) {
    const runs = runsByCase.get(run.goldenCaseId) ?? [];
    runs.push(run);
    runsByCase.set(run.goldenCaseId, runs);
  }
  return {
    cases: (casesPage?.list ?? []).map((goldenCase) => ({
      goldenCase,
      runs: runsByCase.get(goldenCase.id) ?? []
    })),
    reviews: reviewsPage?.list ?? [],
    caseNextCursor: casesPage?.nextCursor ?? null,
    runNextCursor: runsPage?.nextCursor ?? null,
    reviewNextCursor: reviewsPage?.nextCursor ?? null
  } satisfies ScalePublicationHistory;
}

export async function saveScaleGoldenCase(scaleId: number, payload: CreateScaleGoldenCaseRequest) {
  const response = await http.post<ApiResponse<ScaleGoldenCase>>(`/scales/${scaleId}/publication/golden-cases`, payload);
  return response.data.data;
}

export async function runScaleGoldenCase(scaleId: number, caseId: number) {
  const response = await http.post<ApiResponse<GoldenCaseRunResponse>>(
    `/scales/${scaleId}/publication/golden-cases/${caseId}/run`
  );
  return response.data.data;
}

export async function approveScaleGoldenCase(scaleId: number, caseId: number) {
  const response = await http.post<ApiResponse<ScaleGoldenCase>>(
    `/scales/${scaleId}/publication/golden-cases/${caseId}/approve`
  );
  return response.data.data;
}

export async function submitScalePublicationReview(
  scaleId: number,
  reviewType: "PROFESSIONAL" | "BUSINESS",
  payload: PublicationReviewRequest
) {
  const response = await http.post<ApiResponse<ScalePublicationReview>>(
    `/scales/${scaleId}/publication/reviews/${reviewType}`,
    payload
  );
  return response.data.data;
}
