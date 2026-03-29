export const AUTH_REQUIRED_EVENT = "psy-admin-web-auth-required";

export type AuthRequiredDetail = {
  reason: "expired" | "unauthorized" | "logout";
  message?: string;
  from?: string;
};

export function dispatchAuthRequired(detail: AuthRequiredDetail) {
  if (typeof window === "undefined") {
    return;
  }
  window.dispatchEvent(new CustomEvent<AuthRequiredDetail>(AUTH_REQUIRED_EVENT, { detail }));
}
