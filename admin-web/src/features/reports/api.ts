import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type ReportDetail = {
  reportId: number;
  resultId: number;
  reportType: string;
  totalScore: number;
  riskLevel: string;
  content: string;
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
