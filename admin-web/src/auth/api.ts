import axios from "axios";
import { clearAuthTokens, readAuthToken, readRefreshToken, setAuthTokens } from "./token";
import { getOrCreateDeviceId } from "./device";
import { dispatchAuthRequired } from "./events";
import { showToast } from "../feedback/toast";
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, translateMessage, type SupportedLocale } from "../i18n/messages";

type StarterApiResponse<T> = {
  code: string;
  message: string;
  data: T;
};

type AuthUser = {
  userId: number;
  username: string;
  displayName?: string | null;
};

export type PasswordLoginRequest = {
  principal: string;
  password: string;
  deviceId?: string;
  deviceType?: string;
  deviceName?: string;
};

export type PasswordLoginResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
};

export type RefreshTokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
};

export type RegisterRequest = {
  username: string;
  password: string;
  email?: string;
  mobile?: string;
  displayName?: string;
};

export type RegisterResponse = {
  userId: number;
  username: string;
  defaultRoles: string[];
};

export type RegistrationOptions = {
  selfServiceEnabled: boolean;
  passwordMinLength: number;
};

export const authHttp = axios.create({
  timeout: 10000
});

let refreshPromise: Promise<unknown> | null = null;

function readLocale(): SupportedLocale {
  if (typeof window === "undefined") {
    return DEFAULT_LOCALE;
  }
  const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  return stored === "en-US" || stored === "zh-CN" ? stored : DEFAULT_LOCALE;
}

authHttp.interceptors.request.use((config) => {
  const token = readAuthToken();
  const locale = readLocale();
  config.headers = config.headers ?? {};
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers["Accept-Language"] = locale;
  return config;
});

authHttp.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config as (Record<string, unknown> & { headers?: Record<string, string> }) | undefined;
    const currentPath = typeof window !== "undefined" ? window.location.pathname + window.location.search : "";
    const locale = readLocale();
    const requestUrl = typeof originalRequest?.url === "string" ? originalRequest.url : "";

    if (error?.response?.status === 401 && requestUrl.includes("/auth/token/refresh")) {
      const message = translateMessage(locale, "http.authRequired");
      clearAuthTokens();
      showToast("warning", message, "auth-required");
      dispatchAuthRequired({
        reason: "unauthorized",
        message,
        from: currentPath
      });
      return Promise.reject(error);
    }

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
          return authHttp(originalRequest);
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

export async function passwordLogin(request: PasswordLoginRequest) {
  const response = await authHttp.post<StarterApiResponse<PasswordLoginResponse>>("/auth/login/password", {
    ...request,
    deviceId: request.deviceId ?? getOrCreateDeviceId(),
    deviceType: request.deviceType ?? "WEB",
    deviceName: request.deviceName ?? "Admin Web"
  });
  const data = response.data.data;
  setAuthTokens(data.accessToken, data.refreshToken, { expiresInSeconds: data.expiresIn });
  return data;
}

export type SsoTicketExchangeRequest = {
  ticket: string;
  deviceId?: string;
  deviceType?: string;
  deviceName?: string;
};

/**
 * Full URL to start an SSO (CAS/OIDC) login. The backend generates state/nonce
 * and 302-redirects the browser to the school identity provider. `returnTo` is
 * the frontend callback page that will receive the one-time ticket.
 */
export function ssoAuthorizeUrl(provider: "oidc" | "cas", returnTo?: string) {
  const params = returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : "";
  return `/auth/sso/${provider}/authorize${params}`;
}

/** Exchange the one-time SSO ticket (from the callback redirect) for tokens. */
export async function exchangeSsoTicket(ticket: string) {
  const response = await authHttp.post<StarterApiResponse<PasswordLoginResponse>>("/auth/sso/token", {
    ticket,
    deviceId: getOrCreateDeviceId(),
    deviceType: "WEB",
    deviceName: "Admin Web"
  } satisfies SsoTicketExchangeRequest);
  const data = response.data.data;
  setAuthTokens(data.accessToken, data.refreshToken, { expiresInSeconds: data.expiresIn });
  return data;
}

export async function fetchRegistrationOptions() {
  const response = await authHttp.get<StarterApiResponse<RegistrationOptions>>("/auth/register/options");
  return response.data.data;
}

export async function registerAccount(request: RegisterRequest) {
  const response = await authHttp.post<StarterApiResponse<RegisterResponse>>("/auth/register", request);
  return response.data.data;
}

export async function refreshAuthToken(refreshToken?: string | null) {
  const token = refreshToken ?? readRefreshToken();
  if (!token) {
    throw new Error("missing refresh token");
  }
  const response = await authHttp.post<StarterApiResponse<RefreshTokenResponse>>("/auth/token/refresh", {
    refreshToken: token
  });
  const data = response.data.data;
  setAuthTokens(data.accessToken, data.refreshToken, { expiresInSeconds: data.expiresIn });
  return data;
}

export async function logoutAuth() {
  const refreshToken = readRefreshToken();
  const accessToken = readAuthToken();
  if (!refreshToken) {
    clearAuthTokens();
    return;
  }
  try {
    await authHttp.post(
      "/auth/logout",
      { refreshToken },
      {
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined
      }
    );
  } finally {
    clearAuthTokens();
  }
}
