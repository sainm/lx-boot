import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type PendingExternalRegistration = {
  id: number;
  username: string;
  display_name: string;
  email: string;
  register_source: string;
  created_at: string;
};

export async function fetchPendingExternalRegistrations() {
  const response = await http.get<ApiResponse<PendingExternalRegistration[]>>(
    "/admin/external-registrations/pending"
  );
  return response.data.data;
}

export async function approveExternalRegistration(userId: number) {
  await http.post<ApiResponse<{ message: string }>>(`/admin/external-registrations/${userId}/approve`);
}

export async function rejectExternalRegistration(userId: number) {
  await http.post<ApiResponse<{ message: string }>>(`/admin/external-registrations/${userId}/reject`);
}
