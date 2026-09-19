import { showToast } from "../feedback/toast";

export function isLoginRoute(): boolean {
  return typeof window !== "undefined" && window.location.pathname.startsWith("/login");
}

/**
 * Authentication failures can be reported by several independent paths
 * (scheduled refresh, session restore, 401 interceptor). Use one toast key so
 * they replace each other, and keep the login page quiet because it already
 * renders the message inline.
 */
export function showAuthIssueToast(message: string): void {
  if (isLoginRoute()) {
    return;
  }
  showToast("warning", message, "auth-required");
}
