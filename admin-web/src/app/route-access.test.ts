import { describe, expect, it } from "vitest";
import {
  canRoleAccessPath,
  canRolesAccessPath,
  defaultRouteForRole,
  resolveSafeRedirect,
  resolveSafeRedirectForRoles
} from "./route-access";

describe("defaultRouteForRole", () => {
  it("sends respondents to the user home", () => {
    expect(defaultRouteForRole("USER")).toBe("/home");
  });

  it("sends staff roles to the dashboard", () => {
    expect(defaultRouteForRole("COUNSELOR")).toBe("/dashboard");
    expect(defaultRouteForRole("ASSESSMENT_ADMIN")).toBe("/dashboard");
    expect(defaultRouteForRole("ORG_MANAGER")).toBe("/dashboard");
    expect(defaultRouteForRole("SCHOOL_LEADER")).toBe("/dashboard");
    expect(defaultRouteForRole("SYS_ADMIN")).toBe("/dashboard");
  });
});

describe("canRoleAccessPath", () => {
  it("matches static routes against the route role matrix", () => {
    expect(canRoleAccessPath("ASSESSMENT_ADMIN", "/scales")).toBe(true);
    expect(canRoleAccessPath("USER", "/scales")).toBe(false);
    expect(canRoleAccessPath("SYS_ADMIN", "/user-admin")).toBe(true);
    expect(canRoleAccessPath("COUNSELOR", "/user-admin")).toBe(false);
  });

  it("matches dynamic routes", () => {
    expect(canRoleAccessPath("USER", "/my/tasks/12")).toBe(true);
    expect(canRoleAccessPath("USER", "/reports/4")).toBe(true);
    expect(canRoleAccessPath("COUNSELOR", "/reports/4")).toBe(true);
  });

  it("ignores query strings and hashes while matching", () => {
    expect(canRoleAccessPath("USER", "/reports/4?resultId=4&taskId=6")).toBe(true);
    expect(canRoleAccessPath("ASSESSMENT_ADMIN", "/scales?page=1#top")).toBe(true);
  });

  it("rejects unknown or non-absolute paths", () => {
    expect(canRoleAccessPath("SYS_ADMIN", "/does-not-exist")).toBe(false);
    expect(canRoleAccessPath("SYS_ADMIN", "https://example.com")).toBe(false);
  });
});

describe("multi-role accounts (A3)", () => {
  it("keeps every capability granted by the full role set", () => {
    // ORG_MANAGER alone cannot open the warning queue, but the same account is
    // also a counselor, so the union must allow it.
    expect(canRoleAccessPath("ORG_MANAGER", "/warnings")).toBe(false);
    expect(canRolesAccessPath(["ORG_MANAGER", "COUNSELOR"], "/warnings")).toBe(true);
    expect(canRolesAccessPath(["ORG_MANAGER", "COUNSELOR"], "/scales")).toBe(false);
  });

  it("lets a school leader reach the dashboard and group reports", () => {
    expect(canRolesAccessPath(["SCHOOL_LEADER"], "/dashboard")).toBe(true);
    expect(canRolesAccessPath(["SCHOOL_LEADER"], "/group-reports")).toBe(true);
    expect(canRolesAccessPath(["SCHOOL_LEADER"], "/user-admin")).toBe(false);
  });

  it("falls back to the shell route for the primary role", () => {
    expect(resolveSafeRedirectForRoles("/warnings", ["ORG_MANAGER", "COUNSELOR"])).toBe("/warnings");
    expect(resolveSafeRedirectForRoles("/warnings", ["USER"])).toBe("/home");
  });
});

describe("resolveSafeRedirect", () => {
  it("uses the role default when no source path exists", () => {
    expect(resolveSafeRedirect(undefined, "ASSESSMENT_ADMIN")).toBe("/dashboard");
    expect(resolveSafeRedirect(undefined, "USER")).toBe("/home");
  });

  it("keeps an allowed source path", () => {
    expect(resolveSafeRedirect("/scales", "ASSESSMENT_ADMIN")).toBe("/scales");
    expect(resolveSafeRedirect("/my/tasks/12", "USER")).toBe("/my/tasks/12");
  });

  it("falls back when the source path is not allowed for the role", () => {
    expect(resolveSafeRedirect("/scales", "USER")).toBe("/home");
    expect(resolveSafeRedirect("/home", "ASSESSMENT_ADMIN")).toBe("/dashboard");
    expect(resolveSafeRedirect("/user-admin", "COUNSELOR")).toBe("/dashboard");
  });

  it("never redirects back to the login page or an unsafe target", () => {
    expect(resolveSafeRedirect("/login", "SYS_ADMIN")).toBe("/dashboard");
    expect(resolveSafeRedirect("https://example.com", "SYS_ADMIN")).toBe("/dashboard");
    expect(resolveSafeRedirect("//evil.example.com", "SYS_ADMIN")).toBe("/dashboard");
  });
});
