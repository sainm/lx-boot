export type AppRole = "USER" | "COUNSELOR" | "ASSESSMENT_ADMIN" | "ORG_MANAGER" | "SYS_ADMIN";

export const ROLE_STORAGE_KEY = "psy-admin-web.current-role";

export const APP_ROLE_OPTIONS: Array<{ label: string; value: AppRole }> = [
  { label: "咨询师", value: "COUNSELOR" },
  { label: "测评管理员", value: "ASSESSMENT_ADMIN" },
  { label: "机构管理者", value: "ORG_MANAGER" },
  { label: "系统管理员", value: "SYS_ADMIN" }
];

export const ADMIN_ROLE_OPTIONS = APP_ROLE_OPTIONS;

export const ROLE_LABELS: Record<AppRole, string> = {
  USER: "被测者",
  COUNSELOR: "咨询师",
  ASSESSMENT_ADMIN: "测评管理员",
  ORG_MANAGER: "机构管理者",
  SYS_ADMIN: "系统管理员"
};

export const DEFAULT_ROLE: AppRole = "ASSESSMENT_ADMIN";

export function isAppRole(value: string | null | undefined): value is AppRole {
  return Boolean(value && Object.prototype.hasOwnProperty.call(ROLE_LABELS, value));
}

export function canAccess(allowedRoles: AppRole[], currentRole: AppRole) {
  return allowedRoles.includes(currentRole);
}
