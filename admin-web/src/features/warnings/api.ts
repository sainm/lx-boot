import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type WarningSummary = {
  id: number;
  resultId: number;
  warningLevel: string;
  warningPriority: string;
  warningReason?: string;
  status: string;
  createdAt: string;
  deadlineTime?: string | null;
  firstResponseTime?: string | null;
  safetyPolicyId?: number | null;
  safetyPolicyVersion?: number | null;
  policyResolutionStatus: "RESOLVED" | "MISSING";
};

export type WarningActionResult = {
  warningId: number;
  status: string;
  assigneeUserId?: number;
};

export async function fetchWarningPage(params: {
  status?: string;
  warningLevel?: string;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<PageResponse<WarningSummary>>>("/warnings", {
    params
  });
  return response.data.data;
}

export async function claimWarning(warningId: number) {
  const response = await http.post<ApiResponse<WarningActionResult>>(`/warnings/${warningId}/claim`);
  return response.data.data;
}

export async function assignWarning(warningId: number, assigneeUserId: number) {
  const response = await http.post<ApiResponse<WarningActionResult>>(`/warnings/${warningId}/assign`, {
    assigneeUserId
  });
  return response.data.data;
}
