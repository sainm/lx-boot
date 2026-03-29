import axios from "axios";
import { readAuthToken } from "../../auth/token";
import type { ApiResponse } from "../../types/api";

const authAuditHttp = axios.create({
  timeout: 10000
});

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
