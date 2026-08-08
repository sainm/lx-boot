import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type CreateInterventionRequest = {
  warningId: number;
  counselorUserId?: number;
  planText: string;
};

export type CloseInterventionRequest = {
  closeSummary: string;
  needRetest?: boolean;
};

export type InterventionActionResult = {
  interventionId: number;
  warningId: number;
  status: string;
  retestTaskId?: number | null;
};

export type InterventionDetail = {
  id: number;
  warningId: number;
  counselorUserId?: number | null;
  currentStatus: string;
  planText?: string | null;
  closeSummary?: string | null;
  needRetestFlag: boolean;
  retestTaskId?: number | null;
};

export async function fetchInterventionByWarning(warningId: number) {
  const response = await http.get<ApiResponse<InterventionDetail | null>>(`/interventions/by-warning/${warningId}`);
  return response.data.data;
}

export async function createIntervention(payload: CreateInterventionRequest) {
  const response = await http.post<ApiResponse<InterventionActionResult>>("/interventions", payload);
  return response.data.data;
}

export async function closeIntervention(interventionId: number, payload: CloseInterventionRequest) {
  const response = await http.post<ApiResponse<InterventionActionResult>>(
    `/interventions/${interventionId}/close`,
    payload
  );
  return response.data.data;
}
