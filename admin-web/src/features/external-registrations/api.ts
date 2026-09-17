import { http } from "../../services/http";

export type PendingExternalRegistration = {
  id: number;
  username: string;
  display_name: string;
  email: string;
  register_source: string;
  created_at: string;
};

export async function fetchPendingExternalRegistrations() {
  const response = await http.get<PendingExternalRegistration[]>("/admin/external-registrations/pending");
  return response.data;
}

export async function approveExternalRegistration(userId: number) {
  await http.post(`/admin/external-registrations/${userId}/approve`);
}

export async function rejectExternalRegistration(userId: number) {
  await http.post(`/admin/external-registrations/${userId}/reject`);
}
