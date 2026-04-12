export type AppRole = "USER" | "COUNSELOR" | "ASSESSMENT_ADMIN" | "ORG_MANAGER" | "SYS_ADMIN";

export const ROLE_STORAGE_KEY = "psy-admin-web.current-role";

export const ROLE_I18N_KEYS: Record<AppRole, string> = {
  USER: "role.USER",
  COUNSELOR: "role.COUNSELOR",
  ASSESSMENT_ADMIN: "role.ASSESSMENT_ADMIN",
  ORG_MANAGER: "role.ORG_MANAGER",
  SYS_ADMIN: "role.SYS_ADMIN"
};

export const ROLE_LABELS: Record<AppRole, string> = {
  USER: "Respondent",
  COUNSELOR: "Counselor",
  ASSESSMENT_ADMIN: "Assessment Admin",
  ORG_MANAGER: "Organization Manager",
  SYS_ADMIN: "System Admin"
};

export const APP_ROLE_OPTIONS: Array<{ label: string; value: AppRole }> = [
  { label: ROLE_LABELS.COUNSELOR, value: "COUNSELOR" },
  { label: ROLE_LABELS.ASSESSMENT_ADMIN, value: "ASSESSMENT_ADMIN" },
  { label: ROLE_LABELS.ORG_MANAGER, value: "ORG_MANAGER" },
  { label: ROLE_LABELS.SYS_ADMIN, value: "SYS_ADMIN" }
];

export const ADMIN_ROLE_OPTIONS = APP_ROLE_OPTIONS;

export const DEFAULT_ROLE: AppRole = "ASSESSMENT_ADMIN";

export function getRoleLabel(role: AppRole, translate: (key: string) => string) {
  return translate(ROLE_I18N_KEYS[role]);
}

export function getAdminRoleOptions(translate: (key: string) => string) {
  return ADMIN_ROLE_OPTIONS.map((option) => ({
    label: getRoleLabel(option.value, translate),
    value: option.value
  }));
}

export function isAppRole(value: string | null | undefined): value is AppRole {
  return Boolean(value && Object.prototype.hasOwnProperty.call(ROLE_LABELS, value));
}

export function canAccess(allowedRoles: AppRole[], currentRole: AppRole) {
  return allowedRoles.includes(currentRole);
}
