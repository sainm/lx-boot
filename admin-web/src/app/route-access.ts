import { matchPath } from "react-router-dom";
import { hasAnyRole, pickPrimaryRole, type AppRole } from "../auth/roles";
import { appRoutes } from "./route-config";

export function defaultRouteForRole(role: AppRole): string {
  return role === "USER" ? "/home" : "/dashboard";
}

/** Landing route for a multi-role account: keep the USER shell when the account is a respondent. */
export function defaultRouteForRoles(roles: AppRole[]): string {
  return defaultRouteForRole(pickPrimaryRole(roles));
}

export function canRoleAccessPath(role: AppRole, path: string): boolean {
  return canRolesAccessPath([role], path);
}

/** A path is reachable when any of the held roles may open it (multi-role accounts). */
export function canRolesAccessPath(roles: AppRole[], path: string): boolean {
  const pathname = path.split(/[?#]/)[0];
  if (!pathname.startsWith("/")) {
    return false;
  }
  return appRoutes.some(
    (route) =>
      matchPath({ path: route.path, end: true }, pathname) !== null &&
      hasAnyRole(route.roles, roles)
  );
}

export function resolveSafeRedirect(from: string | undefined, role: AppRole): string {
  return resolveSafeRedirectForRoles(from, [role]);
}

export function resolveSafeRedirectForRoles(from: string | undefined, roles: AppRole[]): string {
  const fallback = defaultRouteForRoles(roles);
  if (!from || !from.startsWith("/") || from.startsWith("/login")) {
    return fallback;
  }
  return canRolesAccessPath(roles, from) ? from : fallback;
}
