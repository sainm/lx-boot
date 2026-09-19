import { matchPath } from "react-router-dom";
import { canAccess, type AppRole } from "../auth/roles";
import { appRoutes } from "./route-config";

export function defaultRouteForRole(role: AppRole): string {
  return role === "USER" ? "/home" : "/dashboard";
}

export function canRoleAccessPath(role: AppRole, path: string): boolean {
  const pathname = path.split(/[?#]/)[0];
  if (!pathname.startsWith("/")) {
    return false;
  }
  return appRoutes.some(
    (route) =>
      matchPath({ path: route.path, end: true }, pathname) !== null &&
      canAccess(route.roles, role)
  );
}

export function resolveSafeRedirect(from: string | undefined, role: AppRole): string {
  const fallback = defaultRouteForRole(role);
  if (!from || !from.startsWith("/") || from.startsWith("/login")) {
    return fallback;
  }
  return canRoleAccessPath(role, from) ? from : fallback;
}
