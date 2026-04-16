import { authHttp } from "./api";
import type { ApiResponse } from "../types/api";
import type { AppRole } from "./roles";

export type AuthProfile = {
  userId: number;
  username: string;
  displayName?: string | null;
  sessionId?: string | null;
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

export type UserSession = {
  sessionId: string;
  userId: number;
  username: string;
  tenantId?: number | null;
  clientId?: string | null;
  deviceType?: string | null;
  deviceName?: string | null;
  userAgent?: string | null;
  ip?: string | null;
  status: string;
  current: boolean;
  lastSeenAt?: string | null;
  accessExpireAt?: string | null;
  refreshExpireAt?: string | null;
  createdAt: string;
  updatedAt: string;
  revokedAt?: string | null;
  revokeReason?: string | null;
};

export type SessionPolicy = {
  policy: "SINGLE_DEVICE" | "MULTI_DEVICE";
};

export async function fetchMyProfile() {
  const response = await authHttp.get<ApiResponse<AuthProfile>>("/auth/me");
  return response.data.data;
}

export async function fetchMyLoginActivities() {
  const response = await authHttp.get<ApiResponse<LoginActivity[]>>("/auth/me/login-activities");
  return response.data.data;
}

export async function fetchMySecurityEvents() {
  const response = await authHttp.get<ApiResponse<SecurityEvent[]>>("/auth/me/security-events");
  return response.data.data;
}

export async function fetchMySessions() {
  const response = await authHttp.get<ApiResponse<UserSession[]>>("/auth/me/sessions");
  return response.data.data;
}

export async function revokeMySession(sessionId: string) {
  const response = await authHttp.post<ApiResponse<boolean>>(`/auth/me/sessions/${sessionId}/revoke`);
  return response.data.data;
}

export async function revokeOtherMySessions() {
  const response = await authHttp.post<ApiResponse<{ revokedCount: number }>>("/auth/me/sessions/revoke-others");
  return response.data.data;
}

export async function fetchMySessionPolicy() {
  const response = await authHttp.get<ApiResponse<SessionPolicy>>("/auth/me/session-policy");
  return response.data.data;
}

export async function updateMySessionPolicy(policy: SessionPolicy["policy"]) {
  const response = await authHttp.post<ApiResponse<SessionPolicy>>("/auth/me/session-policy", { policy });
  return response.data.data;
}
