import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  readJwtExpiresAt,
  readJwtTokenUse,
  getTokenRemainingMs,
  getAccessTokenRefreshDelayMs,
  isAccessTokenExpired,
  shouldRefreshAccessToken,
  readAuthToken,
  readRefreshToken,
  setAuthTokens,
  clearAuthTokens,
  AUTH_TOKEN_STORAGE_KEY,
  REFRESH_TOKEN_STORAGE_KEY,
  ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY,
  TOKEN_LAST_SYNC_AT_STORAGE_KEY,
  AUTH_SESSION_CHANGED_EVENT
} from "../auth/token";

// ── helpers ──────────────────────────────────────────────────────────────────

function makeJwt(payload: Record<string, unknown>) {
  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

function futureExp(offsetSeconds = 3600) {
  return Math.floor(Date.now() / 1000) + offsetSeconds;
}

function pastExp(offsetSeconds = 3600) {
  return Math.floor(Date.now() / 1000) - offsetSeconds;
}

// ── readJwtExpiresAt ─────────────────────────────────────────────────────────

describe("readJwtExpiresAt", () => {
  it("returns null for null token", () => {
    expect(readJwtExpiresAt(null)).toBeNull();
  });

  it("returns null for token with no exp field", () => {
    const token = makeJwt({ sub: "user1" });
    expect(readJwtExpiresAt(token)).toBeNull();
  });

  it("returns exp * 1000 for valid token", () => {
    const exp = futureExp(3600);
    const token = makeJwt({ exp });
    expect(readJwtExpiresAt(token)).toBe(exp * 1000);
  });

  it("returns null for malformed token string", () => {
    expect(readJwtExpiresAt("not.a.valid.token.at.all")).toBeNull();
  });
});

// ── readJwtTokenUse ──────────────────────────────────────────────────────────

describe("readJwtTokenUse", () => {
  it("returns null for null token", () => {
    expect(readJwtTokenUse(null)).toBeNull();
  });

  it("returns tokenUse field when present", () => {
    const token = makeJwt({ tokenUse: "access" });
    expect(readJwtTokenUse(token)).toBe("access");
  });

  it("returns null when tokenUse field is missing", () => {
    const token = makeJwt({ sub: "user1" });
    expect(readJwtTokenUse(token)).toBeNull();
  });
});

// ── getTokenRemainingMs ──────────────────────────────────────────────────────

describe("getTokenRemainingMs", () => {
  it("returns null for null expiresAt", () => {
    expect(getTokenRemainingMs(null)).toBeNull();
  });

  it("returns null for undefined expiresAt", () => {
    expect(getTokenRemainingMs(undefined)).toBeNull();
  });

  it("returns positive number for future expiresAt", () => {
    const expiresAt = Date.now() + 60_000;
    const remaining = getTokenRemainingMs(expiresAt);
    expect(remaining).toBeGreaterThan(0);
    expect(remaining).toBeLessThanOrEqual(60_000);
  });

  it("returns negative number for past expiresAt", () => {
    const expiresAt = Date.now() - 60_000;
    expect(getTokenRemainingMs(expiresAt)).toBeLessThan(0);
  });
});

// ── getAccessTokenRefreshDelayMs ─────────────────────────────────────────────

describe("getAccessTokenRefreshDelayMs", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("returns null when no access token expiry stored", () => {
    expect(getAccessTokenRefreshDelayMs()).toBeNull();
  });

  it("returns 0 when token is already within buffer window", () => {
    // expires in 30s, buffer=60s → remaining(30000) - buffer(60000) = -30000 → max(0, …) = 0
    localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + 30_000));
    const delay = getAccessTokenRefreshDelayMs(60_000);
    expect(delay).toBe(0);
  });

  it("returns positive delay when token has plenty of time left", () => {
    localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + 600_000));
    const delay = getAccessTokenRefreshDelayMs(60_000);
    expect(delay).toBeGreaterThan(0);
  });
});

// ── isAccessTokenExpired ─────────────────────────────────────────────────────

describe("isAccessTokenExpired", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("returns false when no expiry is stored (defensive default)", () => {
    expect(isAccessTokenExpired()).toBe(false);
  });

  it("returns true when token expiry is in the past", () => {
    localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() - 10_000));
    expect(isAccessTokenExpired()).toBe(true);
  });

  it("returns false when token expiry is in the future", () => {
    localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + 300_000));
    expect(isAccessTokenExpired()).toBe(false);
  });

  it("returns true when remaining time is within bufferMs", () => {
    localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + 30_000));
    expect(isAccessTokenExpired(60_000)).toBe(true);
  });
});

// ── shouldRefreshAccessToken ─────────────────────────────────────────────────

describe("shouldRefreshAccessToken", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("returns false when no expiry stored", () => {
    expect(shouldRefreshAccessToken()).toBe(false);
  });

  it("returns true when token expires within the default buffer window", () => {
    // default buffer = 60s; token expires in 30s
    localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + 30_000));
    expect(shouldRefreshAccessToken()).toBe(true);
  });
});

// ── localStorage read/write ──────────────────────────────────────────────────

describe("readAuthToken / readRefreshToken", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("returns null when auth token is absent", () => {
    expect(readAuthToken()).toBeNull();
  });

  it("returns null for whitespace-only auth token", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "   ");
    expect(readAuthToken()).toBeNull();
  });

  it("returns stored auth token", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "my-token");
    expect(readAuthToken()).toBe("my-token");
  });

  it("returns null when refresh token is absent", () => {
    expect(readRefreshToken()).toBeNull();
  });

  it("returns stored refresh token", () => {
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, "refresh-token");
    expect(readRefreshToken()).toBe("refresh-token");
  });
});

// ── setAuthTokens ─────────────────────────────────────────────────────────────

describe("setAuthTokens", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("stores both tokens and updates TOKEN_LAST_SYNC_AT", () => {
    setAuthTokens("access-tok", "refresh-tok");
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBe("access-tok");
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBe("refresh-tok");
    expect(localStorage.getItem(TOKEN_LAST_SYNC_AT_STORAGE_KEY)).not.toBeNull();
  });

  it("dispatches AUTH_SESSION_CHANGED_EVENT", () => {
    const handler = vi.fn();
    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, handler);
    setAuthTokens("tok", "ref");
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, handler);
  });

  it("removes tokens and sync timestamp when both are null", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "old");
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, "old");
    setAuthTokens(null, null);
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(TOKEN_LAST_SYNC_AT_STORAGE_KEY)).toBeNull();
  });

  it("stores expiresAt when expiresInSeconds is provided", () => {
    const before = Date.now();
    setAuthTokens("tok", null, { expiresInSeconds: 300 });
    const stored = Number(localStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_STORAGE_KEY));
    expect(stored).toBeGreaterThanOrEqual(before + 300_000 - 100);
    expect(stored).toBeLessThanOrEqual(before + 300_000 + 100);
  });
});

// ── clearAuthTokens ───────────────────────────────────────────────────────────

describe("clearAuthTokens", () => {
  it("removes all token keys from localStorage", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "a");
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, "r");
    clearAuthTokens();
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBeNull();
  });
});
