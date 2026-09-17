import { describe, expect, it } from "vitest";
import { ssoAuthorizeUrl } from "./api";

describe("SSO API", () => {
  it("uses the server-configured callback instead of accepting a client returnTo", () => {
    expect(ssoAuthorizeUrl("oidc")).toBe("/auth/sso/oidc/authorize");
    expect(ssoAuthorizeUrl("cas")).toBe("/auth/sso/cas/authorize");
  });
});
