import { afterEach, describe, expect, it, vi } from "vitest";
import { authHttp, refreshAuthTokenOnce, ssoAuthorizeUrl } from "./api";

describe("SSO API", () => {
  it("uses the server-configured callback instead of accepting a client returnTo", () => {
    expect(ssoAuthorizeUrl("oidc")).toBe("/auth/sso/oidc/authorize");
    expect(ssoAuthorizeUrl("cas")).toBe("/auth/sso/cas/authorize");
  });
});

describe("refreshAuthTokenOnce", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("deduplicates concurrent refresh requests", async () => {
    const post = vi.spyOn(authHttp, "post").mockResolvedValue({
      data: {
        data: {
          accessToken: "access-token",
          refreshToken: "rotated-refresh-token",
          tokenType: "Bearer",
          expiresIn: 3600
        }
      }
    } as never);

    const [first, second] = await Promise.all([
      refreshAuthTokenOnce("refresh-token-1"),
      refreshAuthTokenOnce("refresh-token-1")
    ]);

    expect(post).toHaveBeenCalledTimes(1);
    expect(first).toEqual(second);
    expect(first.accessToken).toBe("access-token");
  });
});
