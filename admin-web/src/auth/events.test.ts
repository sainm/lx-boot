import { describe, it, expect, vi, afterEach } from "vitest";
import { dispatchAuthRequired, AUTH_REQUIRED_EVENT } from "./events";
import type { AuthRequiredDetail } from "./events";

describe("dispatchAuthRequired", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("dispatches AUTH_REQUIRED_EVENT with the given detail", () => {
    const received: AuthRequiredDetail[] = [];
    const handler = (e: Event) => {
      received.push((e as CustomEvent<AuthRequiredDetail>).detail);
    };
    window.addEventListener(AUTH_REQUIRED_EVENT, handler);

    dispatchAuthRequired({ reason: "expired", message: "Session expired", from: "/dashboard" });

    window.removeEventListener(AUTH_REQUIRED_EVENT, handler);

    expect(received).toHaveLength(1);
    expect(received[0]).toEqual({
      reason: "expired",
      message: "Session expired",
      from: "/dashboard"
    });
  });

  it("dispatches with reason only (optional fields omitted)", () => {
    const received: AuthRequiredDetail[] = [];
    const handler = (e: Event) => {
      received.push((e as CustomEvent<AuthRequiredDetail>).detail);
    };
    window.addEventListener(AUTH_REQUIRED_EVENT, handler);

    dispatchAuthRequired({ reason: "logout" });

    window.removeEventListener(AUTH_REQUIRED_EVENT, handler);

    expect(received[0].reason).toBe("logout");
    expect(received[0].message).toBeUndefined();
    expect(received[0].from).toBeUndefined();
  });

  it("dispatches with reason unauthorized", () => {
    const received: AuthRequiredDetail[] = [];
    const handler = (e: Event) => {
      received.push((e as CustomEvent<AuthRequiredDetail>).detail);
    };
    window.addEventListener(AUTH_REQUIRED_EVENT, handler);

    dispatchAuthRequired({ reason: "unauthorized", message: "Auth required", from: "/protected" });

    window.removeEventListener(AUTH_REQUIRED_EVENT, handler);

    expect(received[0].reason).toBe("unauthorized");
  });
});
