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
  assigneeUserId?: number | null;
  assigneeDisplayName?: string | null;
};

export type WarningAssigneeOption = {
  userId: number;
  username: string;
  displayName: string;
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

/** Candidate owners for the assignment picker. */
export async function fetchWarningAssigneeOptions() {
  const response = await http.get<ApiResponse<WarningAssigneeOption[]>>("/warnings/assignee-options");
  return response.data.data;
}

export type WarningPolicyResolution = {
  warningId: number;
  safetyPolicyId?: number | null;
  safetyPolicyVersion?: number | null;
  policyResolutionStatus: "RESOLVED" | "MISSING";
  deadlineTime?: string | null;
};

/**
 * Re-resolves the safety-response policy for a legacy warning that was raised
 * before an approved policy existed, so it can be closed normally.
 */
export async function resolveWarningPolicy(warningId: number) {
  const response = await http.post<ApiResponse<WarningPolicyResolution>>(
    `/warnings/${warningId}/policy-resolution`
  );
  return response.data.data;
}
