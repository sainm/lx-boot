import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

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

export async function downloadGroupReportsPdf(params: {
  taskId?: number;
  groupId?: number;
  scaleId?: number;
  compareUserId?: number;
  page?: number;
  size?: number;
}) {
  const response = await http.get<Blob>("/statistics/group-reports/download", {
    params: {
      ...params,
      page: params.page ?? 1,
      size: params.size ?? 200
    },
    responseType: "blob"
  });
  const headers = normalizeHeaders(response.headers);
  const fileName = sanitizeFileName(
    headers["content-disposition"]?.match(/filename="?([^";]+)"?/i)?.[1] || "psy-group-report.pdf"
  );
  return {
    fileName,
    blob: response.data,
    contentType: headers["content-type"] || "application/pdf"
  };
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
