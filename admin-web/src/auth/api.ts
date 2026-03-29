import axios from "axios";
import { clearAuthTokens, readAuthToken, readRefreshToken, setAuthTokens } from "./token";

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

const authHttp = axios.create({
  timeout: 10000
});

export async function passwordLogin(request: PasswordLoginRequest) {
  const response = await authHttp.post<StarterApiResponse<PasswordLoginResponse>>("/auth/login/password", request);
  const data = response.data.data;
  setAuthTokens(data.accessToken, data.refreshToken, { expiresInSeconds: data.expiresIn });
  return data;
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
