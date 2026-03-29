import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type ScaleSummary = {
  id: number;
  scaleCode: string;
  scaleName: string;
  applicableTarget?: string;
  versionNo?: string;
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
  options: ScaleQuestionOption[];
};

export type ScaleResultRule = {
  id: number;
  scaleId: number;
  dimensionId?: number;
  riskLevel: string;
  scoreMin: number;
  scoreMax: number;
  resultTitle?: string;
  resultDescription?: string;
  suggestionText?: string;
};

export type ScaleDetail = {
  id: number;
  scaleCode: string;
  scaleName: string;
  description?: string;
  applicableTarget?: string;
  versionNo?: string;
  status: string;
  scoreMethod: string;
  scoreCoefficient: number;
  anonymousSupported: boolean;
  reportTemplate?: string;
  createdAt: string;
  updatedAt: string;
  dimensions: ScaleDimension[];
  questions: ScaleQuestion[];
  resultRules: ScaleResultRule[];
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
  options: CreateQuestionOptionItem[];
};

export type CreateResultRuleItem = {
  dimensionId?: number;
  riskLevel: string;
  scoreMin: number;
  scoreMax: number;
  resultTitle?: string;
  resultDescription?: string;
  suggestionText?: string;
};

export type BatchCreateResponse = {
  createdIds: number[];
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

