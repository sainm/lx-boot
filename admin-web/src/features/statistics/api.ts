import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";
import type { ReportVisualization } from "../visualizations/types";

export type DashboardMetricCard = {
  key: string;
  label: string;
  value: number;
  suffix?: string | null;
  description?: string | null;
};

export type DashboardTrendPoint = {
  day: string;
  count: number;
};

export type KeyValueCount = {
  key: string;
  value: number;
};

export type DashboardRecentWarningItem = {
  warningId: number;
  resultId: number;
  taskName: string;
  scaleName: string;
  warningLevel: string;
  warningPriority: string;
  status: string;
  totalScore: number;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  highRiskRuleCode?: string | null;
  createdAt: string;
};

export type DashboardRecentReportItem = {
  reportId: number;
  resultId: number;
  taskName: string;
  scaleName: string;
  reportType: string;
  riskLevel: string;
  totalScore: number;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  highRiskRuleCode?: string | null;
  createdAt: string;
};

export type DashboardStatistics = {
  generatedAt: string;
  overviewCards: DashboardMetricCard[];
  taskStatusDistribution: KeyValueCount[];
  riskDistribution: KeyValueCount[];
  submissionTrend: DashboardTrendPoint[];
  warningTrend: DashboardTrendPoint[];
  recentWarnings: DashboardRecentWarningItem[];
  recentReports: DashboardRecentReportItem[];
};

export type GroupDimensionStat = {
  dimensionId?: number | null;
  dimensionName: string;
  averageScore: number;
  answerCount: number;
  standardDeviation?: number | null;
  maxScore?: number | null;
  minScore?: number | null;
  exceedCount?: number | null;
};

export type GroupUserComparison = {
  userId: number;
  displayName?: string | null;
  totalScore: number;
  riskLevel: string;
  scoreSource?: string;
  standardScore?: number | null;
  zScore?: number | null;
  tScore?: number | null;
  normCode?: string | null;
  highRiskFlag?: boolean;
  highRiskRuleCode?: string | null;
  scoreGapToAverage?: number | null;
};

export type GroupReportSummary = {
  taskId: number;
  taskName: string;
  scaleId: number;
  scaleName: string;
  taskStartTime?: string | null;
  taskEndTime?: string | null;
  groupId: number;
  groupName: string;
  memberCount: number;
  submittedCount: number;
  completionRate: number;
  averageScore?: number | null;
  highRiskCount: number;
  warningCount: number;
  riskDistribution: KeyValueCount[];
  latestSubmittedAt?: string | null;
  compareUserResult?: GroupUserComparison | null;
  dimensionStats: GroupDimensionStat[];
  visualizations?: ReportVisualization[];
};

export type GroupReportPage = PageResponse<GroupReportSummary>;

export async function fetchDashboardStatistics() {
  const response = await http.get<ApiResponse<DashboardStatistics>>("/statistics/dashboard");
  return response.data.data;
}

export async function fetchGroupReports(params: {
  taskId?: number;
  groupId?: number;
  scaleId?: number;
  compareUserId?: number;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<GroupReportPage>>("/statistics/group-reports", {
    params
  });
  return response.data.data;
}

export type GroupReportExportFormat = "PDF" | "WORD";

export async function downloadGroupReportsFile(params: {
  taskId?: number;
  groupId?: number;
  scaleId?: number;
  compareUserId?: number;
  page?: number;
  size?: number;
  format?: GroupReportExportFormat;
}) {
  const response = await http.get<Blob>("/statistics/group-reports/download", {
    params: {
      ...params,
      format: params.format ?? "PDF",
      exportFormat: params.format ?? "PDF",
      page: params.page ?? 1,
      size: params.size ?? 200
    },
    responseType: "blob"
  });
  const headers = normalizeHeaders(response.headers);
  const fallbackExtension = params.format === "WORD" ? "docx" : "pdf";
  const fileName = sanitizeFileName(resolveContentDispositionFileName(headers["content-disposition"]) || `SCL-90-group-screening-report.${fallbackExtension}`);
  return {
    fileName,
    blob: response.data,
    contentType: headers["content-type"] || (params.format === "WORD" ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/pdf")
  };
}

export async function downloadGroupReportsPdf(params: Omit<Parameters<typeof downloadGroupReportsFile>[0], "format">) {
  return downloadGroupReportsFile({ ...params, format: "PDF" });
}

export function downloadBlobFile(blob: Blob, fileName: string, contentType?: string) {
  const typedBlob = new Blob([blob], { type: contentType || blob.type || "application/octet-stream" });
  const url = window.URL.createObjectURL(typedBlob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName || "download";
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

function normalizeHeaders(headers: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(headers).map(([key, value]) => [
      key.toLowerCase(),
      Array.isArray(value) ? String(value[0] ?? "") : value == null ? "" : String(value)
    ])
  ) as Record<string, string>;
}

function sanitizeFileName(value: string) {
  return value.replace(/^"/, "").replace(/"$/, "");
}

function resolveContentDispositionFileName(value?: string) {
  if (!value) return "";
  const encoded = value.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) return decodeURIComponent(encoded);
  return value.match(/filename="?([^";]+)"?/i)?.[1] ?? "";
}
