import axios from "axios";
import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { showToast } from "../feedback/toast";
import { logoutAuth, refreshAuthToken } from "./api";
import { AUTH_REQUIRED_EVENT, type AuthRequiredDetail } from "./events";
import { fetchMyProfile, type AuthProfile } from "./profile";
import { DEFAULT_ROLE, isAppRole, ROLE_STORAGE_KEY, type AppRole } from "./roles";
import {
  AUTH_SESSION_CHANGED_EVENT,
  clearAuthTokens,
  getAccessTokenRefreshDelayMs,
  getAccessTokenRemainingMs,
  getTokenRemainingMs,
  isAccessTokenExpired,
  readAccessTokenExpiresAt,
  readAuthToken,
  readDevSessionEnabled,
  readJwtExpiresAt,
  readJwtTokenUse,
  readRefreshToken,
  readTokenLastSyncAt,
  setAuthToken,
  setAuthTokens,
  setDevSessionEnabled
} from "./token";

type SessionSource = "loading" | "server" | "dev" | "expired" | "anonymous";
type SessionHealth = "healthy" | "expiring" | "refreshing" | "expired" | "anonymous" | "development";

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
  setCurrentRole: (role: AppRole) => void;
  setToken: (token: string | null) => void;
  setTokens: (accessToken: string | null, refreshToken: string | null, expiresInSeconds?: number | null) => void;
  enableDevelopmentSession: (role: AppRole, token?: string | null) => void;
  disableDevelopmentSession: () => void;
  clearAuthRequired: () => void;
  resetRole: () => void;
  refreshSession: () => Promise<void>;
  buildDiagnosticsText: () => string;
  clearSession: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

function readStoredRole() {
  if (typeof window === "undefined") {
    return DEFAULT_ROLE;
  }
  const storedRole = window.localStorage.getItem(ROLE_STORAGE_KEY);
  return isAppRole(storedRole) ? storedRole : DEFAULT_ROLE;
}

function pickPrimaryRole(roles: AppRole[]) {
  const priority: AppRole[] = ["SYS_ADMIN", "ORG_MANAGER", "ASSESSMENT_ADMIN", "COUNSELOR", "USER"];
  return priority.find((role) => roles.includes(role)) ?? DEFAULT_ROLE;
}

