import axios from "axios";
import { refreshAuthToken } from "../auth/api";
import { dispatchAuthRequired } from "../auth/events";
import { clearAuthTokens, readAuthToken, readRefreshToken } from "../auth/token";
import { showToast } from "../feedback/toast";

export const http = axios.create({
  baseURL: "/api/v1",
  timeout: 10000
});

let refreshPromise: Promise<unknown> | null = null;

http.interceptors.request.use((config) => {
  const token = readAuthToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config as (Record<string, unknown> & { headers?: Record<string, string> }) | undefined;
    const currentPath = typeof window !== "undefined" ? window.location.pathname + window.location.search : "";

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
          clearAuthTokens();
          showToast("warning", "Your session has expired. Please sign in again.", "auth-expired");
          dispatchAuthRequired({
            reason: "expired",
            message: "Your session has expired. Please sign in again.",
            from: currentPath
          });
        }
      } else {
        clearAuthTokens();
        showToast("warning", "Authentication is required.", "auth-required");
        dispatchAuthRequired({
          reason: "unauthorized",
          message: "Authentication is required.",
          from: currentPath
        });
      }
    } else if (error?.response?.status === 401) {
      clearAuthTokens();
      showToast("warning", "Authentication is required.", "auth-required");
      dispatchAuthRequired({
        reason: "unauthorized",
        message: "Authentication is required.",
        from: currentPath
      });
    }
    return Promise.reject(error);
  }
);
