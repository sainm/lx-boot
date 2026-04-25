import { http } from "../../services/http";
import type { ApiResponse } from "../../types/api";

export type MyProfile = {
  userId: number;
  username: string;
  nickname?: string | null;
  displayName?: string | null;
  email?: string | null;
  mobile?: string | null;
  avatarUrl?: string | null;
  groupId?: number | null;
  groupName?: string | null;
  tenantId?: number | null;
  tenantName?: string | null;
  roles: string[];
  updatedAt?: string | null;
};

export type UpdateMyProfileRequest = {
  nickname?: string | null;
  displayName?: string | null;
  email?: string | null;
  mobile?: string | null;
  avatarUrl?: string | null;
};

export async function fetchMyEditableProfile() {
  const response = await http.get<ApiResponse<MyProfile>>("/my/profile");
  return response.data.data;
}

export async function updateMyEditableProfile(request: UpdateMyProfileRequest) {
  const response = await http.post<ApiResponse<MyProfile>>("/my/profile", request);
  return response.data.data;
}