export function SessionProvider({ children }: PropsWithChildren) {
  const [devRole, setDevRoleState] = useState<AppRole>(readStoredRole);
  const [authToken, setAuthTokenState] = useState<string | null>(readAuthToken);
  const [refreshToken, setRefreshTokenState] = useState<string | null>(readRefreshToken);
  const [devSessionEnabled, setDevSessionEnabledState] = useState<boolean>(readDevSessionEnabled);
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
  const developmentSessionActive = import.meta.env.DEV && devSessionEnabled;

  useEffect(() => {
    const syncTokenState = () => {
      setAuthTokenState(readAuthToken());
      setRefreshTokenState(readRefreshToken());
      setDevSessionEnabledState(readDevSessionEnabled());
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
    if (!authToken || developmentSessionActive) {
      setAccessTokenRemainingMs(null);
      setRefreshTokenRemainingMs(developmentSessionActive ? null : getTokenRemainingMs(readJwtExpiresAt(readRefreshToken())));
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
  }, [authToken, developmentSessionActive]);

  useEffect(() => {
    if (!authToken) {
      setProfile(null);
      return;
    }
    if (developmentSessionActive) {
      setProfile(null);
      return;
    }

    if (refreshToken && isAccessTokenExpired(5_000)) {
      setRefreshingSession(true);
      void refreshAuthToken(refreshToken)
        .catch((error: unknown) => {
          clearAuthTokens();
          setDevSessionEnabled(false);
          setDevSessionEnabledState(false);
          setProfile(null);
          showToast("warning", "Your session could not be refreshed. Please sign in again.", "auth-refresh-failed");
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: "Your session could not be refreshed. Please sign in again."
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
          clearAuthTokens();
          setDevSessionEnabled(false);
          setDevSessionEnabledState(false);
          setProfile(null);
          showToast("warning", "Your session could not be restored. Please sign in again.", "auth-restore-failed");
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: "Your session could not be restored. Please sign in again."
          });
        }
      });

    return () => {
      active = false;
    };
  }, [authToken, refreshToken, developmentSessionActive]);

  useEffect(() => {
    if (!authToken || !refreshToken || developmentSessionActive) {
      return;
    }

    const triggerRefresh = () => {
      setRefreshingSession(true);
      void refreshAuthToken(refreshToken)
        .catch((error: unknown) => {
          clearAuthTokens();
          setDevSessionEnabled(false);
          setDevSessionEnabledState(false);
          setProfile(null);
          showToast("warning", "Your session has expired. Please sign in again.", "auth-expired");
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: "Your session has expired. Please sign in again."
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
  }, [authToken, refreshToken, developmentSessionActive]);

  useEffect(() => {
    window.localStorage.setItem(ROLE_STORAGE_KEY, devRole);
  }, [devRole]);

  const currentRole = profile ? pickPrimaryRole(profile.roles) : developmentSessionActive ? devRole : DEFAULT_ROLE;
  const sessionSource: SessionSource =
    profile
      ? "server"
      : authRequiredDetail
        ? "expired"
        : authToken
          ? "loading"
          : developmentSessionActive
            ? "dev"
            : "anonymous";

  const sessionHealth: SessionHealth =
    sessionSource === "expired"
      ? "expired"
      : sessionSource === "anonymous"
        ? "anonymous"
        : sessionSource === "dev"
          ? "development"
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
      isAuthenticated: Boolean(profile) || developmentSessionActive,
      authRequiredDetail,
      accessTokenRemainingMs,
      accessTokenExpiresAt,
      accessTokenTokenUse,
      refreshTokenRemainingMs,
      refreshTokenExpiresAt,
      refreshTokenTokenUse,
      tokenLastSyncAt,
      setCurrentRole: (role: AppRole) => setDevRoleState(role),
      setToken: (token: string | null) => {
        setAuthToken(token);
        setAuthTokenState(token);
      },
      setTokens: (accessToken: string | null, nextRefreshToken: string | null, expiresInSeconds?: number | null) => {
        setAuthTokens(accessToken, nextRefreshToken, { expiresInSeconds });
        setDevSessionEnabled(false);
        setAuthTokenState(accessToken);
        setRefreshTokenState(nextRefreshToken);
        setDevSessionEnabledState(false);
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
      enableDevelopmentSession: (role: AppRole, token?: string | null) => {
        setDevRoleState(role);
        setAuthTokens(token?.trim() ? token.trim() : null, null);
        setAuthTokenState(token?.trim() ? token.trim() : null);
        setRefreshTokenState(null);
        setDevSessionEnabled(true);
        setDevSessionEnabledState(true);
        setAccessTokenRemainingMs(null);
        setRefreshTokenRemainingMs(null);
        setAccessTokenExpiresAt(null);
        setRefreshTokenExpiresAt(null);
        setAccessTokenTokenUse(null);
        setRefreshTokenTokenUse(null);
        setTokenLastSyncAt(null);
        setProfile(null);
        setAuthRequiredDetail(null);
      },
      disableDevelopmentSession: () => {
        clearAuthTokens();
        setDevSessionEnabled(false);
        setAuthTokenState(null);
        setRefreshTokenState(null);
        setDevSessionEnabledState(false);
        setAccessTokenRemainingMs(null);
        setRefreshTokenRemainingMs(null);
        setAccessTokenExpiresAt(null);
        setRefreshTokenExpiresAt(null);
        setAccessTokenTokenUse(null);
        setRefreshTokenTokenUse(null);
        setTokenLastSyncAt(null);
        setProfile(null);
      },
      clearAuthRequired: () => setAuthRequiredDetail(null),
      resetRole: () => setDevRoleState(DEFAULT_ROLE),
      refreshSession: async () => {
        if (!refreshToken || developmentSessionActive) {
          showToast("info", "No backend refresh token is available.", "auth-refresh-skipped");
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
          showToast("success", "Session refreshed.", "auth-refresh-success");
        } catch (error) {
          clearAuthTokens();
          setProfile(null);
          setAuthRequiredDetail({
            reason: axios.isAxiosError(error) && error.response?.status === 401 ? "expired" : "unauthorized",
            message: "Your session could not be refreshed. Please sign in again."
          });
          showToast("warning", "Your session could not be refreshed. Please sign in again.", "auth-refresh-failed");
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
            isAuthenticated: Boolean(profile) || developmentSessionActive,
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
        await logoutAuth();
        setDevSessionEnabled(false);
        setAuthTokenState(null);
        setRefreshTokenState(null);
        setDevSessionEnabledState(false);
        setAccessTokenRemainingMs(null);
        setRefreshTokenRemainingMs(null);
        setAccessTokenExpiresAt(null);
        setRefreshTokenExpiresAt(null);
        setAccessTokenTokenUse(null);
        setRefreshTokenTokenUse(null);
        setTokenLastSyncAt(null);
        setProfile(null);
        showToast("info", "You have signed out.", "auth-logout");
        setAuthRequiredDetail({ reason: "logout", message: "You have signed out." });
      }
    }),
    [
      accessTokenExpiresAt,
      accessTokenRemainingMs,
      accessTokenTokenUse,
      authRequiredDetail,
      authToken,
      currentRole,
      developmentSessionActive,
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
