import { http } from "../services/http";
import type { ApiResponse } from "../types/api";
import type { AppRole } from "./roles";

export type AuthProfile = {
  userId: number;
  username: string;
  displayName?: string | null;
  roles: AppRole[];
  permissions: string[];
};

export async function fetchMyProfile() {
  const response = await http.get<ApiResponse<AuthProfile>>("/auth/me");
  return response.data.data;
}
