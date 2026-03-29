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

export async function fetchMyNotifications() {
  const response = await http.get<ApiResponse<MyNotification[]>>("/my/notifications");
  return response.data.data;
}

export async function markNotificationRead(notificationId: number) {
  const response = await http.post<ApiResponse<NotificationActionResult>>(`/my/notifications/${notificationId}/read`);
  return response.data.data;
}
