import { http } from "../../services/http";
import type { ApiResponse, PageResponse } from "../../types/api";

export type UserAdminUser = {
  userId: number;
  username: string;
  displayName?: string | null;
  email?: string | null;
  mobile?: string | null;
  status: string;
  groupId?: number | null;
  groupName?: string | null;
  tenantId?: number | null;
  tenantName?: string | null;
  roles: string[];
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type UserAdminRole = {
  roleId: number;
  roleCode: string;
  roleName: string;
  tenantId?: number | null;
};

export type UserAdminTenant = {
  tenantId: number;
  tenantCode: string;
  tenantName: string;
};

export type UserAdminGroup = {
  groupId: number;
  groupCode: string;
  groupName: string;
  tenantId?: number | null;
  parentId?: number | null;
};

export type UserAdminUserPageQuery = {
  username?: string;
  status?: string;
  tenantId?: number;
  groupId?: number;
  page?: number;
  size?: number;
};

export type CreateUserAdminUserRequest = {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
  mobile?: string;
  tenantId?: number;
  groupId?: number;
  roleCodes?: string[];
};

export async function fetchUserAdminUserPage(query: UserAdminUserPageQuery) {
  const response = await http.get<ApiResponse<PageResponse<UserAdminUser>>>("/user-admin/users", { params: query });
  return response.data.data;
}

export async function fetchUserAdminRoles(tenantId?: number) {
  const response = await http.get<ApiResponse<UserAdminRole[]>>("/user-admin/roles", {
    params: tenantId == null ? undefined : { tenantId }
  });
  return response.data.data;
}

export async function fetchUserAdminTenants() {
  const response = await http.get<ApiResponse<UserAdminTenant[]>>("/user-admin/tenants");
  return response.data.data;
}

export async function fetchUserAdminGroups(tenantId?: number) {
  const response = await http.get<ApiResponse<UserAdminGroup[]>>("/user-admin/groups", {
    params: tenantId == null ? undefined : { tenantId }
  });
  return response.data.data;
}

export async function createUserAdminUser(request: CreateUserAdminUserRequest) {
  const response = await http.post<ApiResponse<UserAdminUser>>("/user-admin/users", request);
  return response.data.data;
}

export async function assignUserAdminRoles(userId: number, roleCodes: string[]) {
  const response = await http.post<ApiResponse<UserAdminUser>>(`/user-admin/users/${userId}/roles`, { roleCodes });
  return response.data.data;
}

export async function updateUserAdminStatus(userId: number, enabled: boolean) {
  const response = await http.post<ApiResponse<UserAdminUser>>(`/user-admin/users/${userId}/status`, { enabled });
  return response.data.data;
}

export async function resetUserAdminPassword(userId: number, newPassword: string) {
  const response = await http.post<ApiResponse<boolean>>(`/user-admin/users/${userId}/password/reset`, { newPassword });
  return response.data.data;
}
