import axios from "axios";
import { refreshAuthToken } from "../auth/api";
import { dispatchAuthRequired } from "../auth/events";
import { clearAuthTokens, readAuthToken, readRefreshToken } from "../auth/token";
import { showToast } from "../feedback/toast";
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, isSupportedLocale, translateMessage, type SupportedLocale } from "../i18n/messages";

export const http = axios.create({
  baseURL: "/api/v1",
  timeout: 10000
});

let refreshPromise: Promise<unknown> | null = null;

function readLocale(): SupportedLocale {
  if (typeof window === "undefined") {
    return DEFAULT_LOCALE;
  }
  const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  return isSupportedLocale(stored) ? stored : DEFAULT_LOCALE;
}

http.interceptors.request.use((config) => {
  const token = readAuthToken();
  const locale = readLocale();
  config.headers = config.headers ?? {};
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers["Accept-Language"] = locale;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config as (Record<string, unknown> & { headers?: Record<string, string> }) | undefined;
    const currentPath = typeof window !== "undefined" ? window.location.pathname + window.location.search : "";
    const locale = readLocale();

    if (error?.response?.status === 401 && originalRequest && !originalRequest.__retried) {
      const refreshToken = readRefreshToken();
      if (refreshToken) {
        try {
          refreshPromise ??= refreshAuthToken(refreshToken).finally(() => {
            refreshPromise = null;
          });
          await refreshPromise;
          originalRequest.__retried = true;
          originalRequest.headers = originalRequest.headers ?? {};
          const latestToken = readAuthToken();
          if (latestToken) {
            originalRequest.headers.Authorization = `Bearer ${latestToken}`;
          }
          return http(originalRequest);
        } catch {
          const message = translateMessage(locale, "http.sessionExpired");
          clearAuthTokens();
          showToast("warning", message, "auth-expired");
          dispatchAuthRequired({
            reason: "expired",
            message,
            from: currentPath
          });
        }
      } else {
        const message = translateMessage(locale, "http.authRequired");
        clearAuthTokens();
        showToast("warning", message, "auth-required");
        dispatchAuthRequired({
          reason: "unauthorized",
          message,
          from: currentPath
        });
      }
    } else if (error?.response?.status === 401) {
      const message = translateMessage(locale, "http.authRequired");
      clearAuthTokens();
      showToast("warning", message, "auth-required");
      dispatchAuthRequired({
        reason: "unauthorized",
        message,
        from: currentPath
      });
    }
    return Promise.reject(error);
  }
);
