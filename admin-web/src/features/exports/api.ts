import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type ExportFormat = "TEXT" | "PDF";

export type ExportTarget = {
  reportId?: number;
  resultId?: number;
};

export type ExportReportRequest = {
  reportId?: number;
  resultId?: number;
  exportFormat?: ExportFormat;
};

export type ExportReportResponse = {
  exportId: string;
  fileName: string;
  exportFormat: string;
  downloadExtension: string;
  contentType: string;
  contentEncoding: string;
  generatedAt: string;
  reportId: number;
  resultId: number;
  content: string;
};

export type DownloadedExportReport = {
  exportId: string;
  fileName: string;
  exportFormat: string;
  downloadExtension: string;
  contentType: string;
  generatedAt: string;
  reportId: number;
  resultId: number;
  blob: Blob;
};

export async function exportReport(request: ExportReportRequest) {
  const response = await http.post<ApiResponse<ExportReportResponse>>("/exports/reports", {
    ...request,
    exportFormat: request.exportFormat ?? "TEXT"
  });
  return response.data.data;
}

export async function downloadExportReport(request: ExportReportRequest) {
  const response = await http.get<Blob>("/exports/reports/download", {
    params: {
      reportId: request.reportId,
      resultId: request.resultId,
      exportFormat: request.exportFormat ?? "TEXT"
    },
    responseType: "blob"
  });
  const headers = normalizeHeaders(response.headers);
  return {
    exportId: headers["x-export-id"] || "",
    fileName: sanitizeFileName(
      headers["content-disposition"]?.match(/filename="?([^";]+)"?/i)?.[1] || ""
    ),
    exportFormat: headers["x-export-format"] || (request.exportFormat ?? "TEXT"),
    downloadExtension: headers["x-download-extension"] || (request.exportFormat === "PDF" ? "pdf" : "txt"),
    contentType: headers["content-type"] || "application/octet-stream",
    generatedAt: headers["x-generated-at"] || "",
    reportId: Number(headers["x-report-id"] || request.reportId || 0),
    resultId: Number(headers["x-result-id"] || request.resultId || 0),
    blob: response.data
  } satisfies DownloadedExportReport;
}

export type ExportJobStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED";

export type ExportJobSubmitResponse = {
  jobId: string;
  status: ExportJobStatus;
};

export type ExportJobStatusResponse = {
  jobId: string;
  status: ExportJobStatus;
  reportId?: number | null;
  resultId?: number | null;
  exportFormat?: string | null;
  localeTag?: string | null;
  fileName: string | null;
  contentType: string | null;
  error: string | null;
  createdAt: string;
  completedAt: string | null;
};

export async function submitExportJob(request: ExportReportRequest) {
  const response = await http.post<ApiResponse<ExportJobSubmitResponse>>("/exports/reports/jobs", {
    ...request,
    exportFormat: request.exportFormat ?? "TEXT"
  });
  return response.data.data;
}

export async function pollExportJobStatus(jobId: string) {
  const response = await http.get<ApiResponse<ExportJobStatusResponse>>(`/exports/reports/jobs/${jobId}`);
  return response.data.data;
}

export async function retryExportJob(jobId: string) {
  const response = await http.post<ApiResponse<ExportJobSubmitResponse>>(`/exports/reports/jobs/${jobId}/retry`);
  return response.data.data;
}

export async function downloadExportJobFile(jobId: string, fileName: string, contentType: string) {
  const response = await http.get<Blob>(`/exports/reports/jobs/${jobId}/download`, {
    responseType: "blob"
  });
  const blob = new Blob([response.data], { type: contentType || "application/octet-stream" });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName || "export";
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

export function getExportFileExtension(format: ExportFormat) {
  return format === "PDF" ? "pdf" : "txt";
}

export function buildExportFileName(
  exportResult: Pick<
    ExportReportResponse | DownloadedExportReport,
    "fileName" | "reportId" | "resultId" | "exportFormat" | "downloadExtension"
  >
) {
  const extension =
    exportResult.downloadExtension?.trim() ||
    getExportFileExtension(exportResult.exportFormat as ExportFormat);
  const baseName =
    exportResult.fileName?.trim() ||
    `psy-report-${exportResult.reportId || exportResult.resultId || "export"}`;
  if (baseName.toLowerCase().endsWith(`.${extension}`)) {
    return baseName;
  }
  return `${baseName}.${extension}`;
}

export function downloadExportFile(exportResult: DownloadedExportReport | ExportReportResponse) {
  const fileName = buildExportFileName(exportResult);
  const blob =
    "blob" in exportResult
      ? exportResult.blob
      : exportResult.contentEncoding === "BASE64"
        ? new Blob([decodeBase64(exportResult.content)], {
            type: exportResult.contentType || "application/octet-stream"
          })
        : new Blob([exportResult.content], {
            type: exportResult.contentType || "application/octet-stream"
          });

  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

function decodeBase64(value: string) {
  const binary = window.atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
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
