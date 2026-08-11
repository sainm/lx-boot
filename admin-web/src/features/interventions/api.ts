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
  contactChannel?: string;
  contactOutcome?: string;
  safetyAssessmentSummary?: string;
  imminentDangerFlag?: boolean;
  responsibleHandoffSummary?: string;
  followUpDueTime?: string;
};

export type InterventionActionResult = {
  interventionId: number;
  warningId: number;
  status: string;
  retestTaskId?: number | null;
};

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
