import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type SafetyResponsePolicy = {
  id: number;
  tenantId?: number | null;
  policyCode: string;
  versionNo: number;
  riskCategory: "P0" | "P1" | "P2" | "P3";
  firstResponseMinutes: number;
  escalationMinutes: number;
  followUpMinutes?: number | null;
  responsibleRole: string;
  backupRole: string;
  emergencyContactText: string;
  status: "DRAFT" | "APPROVED" | "RETIRED";
  activeFlag: boolean;
  approvedBy?: number | null;
  professionalReviewerId?: number | null;
  approvedAt?: string | null;
  createdAt: string;
};

export type CreateSafetyResponsePolicyRequest = Omit<
  SafetyResponsePolicy,
  "id" | "tenantId" | "status" | "activeFlag" | "approvedBy" | "professionalReviewerId" | "approvedAt" | "createdAt"
>;

export async function fetchSafetyResponsePolicies() {
  const response = await http.get<ApiResponse<SafetyResponsePolicy[]>>("/safety-response-policies");
  return response.data.data;
}

export async function createSafetyResponsePolicy(payload: CreateSafetyResponsePolicyRequest) {
  const response = await http.post<ApiResponse<SafetyResponsePolicy>>("/safety-response-policies", payload);
  return response.data.data;
}

export async function approveSafetyResponsePolicy(policyId: number, professionalReviewerId: number) {
  const response = await http.post<ApiResponse<SafetyResponsePolicy>>(
    `/safety-response-policies/${policyId}/approve`,
    { professionalReviewerId }
  );
  return response.data.data;
}
