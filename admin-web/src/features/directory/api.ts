import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type DirectoryUser = {
  userId: number;
  username: string;
  displayName: string;
  status: string;
};

export type DirectoryGroup = {
  groupId: number;
  groupCode: string;
  groupName: string;
};

export type DirectoryTask = {
  taskId: number;
  taskName: string;
  scaleId: number;
  scaleName: string;
};

export type DirectoryScale = {
  scaleId: number;
  scaleCode: string;
  scaleName: string;
  status: string;
};

/**
 * Shared tenant-scoped pickers.  They replace the raw id text boxes on the
 * appointment, warning, report, publication and audit screens.
 */
export async function fetchDirectoryUsers(params: {
  keyword?: string;
  staffOnly?: boolean;
  activeOnly?: boolean;
  page?: number;
  size?: number;
}) {
  const response = await http.get<ApiResponse<PageResponse<DirectoryUser>>>("/directory/users", { params });
  return response.data.data;
}

export async function fetchDirectoryGroups() {
  const response = await http.get<ApiResponse<DirectoryGroup[]>>("/directory/groups");
  return response.data.data;
}

export async function fetchDirectoryTasks(keyword?: string) {
  const response = await http.get<ApiResponse<DirectoryTask[]>>("/directory/tasks", {
    params: keyword ? { keyword } : undefined
  });
  return response.data.data;
}

export async function fetchDirectoryScales(keyword?: string) {
  const response = await http.get<ApiResponse<DirectoryScale[]>>("/directory/scales", {
    params: keyword ? { keyword } : undefined
  });
  return response.data.data;
}
