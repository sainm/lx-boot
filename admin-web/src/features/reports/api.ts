import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type ReportDetail = {
  reportId: number;
  resultId: number;
  reportType: string;
  totalScore: number;
  riskLevel: string;
  content: string;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  highRiskRuleCode?: string | null;
  answerDetails?: ReportAnswerDetail[];
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

export async function fetchMyReports() {
  const response = await http.get<ApiResponse<MyReportSummary[]>>("/reports/my");
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
