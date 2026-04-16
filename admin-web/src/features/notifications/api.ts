import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type MyNotification = {
  id: number;
  notificationType: string;
  title: string;
  content: string;
  bizType?: string;
  bizId?: number;
  targetPath?: string;
  readFlag: boolean;
  readTime?: string;
  createdAt: string;
};

export type NotificationActionResult = {
  notificationId: number;
  readFlag: boolean;
};

export type UserDeviceSummary = {
  id: number;
  deviceType: string;
  deviceId: string;
  pushTokenMasked?: string | null;
  appVersion?: string | null;
  activeFlag: boolean;
  lastActiveAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type RegisterDeviceRequest = {
  deviceType: string;
  deviceId: string;
  pushToken?: string;
  appVersion?: string;
};

export type NotificationDeliverySummary = {
  id: number;
  notificationId: number;
  receiverUserId: number;
  deliveryChannel: string;
  deliveryStatus: string;
  readFlag: boolean;
  readTime?: string | null;
  deviceId?: number | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type NotificationDeliveryRetryResult = {
  notificationId: number;
  deliveryChannel?: string | null;
  retriedCount: number;
};

export type NotificationDeliveryOpsBucket = {
  deliveryChannel: string;
  deliveryStatus: string;
  count: number;
};

export type NotificationDeliveryOpsSummary = {
  totalPending: number;
  totalProcessing: number;
  totalFailed: number;
  oldestPendingCreatedAt?: string | null;
  buckets: NotificationDeliveryOpsBucket[];
};

export type NotificationPolicy = {
  id: number;
  notificationType: string;
  inAppEnabled: boolean;
  pushEnabled: boolean;
  cooldownMinutes: number;
};

export type AdminNotificationOpsItem = {
  id: number;
  notificationType: string;
  title: string;
  bizType?: string | null;
  bizId?: number | null;
  targetPath?: string | null;
  createdAt: string;
  totalDeliveries: number;
  pendingDeliveries: number;
  processingDeliveries: number;
  failedDeliveries: number;
  sentDeliveries: number;
  latestErrorMessage?: string | null;
};

export type BatchRetryNotificationDeliveriesRequest = {
  notificationIds: number[];
  deliveryChannel?: string;
};

export type NotificationBatchRetryResult = {
  notificationIds: number[];
  deliveryChannel?: string | null;
  retriedCount: number;
};

export type UpdateNotificationPolicyRequest = {
  notificationType: string;
  inAppEnabled: boolean;
  pushEnabled: boolean;
  cooldownMinutes: number;
};

export async function fetchMyNotifications() {
  const response = await http.get<ApiResponse<MyNotification[]>>("/my/notifications");
  return response.data.data;
}

export async function markNotificationRead(notificationId: number) {
  const response = await http.post<ApiResponse<NotificationActionResult>>(`/my/notifications/${notificationId}/read`);
  return response.data.data;
}

export async function fetchMyDevices() {
  const response = await http.get<ApiResponse<UserDeviceSummary[]>>("/my/notifications/devices");
  return response.data.data;
}

export async function registerMyDevice(payload: RegisterDeviceRequest) {
  const response = await http.post<ApiResponse<UserDeviceSummary>>("/my/notifications/devices", payload);
  return response.data.data;
}

export async function deactivateMyDevice(deviceId: string) {
  const response = await http.delete<ApiResponse<UserDeviceSummary>>(`/my/notifications/devices/${deviceId}`);
  return response.data.data;
}

export async function fetchNotificationDeliveries(notificationId: number) {
  const response = await http.get<ApiResponse<NotificationDeliverySummary[]>>(`/notifications/${notificationId}/deliveries`);
  return response.data.data;
}

export async function fetchNotificationDeliveryOpsSummary() {
  const response = await http.get<ApiResponse<NotificationDeliveryOpsSummary>>("/notifications/deliveries/summary");
  return response.data.data;
}

export async function fetchAdminNotificationOpsFeed(params?: {
  notificationType?: string;
  bizType?: string;
  deliveryStatus?: string;
  limit?: number;
}) {
  const response = await http.get<ApiResponse<AdminNotificationOpsItem[]>>("/notifications/ops/feed", {
    params: {
      notificationType: params?.notificationType?.trim() || undefined,
      bizType: params?.bizType?.trim() || undefined,
      deliveryStatus: params?.deliveryStatus?.trim() || undefined,
      limit: params?.limit ?? 20
    }
  });
  return response.data.data;
}

export async function retryNotificationDeliveries(notificationId: number, deliveryChannel?: string) {
  const response = await http.post<ApiResponse<NotificationDeliveryRetryResult>>(
    `/notifications/${notificationId}/deliveries/retry`,
    null,
    {
      params: { deliveryChannel }
    }
  );
  return response.data.data;
}

export async function retryNotificationDeliveriesBatch(payload: BatchRetryNotificationDeliveriesRequest) {
  const response = await http.post<ApiResponse<NotificationBatchRetryResult>>("/notifications/deliveries/retry-batch", payload);
  return response.data.data;
}

export async function fetchNotificationPolicies() {
  const response = await http.get<ApiResponse<NotificationPolicy[]>>("/notifications/policies");
  return response.data.data;
}

export async function upsertNotificationPolicy(payload: UpdateNotificationPolicyRequest) {
  const response = await http.post<ApiResponse<NotificationPolicy>>("/notifications/policies", payload);
  return response.data.data;
}
