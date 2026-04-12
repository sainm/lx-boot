import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type TaskSummary = {
  id: number;
  taskName: string;
  scaleId: number;
  scaleName: string;
  taskMode: string;
  anonymousFlag: boolean;
  startTime: string;
  endTime: string;
  status: string;
};

export type CreateTaskRequest = {
  taskName: string;
  scaleId: number;
  taskMode: string;
  anonymousFlag?: boolean;
  allowSaveFlag?: boolean;
  allowTimeoutSubmitFlag?: boolean;
  allowRetakeFlag?: boolean;
  startTime: string;
  endTime: string;
};

export type CreateTaskResponse = {
  id: number;
  status: string;
};

export type TaskDetail = TaskSummary & {
  allowSaveFlag: boolean;
  allowTimeoutSubmitFlag: boolean;
  allowRetakeFlag: boolean;
  scaleVersionNo?: string | null;
  scaleVersionGroupId?: number | null;
  createdBy?: number | null;
  createdAt: string;
  closedAt?: string | null;
  closedBy?: number | null;
  closeReason?: string | null;
  assignments: Array<{
    id: number;
    taskId: number;
    targetType: string;
    targetId: number;
    assignedBy?: number | null;
    assignedAt: string;
  }>;
};

export async function fetchTaskPage(params: {
  taskName?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<PageResponse<TaskSummary>>>("/tasks", {
    params
  });
  return response.data.data;
}

export async function createTask(payload: CreateTaskRequest) {
  const response = await http.post<ApiResponse<CreateTaskResponse>>("/tasks", payload);
  return response.data.data;
}

export async function fetchTaskDetail(taskId: number) {
  const response = await http.get<ApiResponse<TaskDetail>>(`/tasks/${taskId}`);
  return response.data.data;
}

export async function assignTaskGroups(taskId: number, groupIds: number[]) {
  const response = await http.post<ApiResponse<{ success: boolean }>>(`/tasks/${taskId}/assign-groups`, {
    groupIds
  });
  return response.data.data;
}

export async function assignTaskUsers(taskId: number, userIds: number[]) {
  const response = await http.post<ApiResponse<{ success: boolean }>>(`/tasks/${taskId}/assign-users`, {
    userIds
  });
  return response.data.data;
}

export async function closeTask(taskId: number, reason: string) {
  const response = await http.post<ApiResponse<TaskDetail>>(`/tasks/${taskId}/close`, {
    reason
  });
  return response.data.data;
}
