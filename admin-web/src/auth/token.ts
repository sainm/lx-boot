export const AUTH_TOKEN_STORAGE_KEY = "psy-admin-web.auth-token";
export const REFRESH_TOKEN_STORAGE_KEY = "psy-admin-web.refresh-token";
export const ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY = "psy-admin-web.access-token-expires-at";
export const TOKEN_LAST_SYNC_AT_STORAGE_KEY = "psy-admin-web.token-last-sync-at";
export const DEV_SESSION_STORAGE_KEY = "psy-admin-web.dev-session-enabled";
export const AUTH_SESSION_CHANGED_EVENT = "psy-admin-web-auth-session-changed";

const DEFAULT_REFRESH_BUFFER_MS = 60_000;

function decodeJwtPayload(token: string) {
  try {
    const parts = token.split(".");
    if (parts.length < 2 || typeof window === "undefined") {
      return null;
    }
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    return JSON.parse(window.atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function readAuthToken() {
  if (typeof window === "undefined") {
    return null;
  }
  const token = window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
  return token && token.trim() ? token : null;
}

export function readRefreshToken() {
  if (typeof window === "undefined") {
    return null;
  }
  const token = window.localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
  return token && token.trim() ? token : null;
}

export function readAccessTokenExpiresAt() {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

export function readTokenLastSyncAt() {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(TOKEN_LAST_SYNC_AT_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

export function readJwtExpiresAt(token: string | null | undefined) {
  if (!token) {
    return null;
  }
  const payload = decodeJwtPayload(token);
  const exp = typeof payload?.exp === "number" ? payload.exp : null;
  return exp ? exp * 1000 : null;
}

export function readAccessTokenJwtExpiresAt() {
  return readJwtExpiresAt(readAuthToken());
}

export function readRefreshTokenExpiresAt() {
  return readJwtExpiresAt(readRefreshToken());
}

export function readJwtTokenUse(token: string | null | undefined) {
  if (!token) {
    return null;
  }
  const payload = decodeJwtPayload(token);
  return typeof payload?.tokenUse === "string" ? payload.tokenUse : null;
}

export function getTokenRemainingMs(expiresAt: number | null | undefined) {
  if (!expiresAt) {
    return null;
  }
  return expiresAt - Date.now();
}

export function getAccessTokenRemainingMs() {
  return getTokenRemainingMs(readAccessTokenExpiresAt());
}

export function getRefreshTokenRemainingMs() {
  return getTokenRemainingMs(readRefreshTokenExpiresAt());
}

export function getAccessTokenRefreshDelayMs(bufferMs = DEFAULT_REFRESH_BUFFER_MS) {
  const remainingMs = getAccessTokenRemainingMs();
  if (remainingMs === null) {
    return null;
  }
  return Math.max(0, remainingMs - bufferMs);
}

export function isAccessTokenExpired(bufferMs = 0) {
  const remainingMs = getAccessTokenRemainingMs();
  if (remainingMs === null) {
    return false;
  }
  return remainingMs <= bufferMs;
}

export function shouldRefreshAccessToken(bufferMs = DEFAULT_REFRESH_BUFFER_MS) {
  return isAccessTokenExpired(bufferMs);
}

type SetAuthTokensOptions = {
  expiresInSeconds?: number | null;
};

export function setAuthToken(token: string | null) {
  setAuthTokens(token, readRefreshToken(), { expiresInSeconds: null });
}

export function setRefreshToken(token: string | null) {
  setAuthTokens(readAuthToken(), token, { expiresInSeconds: null });
}

export function setAuthTokens(
  accessToken: string | null,
  refreshToken: string | null,
  options?: SetAuthTokensOptions
) {
  if (typeof window === "undefined") {
    return;
  }
  if (accessToken && accessToken.trim()) {
    window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, accessToken.trim());
  } else {
    window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  }
  if (refreshToken && refreshToken.trim()) {
    window.localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken.trim());
  } else {
    window.localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  }
  if (accessToken || refreshToken) {
    window.localStorage.setItem(TOKEN_LAST_SYNC_AT_STORAGE_KEY, String(Date.now()));
  } else {
    window.localStorage.removeItem(TOKEN_LAST_SYNC_AT_STORAGE_KEY);
  }
  if (accessToken && options?.expiresInSeconds && options.expiresInSeconds > 0) {
    window.localStorage.setItem(
      ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY,
      String(Date.now() + options.expiresInSeconds * 1000)
    );
  } else if (!accessToken) {
    window.localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY);
  }
  window.dispatchEvent(new Event(AUTH_SESSION_CHANGED_EVENT));
}

export function readDevSessionEnabled() {
  if (typeof window === "undefined") {
    return false;
  }
  return window.localStorage.getItem(DEV_SESSION_STORAGE_KEY) === "true";
}

export function setDevSessionEnabled(enabled: boolean) {
  if (typeof window === "undefined") {
    return;
  }
  if (enabled) {
    window.localStorage.setItem(DEV_SESSION_STORAGE_KEY, "true");
  } else {
    window.localStorage.removeItem(DEV_SESSION_STORAGE_KEY);
  }
  window.dispatchEvent(new Event(AUTH_SESSION_CHANGED_EVENT));
}

export function clearAuthToken() {
  setAuthToken(null);
}

export function clearAuthTokens() {
  setAuthTokens(null, null);
}
