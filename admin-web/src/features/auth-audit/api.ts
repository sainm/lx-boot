import { authHttp } from "../../auth/api";
import { readAuthToken } from "../../auth/token";
import type { ApiResponse } from "../../types/api";

const authAuditHttp = authHttp;

/*
const legacyAuthAuditHttp = axios.create({
  timeout: 10000
});
*/

authAuditHttp.interceptors.request.use((config) => {
  const token = readAuthToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export type PagedSlice<T> = {
  items: T[];
  page: number;
  size: number;
  hasNext: boolean;
};

export type LoginLogRecord = {
  id: number;
  userId: number | null;
  principal: string | null;
  loginType: string;
  result: string;
  ip?: string | null;
  userAgent?: string | null;
  reason: string | null;
  createdAt: string;
};

export type SecurityEventDetail = Record<string, unknown>;

export type SecurityEventRecord = {
  id: number;
  eventType: string;
  userId: number | null;
  tenantId: number | null;
  detailJson: string | null;
  parsedDetail: SecurityEventDetail | null;
  ip: string | null;
  createdAt: string;
};

export type UserSessionRecord = {
  sessionId: string;
  userId: number;
  username: string;
  tenantId: number | null;
  clientId: string | null;
  deviceType: string | null;
  deviceName: string | null;
  userAgent: string | null;
  ip: string | null;
  status: string;
  current: boolean;
  lastSeenAt: string | null;
  accessExpireAt: string | null;
  refreshExpireAt: string | null;
  createdAt: string;
  updatedAt: string;
  revokedAt: string | null;
  revokeReason: string | null;
};

export type UserDeviceRecord = {
  id: number;
  deviceType: string;
  deviceId: string;
  pushTokenMasked?: string | null;
  appVersion?: string | null;
  activeFlag: boolean;
  authSessionId?: string | null;
  authSessionStatus?: string | null;
  authSessionLastSeenAt?: string | null;
  deviceTrustLevel: string;
  riskSignals: string[];
  riskLevel: string;
  autoDisposition: string;
  autoDispositionReason?: string | null;
  lastActiveAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UserDeviceDeactivationResult = {
  device: UserDeviceRecord;
  revokedSessionCount: number;
};

function toPagedSlice<T>(items: T[] | undefined, page: number, size: number): PagedSlice<T> {
  const safeItems = items ?? [];
  return {
    items: safeItems,
    page,
    size,
    hasNext: safeItems.length === size
  };
}

function parseSecurityEventDetail(detailJson: string | null): SecurityEventDetail | null {
  if (!detailJson) {
    return null;
  }
  try {
    const parsed = JSON.parse(detailJson) as unknown;
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as SecurityEventDetail;
    }
    return null;
  } catch {
    return null;
  }
}

function normalizeSecurityEventRecord(record: Omit<SecurityEventRecord, "parsedDetail">): SecurityEventRecord {
  return {
    ...record,
    parsedDetail: parseSecurityEventDetail(record.detailJson)
  };
}

export async function fetchLoginLogs(params: {
  page?: number;
  size?: number;
  principal?: string;
  result?: string;
}) {
  const page = params.page ?? 1;
  const size = params.size ?? 20;
  const response = await authAuditHttp.get<ApiResponse<LoginLogRecord[]>>("/auth/login-logs", { params });
  return toPagedSlice(response.data.data, page, size);
}

export async function fetchSecurityEvents(params: {
  page?: number;
  size?: number;
  eventType?: string;
  userId?: string;
}) {
  const page = params.page ?? 1;
  const size = params.size ?? 20;
  const response = await authAuditHttp.get<ApiResponse<Array<Omit<SecurityEventRecord, "parsedDetail">>>>(
    "/auth/security-events",
    { params }
  );
  const normalizedItems = (response.data.data ?? []).map(normalizeSecurityEventRecord);
  return toPagedSlice(normalizedItems, page, size);
}

export async function fetchUserSessions(userId: string | number) {
  const response = await authAuditHttp.get<ApiResponse<UserSessionRecord[]>>(`/auth/users/${userId}/sessions`);
  return response.data.data ?? [];
}

export async function fetchUserDevices(userId: string | number) {
  const response = await authHttp.get<ApiResponse<UserDeviceRecord[]>>(`/auth/users/${userId}/devices`);
  return response.data.data ?? [];
}

export async function deactivateUserDevice(userId: string | number, deviceId: string) {
  const response = await authHttp.post<ApiResponse<UserDeviceDeactivationResult>>(
    `/auth/users/${userId}/devices/${encodeURIComponent(deviceId)}/deactivate`
  );
  return response.data.data;
}

export async function revokeUserSession(userId: string | number, sessionId: string) {
  const response = await authAuditHttp.post<ApiResponse<boolean>>(`/auth/users/${userId}/sessions/${sessionId}/revoke`);
  return response.data.data ?? false;
}

export async function revokeAllUserSessions(userId: string | number) {
  const response = await authAuditHttp.post<ApiResponse<{ revokedCount: number }>>(`/auth/users/${userId}/sessions/revoke-all`);
  return response.data.data?.revokedCount ?? 0;
}
