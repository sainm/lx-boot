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

export type LoginActivity = {
  id: number;
  userId?: number | null;
  principal?: string | null;
  loginType: string;
  result: string;
  ip?: string | null;
  userAgent?: string | null;
  location?: string | null;
  reason?: string | null;
  createdAt: string;
};

export type SecurityEvent = {
  id: number;
  eventType: string;
  userId?: number | null;
  tenantId?: number | null;
  detail: Record<string, unknown>;
  ip?: string | null;
  createdAt: string;
};

export async function fetchMyProfile() {
  const response = await http.get<ApiResponse<AuthProfile>>("/auth/me");
  return response.data.data;
}

export async function fetchMyLoginActivities() {
  const response = await http.get<ApiResponse<LoginActivity[]>>("/auth/me/login-activities");
  return response.data.data;
}

export async function fetchMySecurityEvents() {
  const response = await http.get<ApiResponse<SecurityEvent[]>>("/auth/me/security-events");
  return response.data.data;
}
