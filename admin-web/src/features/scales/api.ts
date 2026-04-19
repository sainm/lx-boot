import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type ScaleSummary = {
  id: number;
  scaleCode: string;
  scaleName: string;
  applicableTarget?: string;
  versionNo?: string;
  versionGroupId?: number;
  currentVersionFlag: boolean;
  status: string;
  scoreMethod: string;
  scoreCoefficient: number;
  anonymousSupported: boolean;
  createdAt: string;
};

export type ScaleDimension = {
  id: number;
  scaleId: number;
  dimensionCode: string;
  dimensionName: string;
  description?: string;
  sortNo: number;
};

export type ScaleQuestionOption = {
  id: number;
  questionId: number;
  optionCode: string;
  optionLabel: string;
  scoreValue: number;
  sortNo: number;
  exclusiveFlag?: boolean;
  optionGroupCode?: string | null;
};

export type ScaleQuestion = {
  id: number;
  scaleId: number;
  dimensionId?: number;
  questionNo: number;
  questionTitle: string;
  questionType: string;
  requiredFlag: boolean;
  reverseScoreFlag: boolean;
  weightValue: number;
  sortNo: number;
  optionSelectionLimit?: number | null;
  sliderMin?: number | null;
  sliderMax?: number | null;
  sliderStep?: number | null;
  textInputEnabled?: boolean;
  textInputPlaceholder?: string | null;
  matrixGroupCode?: string | null;
  rowCode?: string | null;
  columnCode?: string | null;
  options: ScaleQuestionOption[];
};

export type ScaleResultRule = {
  id: number;
  scaleId: number;
  dimensionId?: number;
  riskLevel: string;
  scoreMin: number;
  scoreMax: number;
  scoreSource?: string;
  normCode?: string | null;
  resultTitle?: string;
  resultDescription?: string;
  suggestionText?: string;
};

export type ScaleNorm = {
  id: number;
  scaleId: number;
  normCode: string;
  normName?: string | null;
  dimensionId?: number | null;
  applicableTarget?: string | null;
  ageMin?: number | null;
  ageMax?: number | null;
  gender?: string | null;
  orgType?: string | null;
  meanScore?: number | null;
  stdDeviation?: number | null;
  tScoreMean?: number | null;
  tScoreStdDeviation?: number | null;
  sortNo: number;
};

export type ScaleNormCoverageItem = {
  dimensionId?: number | null;
  dimensionCode: string;
  dimensionName: string;
  normCount: number;
  hasGlobalNorm: boolean;
  missingOverallNorm: boolean;
};

export type ScaleNormCoverage = {
  scaleId: number;
  normStrategy: string;
  defaultNormGroup?: string | null;
  totalNormCount: number;
  coveredDimensionCount: number;
  uncoveredDimensionCount: number;
  items: ScaleNormCoverageItem[];
};

export type ScaleDetail = {
  id: number;
  scaleCode: string;
  scaleName: string;
  description?: string;
  applicableTarget?: string;
  versionNo?: string;
  versionGroupId?: number;
  currentVersionFlag: boolean;
  status: string;
  scoreMethod: string;
  scoreCoefficient: number;
  normStrategy: string;
  normDefaultGroup?: string | null;
  highRiskWarningEnabled: boolean;
  anonymousSupported: boolean;
  reportTemplate?: string;
  createdAt: string;
  updatedAt: string;
  dimensions: ScaleDimension[];
  questions: ScaleQuestion[];
  resultRules: ScaleResultRule[];
  norms: ScaleNorm[];
};

export type CreateScaleRequest = {
  scaleCode: string;
  scaleName: string;
  description?: string;
  applicableTarget?: string;
  versionNo?: string;
  scoreMethod?: string;
  scoreCoefficient?: number;
  anonymousSupported?: boolean;
  reportTemplate?: string;
};

export type CreateScaleResponse = {
  id: number;
  status: string;
};

export type CreateScaleVersionRequest = {
  versionNo: string;
  scaleName?: string;
  description?: string;
};

export type CreateScaleVersionResponse = {
  id: number;
  versionGroupId: number;
  versionNo: string;
  status: string;
};

