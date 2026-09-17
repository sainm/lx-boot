import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../../services/http";
import {
  approveExternalRegistration,
  fetchPendingExternalRegistrations,
  rejectExternalRegistration
} from "./api";

vi.mock("../../services/http", () => ({
  http: { get: vi.fn(), post: vi.fn() }
}));

describe("external registration API", () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset().mockResolvedValue({ data: [] });
    vi.mocked(http.post).mockReset().mockResolvedValue({ data: {} });
  });

  it("uses paths relative to the shared api-v1 base URL", async () => {
    await fetchPendingExternalRegistrations();
    await approveExternalRegistration(17);
    await rejectExternalRegistration(18);

    expect(http.get).toHaveBeenCalledWith("/admin/external-registrations/pending");
    expect(http.post).toHaveBeenCalledWith("/admin/external-registrations/17/approve");
    expect(http.post).toHaveBeenCalledWith("/admin/external-registrations/18/reject");
  });
});
