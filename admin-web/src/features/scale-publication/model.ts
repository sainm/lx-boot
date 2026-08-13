import type { CreateScaleGoldenCaseRequest, GoldenCaseRunResponse, ScaleGoldenCaseRun } from "./api";

export type GoldenCaseDraft = {
  caseCode: string;
  caseType: CreateScaleGoldenCaseRequest["caseType"];
  sourceReference: string;
  answers?: Array<{ questionNo: number; optionCodes?: string; answerValue?: number | null; answerText?: string | null }>;
  durationSeconds?: number | null;
  age?: number | null;
  gender?: string | null;
  orgType?: string | null;
  applicableTarget?: string | null;
  preferredNormCode?: string | null;
  valid: boolean;
  errorCode?: string | null;
  totalScore?: number | null;
  riskLevel?: string | null;
  highRiskTriggered?: boolean | null;
  highRiskRuleCode?: string | null;
  normCode?: string | null;
  dimensionsJson: string;
  metricsJson?: string;
};

function parseMetricJson(value: string | undefined): Record<string, number> {
  const parsed: unknown = JSON.parse(value || "{}");
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("INVALID_METRICS_JSON");
  for (const item of Object.values(parsed as Record<string, unknown>)) {
    if (typeof item !== "number" || !Number.isFinite(item)) throw new Error("INVALID_METRICS_JSON");
  }
  return parsed as Record<string, number>;
}

export function buildGoldenCaseRequest(values: GoldenCaseDraft): CreateScaleGoldenCaseRequest {
  const normValues = {
    age: values.age,
    gender: values.gender?.trim() || null,
    orgType: values.orgType?.trim() || null,
    applicableTarget: values.applicableTarget?.trim() || null,
    preferredNormCode: values.preferredNormCode?.trim() || null
  };
  const hasNorm = Object.values(normValues).some((value) => value !== undefined && value !== null && value !== "");
  return {
    caseCode: values.caseCode.trim(),
    caseType: values.caseType,
    sourceReference: values.sourceReference.trim(),
    input: {
      answers: (values.answers ?? []).map((answer) => ({
        questionNo: answer.questionNo,
        optionCodes: (answer.optionCodes ?? "").split(",").map((item) => item.trim()).filter(Boolean),
        answerValue: answer.answerValue,
        answerText: answer.answerText?.trim() || null
      })),
      durationSeconds: values.durationSeconds,
      norm: hasNorm ? normValues : null
    },
    expected: {
      valid: values.valid,
      errorCode: values.errorCode?.trim() || null,
      totalScore: values.totalScore,
      riskLevel: values.riskLevel?.trim() || null,
      highRiskTriggered: values.highRiskTriggered,
      highRiskRuleCode: values.highRiskRuleCode?.trim() || null,
      normCode: values.normCode?.trim() || null,
      dimensions: JSON.parse(values.dimensionsJson || "{}") as CreateScaleGoldenCaseRequest["expected"]["dimensions"],
      metrics: parseMetricJson(values.metricsJson)
    }
  };
}

export function buildHistoricRunEvidence(run: ScaleGoldenCaseRun): GoldenCaseRunResponse {
  const actual = JSON.parse(run.actualJson) as unknown;
  const differences = JSON.parse(run.differencesJson) as unknown;
  if (!actual || typeof actual !== "object" || Array.isArray(actual) || !Array.isArray(differences) ||
      differences.some((difference) => typeof difference !== "string")) {
    throw new Error("INVALID_GOLDEN_CASE_RUN_EVIDENCE");
  }
  return {
    runId: run.id,
    goldenCaseId: run.goldenCaseId,
    passed: run.passed,
    actual: actual as Record<string, unknown>,
    differences
  };
}

export function formatPublicationBlocker(
  blocker: string,
  translate: (key: string, params?: Record<string, string | number>) => string
) {
  const [code, ...details] = blocker.split(":");
  const detail = details.join(":");
  switch (code) {
    case "GOLDEN_CASE_STALE":
      return translate("scalePublication.blocker.goldenCaseStale", { caseCode: detail });
    case "REVIEW_PROFESSIONAL_MISSING":
      return translate("scalePublication.blocker.professionalReviewMissing");
    case "REVIEW_BUSINESS_MISSING":
      return translate("scalePublication.blocker.businessReviewMissing");
    case "REVIEW_PROFESSIONAL_QUALIFICATION_MISSING":
      return translate("scalePublication.blocker.professionalQualificationMissing");
    case "REVIEW_PROFESSIONAL_EVIDENCE_MISSING":
      return translate("scalePublication.blocker.professionalEvidenceMissing");
    case "REVIEW_PROFESSIONAL_SCOPE_MISSING":
      return translate("scalePublication.blocker.professionalScopeMissing");
    case "REVIEW_BUSINESS_EVIDENCE_MISSING":
      return translate("scalePublication.blocker.businessEvidenceMissing");
    case "REVIEW_BUSINESS_SCOPE_MISSING":
      return translate("scalePublication.blocker.businessScopeMissing");
    default:
      return blocker;
  }
}