export type PublishScaleVersionResponse = {
  id: number;
  versionGroupId: number;
  versionNo?: string;
  status: string;
  currentVersionFlag: boolean;
};

export type ScaleVersionRef = {
  id: number;
  versionGroupId?: number;
  versionNo?: string;
  scaleName: string;
  status: string;
  currentVersionFlag: boolean;
};

export type ScaleVersionDiffSummary = {
  addedCount: number;
  removedCount: number;
  modifiedCount: number;
};

export type ScaleVersionDiffChange = {
  section: string;
  key: string;
  changeType: "ADDED" | "REMOVED" | "MODIFIED" | string;
  before?: Record<string, string | null | undefined>;
  after?: Record<string, string | null | undefined>;
};

export type ScaleVersionDiff = {
  from: ScaleVersionRef;
  to: ScaleVersionRef;
  summary: ScaleVersionDiffSummary;
  changes: ScaleVersionDiffChange[];
};

export type CreateDimensionItem = {
  dimensionCode: string;
  dimensionName: string;
  description?: string;
  sortNo?: number;
};

export type CreateQuestionOptionItem = {
  optionCode: string;
  optionLabel: string;
  scoreValue: number;
  sortNo?: number;
  exclusiveFlag?: boolean;
  optionGroupCode?: string | null;
};

export type CreateQuestionItem = {
  questionNo: number;
  questionTitle: string;
  questionType: string;
  dimensionId?: number;
  requiredFlag?: boolean;
  reverseScoreFlag?: boolean;
  weightValue?: number;
  sortNo?: number;
  optionSelectionLimit?: number | null;
  sliderMin?: number | null;
  sliderMax?: number | null;
  sliderStep?: number | null;
  textInputEnabled?: boolean;
  textInputPlaceholder?: string | null;
  matrixGroupCode?: string | null;
  rowCode?: string | null;
  columnCode?: string | null;
  options: CreateQuestionOptionItem[];
};

export type CreateResultRuleItem = {
  dimensionId?: number;
  riskLevel: string;
  scoreMin: number;
  scoreMax: number;
  scoreSource?: string;
  normCode?: string | null;
  resultTitle?: string;
  resultDescription?: string;
  suggestionText?: string;
};

export type CreateNormItem = {
  normCode: string;
  normName?: string;
  dimensionId?: number;
  applicableTarget?: string | null;
  ageMin?: number | null;
  ageMax?: number | null;
  gender?: string | null;
  orgType?: string | null;
  meanScore?: number | null;
  stdDeviation?: number | null;
  tScoreMean?: number | null;
  tScoreStdDeviation?: number | null;
  sortNo?: number;
};

export type BatchCreateResponse = {
  createdIds: number[];
};

export type ScaleImportSummary = {
  scaleCode: string;
  scaleName: string;
  dimensionCount: number;
  questionCount: number;
  optionCount: number;
  resultRuleCount: number;
};

export type ScaleImportIssue = {
  severity: string;
  sheetName?: string;
  rowNo?: number;
  columnName?: string;
  errorCode: string;
  message: string;
};

export type ParseScaleImportResponse = {
  importId: number;
  fileName: string;
  status: string;
  summary: ScaleImportSummary;
  errorCount: number;
  warningCount: number;
  errors: ScaleImportIssue[];
  warnings: ScaleImportIssue[];
};

export type ConfirmScaleImportResponse = {
  importId: number;
  status: string;
  scaleId: number;
  createdDimensionCount: number;
  createdQuestionCount: number;
  createdOptionCount: number;
  createdResultRuleCount: number;
};

export type ScaleImportListItem = {
  id: number;
  fileName: string;
  importMode: string;
  draftFlag: boolean;
  status: string;
  errorCount: number;
  warningCount: number;
  createdScaleId?: number;
  operatorUserId: number;
  createdAt: string;
  finishedAt?: string;
};

