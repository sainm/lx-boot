import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";
import type { ReportVisualization } from "../visualizations/types";

export type ReportDetail = {
  reportId: number;
  resultId: number;
  scaleId?: number | null;
  scaleCode?: string | null;
  scaleVersionNo?: string | null;
  username?: string | null;
  displayName?: string | null;
  scaleName?: string | null;
  /** Immutable scale-specific result title from the report snapshot. */
  resultTitle?: string | null;
  /** Controlled scale presentation code; unknown/legacy values use the default layout. */
  reportTemplate?: string | null;
  createdAt?: string | null;
  reportType: string;
  totalScore: number;
  riskLevel: string;
  content: string;
  resultDescription?: string | null;
  suggestionText?: string | null;
  localeCode?: string | null;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  highRiskRuleCode?: string | null;
  calculationVersion?: number | null;
  scaleContentHash?: string | null;
  scoringEngineVersion?: string | null;
  metrics?: ReportMetric[];
  dimensionResults?: ReportDimensionResult[];
  answerDetails?: ReportAnswerDetail[];
  visualizations?: ReportVisualization[];
};

export type ReportMetric = {
  code: string;
  rawValue: number;
  displayValue: string;
  referenceText?: string | null;
  interpretationCode: string;
  reviewStatus: string;
};

export type ReportDimensionResult = {
  dimensionCode: string;
  dimensionName: string;
  score: number;
  riskLevel?: string | null;
  resultTitle?: string | null;
  referenceText?: string | null;
  reviewStatus: string;
};

export type ReportAnswerDetail = {
  questionId: number;
  questionNo: number;
  questionTitle: string;
  questionType: string;
  dimensionCode?: string | null;
  dimensionName?: string | null;
  optionCode?: string | null;
  optionLabel?: string | null;
  answerText?: string | null;
  answerValue?: number | null;
  scoreValue?: number | null;
  sliderMin?: number | null;
  sliderMax?: number | null;
  sliderStep?: number | null;
  matrixGroupCode?: string | null;
  rowCode?: string | null;
  columnCode?: string | null;
};

export type MyReportSummary = {
  reportId: number;
  resultId: number;
  taskId: number;
  taskName: string;
  scaleId: number;
  scaleName: string;
  reportType: string;
  totalScore: number;
  riskLevel: string;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  createdAt: string;
};

export type StaffReportSummary = {
  reportId: number;
  resultId: number;
  userId: number;
  username: string;
  displayName?: string | null;
  groupId?: number | null;
  groupName?: string | null;
  taskId: number;
  taskName: string;
  scaleId: number;
  scaleName: string;
  reportType: string;
  totalScore: number;
  riskLevel: string;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  createdAt: string;
};

export type ReportSearchParams = {
  userId?: number;
  groupId?: number;
  scaleId?: number;
  taskId?: number;
  page?: number;
  size?: number;
};

export type StaffReportPage = PageResponse<StaffReportSummary>;

export async function searchReports(params: ReportSearchParams) {
  const response = await http.get<ApiResponse<StaffReportPage>>("/reports", { params });
  return response.data.data;
}

export async function fetchMyReports() {
  const response = await http.get<ApiResponse<MyReportSummary[]>>("/reports/my");
  return response.data.data;
}

export async function fetchUserReports(userId: number) {
  const response = await http.get<ApiResponse<MyReportSummary[]>>(`/reports/users/${userId}`);
  return response.data.data;
}

export async function fetchReportDetail(reportId: number) {
  const response = await http.get<ApiResponse<ReportDetail>>(`/reports/${reportId}`);
  return response.data.data;
}

export async function fetchReportByResultId(resultId: number) {
  const response = await http.get<ApiResponse<ReportDetail>>(`/reports/by-result/${resultId}`);
  return response.data.data;
}

export async function regenerateReport(reportId: number) {
  const response = await http.post<ApiResponse<ReportDetail>>(`/reports/${reportId}/regenerate`);
  return response.data.data;
}
