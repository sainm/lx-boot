import axios from "axios";
import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { showToast } from "../feedback/toast";
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, isSupportedLocale, translateMessage as translateI18nMessage, type SupportedLocale } from "../i18n/messages";
import { logoutAuth, refreshAuthToken } from "./api";
import { AUTH_REQUIRED_EVENT, type AuthRequiredDetail } from "./events";
import { fetchMyProfile, type AuthProfile } from "./profile";
import { DEFAULT_ROLE, type AppRole } from "./roles";
import {
  AUTH_SESSION_CHANGED_EVENT,
  clearAuthTokens,
  getAccessTokenRefreshDelayMs,
  getAccessTokenRemainingMs,
  getTokenRemainingMs,
  isAccessTokenExpired,
  readAccessTokenExpiresAt,
  readAuthToken,
  readJwtExpiresAt,
  readJwtTokenUse,
  readRefreshToken,
  readTokenLastSyncAt,
  setAuthToken,
  setAuthTokens
} from "./token";

type SessionSource = "loading" | "server" | "expired" | "anonymous";
type SessionHealth = "healthy" | "expiring" | "refreshing" | "expired" | "anonymous";

type SessionContextValue = {
  currentRole: AppRole;
  sessionSource: SessionSource;
  sessionHealth: SessionHealth;
  profile: AuthProfile | null;
  authToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  authRequiredDetail: AuthRequiredDetail | null;
  accessTokenRemainingMs: number | null;
  accessTokenExpiresAt: number | null;
  accessTokenTokenUse: string | null;
  refreshTokenRemainingMs: number | null;
  refreshTokenExpiresAt: number | null;
  refreshTokenTokenUse: string | null;
  tokenLastSyncAt: number | null;
  setToken: (token: string | null) => void;
  setTokens: (accessToken: string | null, refreshToken: string | null, expiresInSeconds?: number | null) => void;
  clearAuthRequired: () => void;
  refreshSession: () => Promise<void>;
  buildDiagnosticsText: () => string;
  clearSession: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

function pickPrimaryRole(roles: AppRole[]) {
  const priority: AppRole[] = ["SYS_ADMIN", "ORG_MANAGER", "SCHOOL_LEADER", "ASSESSMENT_ADMIN", "COUNSELOR", "USER"];
  return priority.find((role) => roles.includes(role)) ?? DEFAULT_ROLE;
}

function readLocale() {
  if (typeof window === "undefined") {
    return DEFAULT_LOCALE;
  }
  const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  return isSupportedLocale(stored) ? stored : DEFAULT_LOCALE;
}

const SESSION_MESSAGE_KEYS = {
  refreshFailed: "session.refreshFailed",
  restoreFailed: "session.restoreFailed",
  expired: "session.expiredMessage",
  refreshSkipped: "session.refreshSkipped",
  refreshSuccess: "session.refreshSuccess",
  logout: "session.logoutSuccess"
} as const;

function tSession(locale: SupportedLocale, key: keyof typeof SESSION_MESSAGE_KEYS) {
  return translateI18nMessage(locale, SESSION_MESSAGE_KEYS[key]);
}

export function SessionProvider({ children }: PropsWithChildren) {
  const [authToken, setAuthTokenState] = useState<string | null>(readAuthToken);
  const [refreshToken, setRefreshTokenState] = useState<string | null>(readRefreshToken);
  const [profile, setProfile] = useState<AuthProfile | null | undefined>(undefined);
  const [authRequiredDetail, setAuthRequiredDetail] = useState<AuthRequiredDetail | null>(null);
  const [accessTokenRemainingMs, setAccessTokenRemainingMs] = useState<number | null>(getAccessTokenRemainingMs);
  const [refreshTokenRemainingMs, setRefreshTokenRemainingMs] = useState<number | null>(
    getTokenRemainingMs(readJwtExpiresAt(readRefreshToken()))
  );
  const [accessTokenExpiresAt, setAccessTokenExpiresAt] = useState<number | null>(readAccessTokenExpiresAt);
  const [refreshTokenExpiresAt, setRefreshTokenExpiresAt] = useState<number | null>(readJwtExpiresAt(readRefreshToken()));
  const [accessTokenTokenUse, setAccessTokenTokenUse] = useState<string | null>(readJwtTokenUse(readAuthToken()));
  const [refreshTokenTokenUse, setRefreshTokenTokenUse] = useState<string | null>(readJwtTokenUse(readRefreshToken()));
  const [tokenLastSyncAt, setTokenLastSyncAt] = useState<number | null>(readTokenLastSyncAt);
  const [refreshingSession, setRefreshingSession] = useState(false);

  useEffect(() => {
    const syncTokenState = () => {
      setAuthTokenState(readAuthToken());
      setRefreshTokenState(readRefreshToken());
      setAccessTokenRemainingMs(getAccessTokenRemainingMs());
      setRefreshTokenRemainingMs(getTokenRemainingMs(readJwtExpiresAt(readRefreshToken())));
      setAccessTokenExpiresAt(readAccessTokenExpiresAt());
      setRefreshTokenExpiresAt(readJwtExpiresAt(readRefreshToken()));
      setAccessTokenTokenUse(readJwtTokenUse(readAuthToken()));
      setRefreshTokenTokenUse(readJwtTokenUse(readRefreshToken()));
      setTokenLastSyncAt(readTokenLastSyncAt());
    };
    const onAuthRequired = (event: Event) => {
      const customEvent = event as CustomEvent<AuthRequiredDetail>;
      setAuthRequiredDetail(customEvent.detail);
      setProfile(null);
      syncTokenState();
    };

    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, syncTokenState);
    window.addEventListener("storage", syncTokenState);
    window.addEventListener(AUTH_REQUIRED_EVENT, onAuthRequired as EventListener);
    return () => {
      window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, syncTokenState);
      window.removeEventListener("storage", syncTokenState);
      window.removeEventListener(AUTH_REQUIRED_EVENT, onAuthRequired as EventListener);
    };
  }, []);

  useEffect(() => {
    if (!authToken) {
      setAccessTokenRemainingMs(null);
      setRefreshTokenRemainingMs(getTokenRemainingMs(readJwtExpiresAt(readRefreshToken())));
      return;
    }

    const syncRemaining = () => {
      setAccessTokenRemainingMs(getAccessTokenRemainingMs());
      setRefreshTokenRemainingMs(getTokenRemainingMs(readJwtExpiresAt(readRefreshToken())));
      setAccessTokenExpiresAt(readAccessTokenExpiresAt());
      setRefreshTokenExpiresAt(readJwtExpiresAt(readRefreshToken()));
      setAccessTokenTokenUse(readJwtTokenUse(readAuthToken()));
      setRefreshTokenTokenUse(readJwtTokenUse(readRefreshToken()));
      setTokenLastSyncAt(readTokenLastSyncAt());
    };

    syncRemaining();
    const timer = window.setInterval(syncRemaining, 1_000);
    return () => window.clearInterval(timer);
  }, [authToken]);

  useEffect(() => {
    if (!authToken) {
      setProfile(null);
      return;
    }

    if (refreshToken && isAccessTokenExpired(5_000)) {
      setRefreshingSession(true);
      void refreshAuthToken(refreshToken)
        .catch((error: unknown) => {
          const locale = readLocale();
          clearAuthTokens();
          setAuthTokenState(null);
          setRefreshTokenState(null);
          setProfile(null);
          showToast("warning", tSession(locale, "refreshFailed"), "auth-refresh-failed");
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: tSession(locale, "refreshFailed")
          });
        })
        .finally(() => setRefreshingSession(false));
      return;
    }

    let active = true;
    setProfile(undefined);

    void fetchMyProfile()
      .then((data) => {
        if (active) {
          setProfile(data);
          setAuthRequiredDetail(null);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          const locale = readLocale();
          clearAuthTokens();
          setAuthTokenState(null);
          setRefreshTokenState(null);
          setProfile(null);
          showToast("warning", tSession(locale, "restoreFailed"), "auth-restore-failed");
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: tSession(locale, "restoreFailed")
          });
        }
      });

    return () => {
      active = false;
    };
  }, [authToken, refreshToken]);

  useEffect(() => {
    if (!authToken || !refreshToken) {
      return;
    }

    const triggerRefresh = () => {
      const locale = readLocale();
      setRefreshingSession(true);
      void refreshAuthToken(refreshToken)
        .catch((error: unknown) => {
          clearAuthTokens();
          setAuthTokenState(null);
          setRefreshTokenState(null);
          setProfile(null);
          showToast("warning", tSession(locale, "expired"), "auth-expired");
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: tSession(locale, "expired")
          });
        })
        .finally(() => setRefreshingSession(false));
    };

    if (isAccessTokenExpired(60_000)) {
      triggerRefresh();
      return;
    }

    const refreshDelayMs = getAccessTokenRefreshDelayMs(60_000) ?? 60_000;
    const refreshAt = window.setTimeout(triggerRefresh, Math.max(1_000, refreshDelayMs));
    return () => window.clearTimeout(refreshAt);
  }, [authToken, refreshToken]);

  const currentRole = profile ? pickPrimaryRole(profile.roles) : DEFAULT_ROLE;
  const isExpiredSession = authRequiredDetail?.reason === "expired" || authRequiredDetail?.reason === "unauthorized";
  const sessionSource: SessionSource =
    profile
      ? "server"
      : isExpiredSession
        ? "expired"
        : authToken
          ? "loading"
          : "anonymous";

  const sessionHealth: SessionHealth =
    sessionSource === "expired"
      ? "expired"
      : sessionSource === "anonymous"
        ? "anonymous"
        : refreshingSession
          ? "refreshing"
          : accessTokenRemainingMs !== null && accessTokenRemainingMs <= 120_000
            ? "expiring"
            : "healthy";

  const value = useMemo<SessionContextValue>(
    () => ({
      currentRole,
      sessionSource,
      sessionHealth,
      profile: profile ?? null,
      authToken,
      refreshToken,
      isAuthenticated: Boolean(profile),
      authRequiredDetail,
      accessTokenRemainingMs,
      accessTokenExpiresAt,
      accessTokenTokenUse,
      refreshTokenRemainingMs,
      refreshTokenExpiresAt,
      refreshTokenTokenUse,
      tokenLastSyncAt,
      setToken: (token: string | null) => {
        setAuthToken(token);
        setAuthTokenState(token);
      },
      setTokens: (accessToken: string | null, nextRefreshToken: string | null, expiresInSeconds?: number | null) => {
        setAuthTokens(accessToken, nextRefreshToken, { expiresInSeconds });
        setAuthTokenState(accessToken);
        setRefreshTokenState(nextRefreshToken);
        setAccessTokenRemainingMs(getAccessTokenRemainingMs());
        setRefreshTokenRemainingMs(getTokenRemainingMs(readJwtExpiresAt(nextRefreshToken)));
        setAccessTokenExpiresAt(readAccessTokenExpiresAt());
        setRefreshTokenExpiresAt(readJwtExpiresAt(nextRefreshToken));
        setAccessTokenTokenUse(readJwtTokenUse(accessToken));
        setRefreshTokenTokenUse(readJwtTokenUse(nextRefreshToken));
        setTokenLastSyncAt(readTokenLastSyncAt());
        setProfile(null);
        setAuthRequiredDetail(null);
      },
      clearAuthRequired: () => setAuthRequiredDetail(null),
      refreshSession: async () => {
        if (!refreshToken) {
          showToast("info", tSession(readLocale(), "refreshSkipped"), "auth-refresh-skipped");
          return;
        }
        setRefreshingSession(true);
        try {
          const result = await refreshAuthToken(refreshToken);
          setAuthTokens(result.accessToken, result.refreshToken, { expiresInSeconds: result.expiresIn });
          setAuthTokenState(result.accessToken);
          setRefreshTokenState(result.refreshToken);
          setAccessTokenRemainingMs(getAccessTokenRemainingMs());
          setRefreshTokenRemainingMs(getTokenRemainingMs(readJwtExpiresAt(result.refreshToken)));
          setAccessTokenExpiresAt(readAccessTokenExpiresAt());
          setRefreshTokenExpiresAt(readJwtExpiresAt(result.refreshToken));
          setAccessTokenTokenUse(readJwtTokenUse(result.accessToken));
          setRefreshTokenTokenUse(readJwtTokenUse(result.refreshToken));
          setTokenLastSyncAt(readTokenLastSyncAt());
          showToast("success", tSession(readLocale(), "refreshSuccess"), "auth-refresh-success");
        } catch (error) {
          const locale = readLocale();
          clearAuthTokens();
          setAuthTokenState(null);
          setRefreshTokenState(null);
          setProfile(null);
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: tSession(locale, "refreshFailed")
          });
          showToast("warning", tSession(locale, "refreshFailed"), "auth-refresh-failed");
        } finally {
          setRefreshingSession(false);
        }
      },
      buildDiagnosticsText: () =>
        JSON.stringify(
          {
            currentRole,
            sessionSource,
            sessionHealth,
            isAuthenticated: Boolean(profile),
            authRequiredDetail,
            profile,
            tokenState: {
              hasAccessToken: Boolean(authToken),
              hasRefreshToken: Boolean(refreshToken),
              accessTokenExpiresAt,
              accessTokenRemainingMs,
              accessTokenTokenUse,
              refreshTokenExpiresAt,
              refreshTokenRemainingMs,
              refreshTokenTokenUse,
              tokenLastSyncAt
            }
          },
          null,
          2
        ),
      clearSession: async () => {
        const locale = readLocale();
        await logoutAuth();
        setAuthTokenState(null);
        setRefreshTokenState(null);
        setAccessTokenRemainingMs(null);
        setRefreshTokenRemainingMs(null);
        setAccessTokenExpiresAt(null);
        setRefreshTokenExpiresAt(null);
        setAccessTokenTokenUse(null);
        setRefreshTokenTokenUse(null);
        setTokenLastSyncAt(null);
        setProfile(null);
        showToast("info", tSession(locale, "logout"), "auth-logout");
        setAuthRequiredDetail({ reason: "logout", message: tSession(locale, "logout") });
      }
    }),
    [
      accessTokenExpiresAt,
      accessTokenRemainingMs,
      accessTokenTokenUse,
      authRequiredDetail,
      authToken,
      currentRole,
      profile,
      refreshToken,
      refreshTokenExpiresAt,
      refreshTokenRemainingMs,
      refreshTokenTokenUse,
      sessionHealth,
      sessionSource,
      tokenLastSyncAt
    ]
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession must be used within SessionProvider");
  }
  return context;
}
