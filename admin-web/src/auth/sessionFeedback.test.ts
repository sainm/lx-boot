import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ showToast: vi.fn() }));

vi.mock("../feedback/toast", () => ({ showToast: mocks.showToast }));

import { isLoginRoute, showAuthIssueToast } from "./sessionFeedback";

describe("sessionFeedback", () => {
  beforeEach(() => {
    mocks.showToast.mockReset();
    window.history.pushState({}, "", "/");
  });

  it("recognizes the login route", () => {
    window.history.pushState({}, "", "/login");
    expect(isLoginRoute()).toBe(true);

    window.history.pushState({}, "", "/login?from=%2Fscales");
    expect(isLoginRoute()).toBe(true);

    window.history.pushState({}, "", "/dashboard");
    expect(isLoginRoute()).toBe(false);
  });

  it("suppresses authentication toasts on the login route", () => {
    window.history.pushState({}, "", "/login");
    showAuthIssueToast("Authentication is required.");
    expect(mocks.showToast).not.toHaveBeenCalled();
  });

  it("uses a single dedupe key outside the login route", () => {
    window.history.pushState({}, "", "/dashboard");
    showAuthIssueToast("Your session could not be refreshed.");
    showAuthIssueToast("Your session has expired.");

    expect(mocks.showToast).toHaveBeenNthCalledWith(
      1,
      "warning",
      "Your session could not be refreshed.",
      "auth-required"
    );
    expect(mocks.showToast).toHaveBeenNthCalledWith(
      2,
      "warning",
      "Your session has expired.",
      "auth-required"
    );
  });
});
