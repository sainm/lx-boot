import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type ReviewStatus = "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "REJECTED" | string;

export type ScalePackageGovernance = {
  sourceTitle?: string | null;
  publisherName?: string | null;
  manualVersion?: string | null;
  citationText?: string | null;
  sourceUrl?: string | null;
  copyrightStatus: string;
  rightsHolder?: string | null;
  authorizationStatus: string;
  authorizationType?: string | null;
  authorizationScope?: string | null;
  authorizedTerritories?: string | null;
  authorizedLanguages?: string | null;
  authorizationValidFrom?: string | null;
  authorizationValidTo?: string | null;
  targetPopulation?: string | null;
  exclusionCriteria?: string | null;
  estimatedMinutes?: number | null;
  resultVisibility?: string | null;
  dataUsageStatement?: string | null;
  nonDiagnosticStatement?: string | null;
  helpResourceText?: string | null;
  governanceStatus: string;
};

export type ScalePackageTranslation = {
  localeCode: string;
  scaleName: string;
  description?: string | null;
  instructionText?: string | null;
  purposeText?: string | null;
  dataUsageText?: string | null;
  resultVisibilityText?: string | null;
  nonDiagnosticText?: string | null;
  highRiskActionText?: string | null;
  helpResourceText?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageDimensionTranslation = {
  dimensionId: number;
  localeCode: string;
  dimensionName: string;
  description?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageQuestionTranslation = {
  questionId: number;
  localeCode: string;
  questionTitle: string;
  textInputPlaceholder?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageOptionTranslation = {
  optionId: number;
  localeCode: string;
  optionLabel: string;
  reviewStatus: ReviewStatus;
};

export type ScalePackageResultRuleTranslation = {
  resultRuleId: number;
  localeCode: string;
  resultTitle: string;
  resultDescription?: string | null;
  suggestionText?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageHighRiskRuleTranslation = {
  highRiskRuleId: number;
  localeCode: string;
  resultTitle: string;
  resultDescription?: string | null;
  suggestionText?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageQualityPolicy = {
  missingAnswerPolicy: string;
  maxMissingRatio: number;
  minimumDurationSeconds?: number | null;
  maximumDurationSeconds?: number | null;
  invalidResultAction: string;
  requireAllRequiredAnswers: boolean;
};

export type ScalePackageValidityRule = {
  ruleCode: string;
  ruleType: string;
  ruleVersion: string;
  configJson: string;
  reviewStatus: ReviewStatus;
  enabled: boolean;
  sortNo: number;
};

export type ScalePackageAlgorithmBinding = {
  algorithmCode: string;
  algorithmVersion: string;
  implementationType: string;
  inputSchemaJson: string;
  outputSchemaJson: string;
  implementationChecksum?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageNormGovernance = {
  normId: number;
  sourceReference?: string | null;
  normVersion?: string | null;
  sampleSize?: number | null;
  regionCode?: string | null;
  languageCode?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
  reviewStatus: ReviewStatus;
};

export type ScalePackageSnapshot = {
  scaleId: number;
  governance?: ScalePackageGovernance | null;
  translations: ScalePackageTranslation[];
  dimensionTranslations: ScalePackageDimensionTranslation[];
  questionTranslations: ScalePackageQuestionTranslation[];
  optionTranslations: ScalePackageOptionTranslation[];
  resultRuleTranslations: ScalePackageResultRuleTranslation[];
  highRiskRuleTranslations: ScalePackageHighRiskRuleTranslation[];
  qualityPolicy?: ScalePackageQualityPolicy | null;
  validityRules: ScalePackageValidityRule[];
  algorithmBinding?: ScalePackageAlgorithmBinding | null;
  normGovernance: ScalePackageNormGovernance[];
};

export type UpdateScalePackageRequest = Omit<ScalePackageSnapshot, "scaleId">;

export type DownloadedScalePackageExport = {
  blob: Blob;
  fileName: string;
  contentType: string;
};

export async function fetchScalePackage(scaleId: number) {
  const response = await http.get<ApiResponse<ScalePackageSnapshot>>(`/scales/${scaleId}/package`);
  return response.data.data;
}

export async function replaceScalePackage(scaleId: number, payload: UpdateScalePackageRequest) {
  const response = await http.put<ApiResponse<ScalePackageSnapshot>>(`/scales/${scaleId}/package`, payload);
  return response.data.data;
}

export async function downloadScalePackageExport(scaleId: number): Promise<DownloadedScalePackageExport> {
  const response = await http.get<Blob>(`/scales/${scaleId}/package/export`, { responseType: "blob" });
  return {
    blob: response.data,
    fileName: safeDownloadFileName(response.headers["x-export-file-name"], `scale-${scaleId}-package-v2.json`),
    contentType: headerString(response.headers["content-type"]) ?? "application/vnd.psy-scale-package+json"
  };
}

export function saveScalePackageExport(artifact: DownloadedScalePackageExport) {
  const objectUrl = URL.createObjectURL(artifact.blob);
  const anchor = document.createElement("a");
  try {
    anchor.href = objectUrl;
    anchor.download = safeDownloadFileName(artifact.fileName, "scale-package-v2.json");
    anchor.style.display = "none";
    document.body.appendChild(anchor);
    anchor.click();
  } finally {
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  }
}

function safeDownloadFileName(value: unknown, fallback: string): string {
  if (typeof value !== "string") return fallback;
  const safe = value.replace(/[\\/\u0000-\u001f\u007f]/g, "_").trim().slice(0, 180);
  return safe || fallback;
}

function headerString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value : undefined;
}
