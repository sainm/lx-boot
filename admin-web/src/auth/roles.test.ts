import { describe, expect, it } from "vitest";
import { APP_ROLE_OPTIONS, canAccess, DEFAULT_ROLE, isAppRole, ROLE_LABELS } from "./roles";
import type { AppRole } from "./roles";

describe("isAppRole", () => {
  it("returns true for all valid AppRole values", () => {
    const valid: string[] = ["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"];
    valid.forEach((role) => {
      expect(isAppRole(role)).toBe(true);
    });
  });

  it("returns false for an unknown string", () => {
    expect(isAppRole("UNKNOWN_ROLE")).toBe(false);
  });

  it("returns false for empty string", () => {
    expect(isAppRole("")).toBe(false);
  });

  it("returns false for null", () => {
    expect(isAppRole(null)).toBe(false);
  });

  it("returns false for undefined", () => {
    expect(isAppRole(undefined)).toBe(false);
  });

  it("is case-sensitive", () => {
    expect(isAppRole("counselor")).toBe(false);
  });
});

describe("canAccess", () => {
  it("returns true when currentRole is in allowedRoles", () => {
    expect(canAccess(["SYS_ADMIN", "ASSESSMENT_ADMIN"], "SYS_ADMIN")).toBe(true);
  });

  it("returns false when currentRole is not in allowedRoles", () => {
    expect(canAccess(["SYS_ADMIN", "ASSESSMENT_ADMIN"], "COUNSELOR")).toBe(false);
  });

  it("returns false for empty allowedRoles list", () => {
    expect(canAccess([], "SYS_ADMIN")).toBe(false);
  });

  it("returns true for single-element list that matches", () => {
    expect(canAccess(["COUNSELOR"], "COUNSELOR")).toBe(true);
  });
});

describe("ROLE_LABELS", () => {
  it("has a label for every AppRole key", () => {
    const roles: AppRole[] = ["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"];
    roles.forEach((role) => {
      expect(ROLE_LABELS[role]).toBeTruthy();
    });
  });

  it("USER label has an English fallback", () => {
    expect(ROLE_LABELS.USER).toBe("Respondent");
  });
});

describe("APP_ROLE_OPTIONS", () => {
  it("each option has a non-empty label and value", () => {
    APP_ROLE_OPTIONS.forEach(({ label, value }) => {
      expect(label.length).toBeGreaterThan(0);
      expect(isAppRole(value)).toBe(true);
    });
  });

  it("does not include USER role for admin-only options", () => {
    const values = APP_ROLE_OPTIONS.map((option) => option.value);
    expect(values).not.toContain("USER");
  });
});

describe("DEFAULT_ROLE", () => {
  it("is a valid AppRole", () => {
    expect(isAppRole(DEFAULT_ROLE)).toBe(true);
  });
});