export type ScaleImportDetail = {
  id: number;
  fileName: string;
  importMode: string;
  draftFlag: boolean;
  status: string;
  operatorUserId: number;
  createdScaleId?: number;
  parsedAt?: string;
  confirmedAt?: string;
  finishedAt?: string;
  summary: ScaleImportSummary;
  errors: ScaleImportIssue[];
  warnings: ScaleImportIssue[];
};

export async function fetchScalePage(params: {
  scaleName?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<PageResponse<ScaleSummary>>>("/scales", {
    params
  });
  return response.data.data;
}

export async function createScale(payload: CreateScaleRequest) {
  const response = await http.post<ApiResponse<CreateScaleResponse>>("/scales", payload);
  return response.data.data;
}

export async function fetchScaleDetail(id: number) {
  const response = await http.get<ApiResponse<ScaleDetail>>(`/scales/${id}`);
  return response.data.data;
}

export async function createScaleVersion(scaleId: number, payload: CreateScaleVersionRequest) {
  const response = await http.post<ApiResponse<CreateScaleVersionResponse>>(`/scales/${scaleId}/versions`, payload);
  return response.data.data;
}

export async function publishScaleVersion(scaleId: number) {
  const response = await http.post<ApiResponse<PublishScaleVersionResponse>>(`/scales/${scaleId}/publish`);
  return response.data.data;
}

export async function fetchScaleVersionDiff(scaleId: number, targetId: number) {
  const response = await http.get<ApiResponse<ScaleVersionDiff>>(`/scales/${scaleId}/versions/${targetId}/diff`);
  return response.data.data;
}

export async function fetchScaleVersions(scaleId: number) {
  const response = await http.get<ApiResponse<ScaleSummary[]>>(`/scales/${scaleId}/versions`);
  return response.data.data;
}

export async function batchCreateDimensions(scaleId: number, dimensions: CreateDimensionItem[]) {
  const response = await http.post<ApiResponse<BatchCreateResponse>>(
    `/scales/${scaleId}/dimensions/batch`,
    { dimensions }
  );
  return response.data.data;
}

export async function batchCreateQuestions(scaleId: number, questions: CreateQuestionItem[]) {
  const response = await http.post<ApiResponse<BatchCreateResponse>>(
    `/scales/${scaleId}/questions/batch`,
    { questions }
  );
  return response.data.data;
}

export async function batchCreateResultRules(scaleId: number, resultRules: CreateResultRuleItem[]) {
  const response = await http.post<ApiResponse<BatchCreateResponse>>(
    `/scales/${scaleId}/result-rules/batch`,
    { resultRules }
  );
  return response.data.data;
}

export async function batchCreateNorms(scaleId: number, norms: CreateNormItem[]) {
  const response = await http.post<ApiResponse<BatchCreateResponse>>(
    `/scales/${scaleId}/norms/batch`,
    { norms }
  );
  return response.data.data;
}

export async function fetchScaleNormCoverage(scaleId: number) {
  const response = await http.get<ApiResponse<ScaleNormCoverage>>(`/scales/${scaleId}/norm-coverage`);
  return response.data.data;
}

export async function downloadScaleImportTemplate() {
  const response = await http.get<Blob>("/scales/import-template", {
    responseType: "blob"
  });
  return response.data;
}

export async function parseScaleImport(file: File, importMode = "CREATE_ONLY", draftFlag = true) {
  const formData = new FormData();
  formData.append("file", file);
  const response = await http.post<ApiResponse<ParseScaleImportResponse>>("/scales/imports/parse", formData, {
    params: { importMode, draftFlag },
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });
  return response.data.data;
}

export async function confirmScaleImport(importId: number, confirmRemark: string) {
  const response = await http.post<ApiResponse<ConfirmScaleImportResponse>>(`/scales/imports/${importId}/confirm`, {
    confirmRemark
  });
  return response.data.data;
}

export async function fetchScaleImportPage(params: {
  fileName?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<PageResponse<ScaleImportListItem>>>("/scales/imports", {
    params
  });
  return response.data.data;
}

export async function fetchScaleImportDetail(importId: number) {
  const response = await http.get<ApiResponse<ScaleImportDetail>>(`/scales/imports/${importId}`);
  return response.data.data;
}
