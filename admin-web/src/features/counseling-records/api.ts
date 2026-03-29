import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type CreateCounselingRecordRequest = {
  appointmentId: number;
  summaryText?: string;
  suggestionText?: string;
  needRetestFlag?: boolean;
  needTransferFlag?: boolean;
};

export type CreateCounselingRecordResult = {
  id: number;
  appointmentId: number;
};

export async function createCounselingRecord(payload: CreateCounselingRecordRequest) {
  const response = await http.post<ApiResponse<CreateCounselingRecordResult>>("/counseling-records", payload);
  return response.data.data;
}
