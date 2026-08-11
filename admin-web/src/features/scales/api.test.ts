import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../../services/http";
import { confirmScalePackageImport, previewScalePackageImport } from "./api";

vi.mock("../../services/http", () => ({
  http: { post: vi.fn() }
}));

describe("controlled scale package import API", () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset();
  });

  it("uploads the package as multipart preview evidence", async () => {
    const file = new File(["{}"], "scale-package.json", { type: "application/json" });
    vi.mocked(http.post).mockResolvedValueOnce({ data: { data: { importId: 99, confirmationSupported: true } } } as never);

    const result = await previewScalePackageImport(file);

    expect(result.importId).toBe(99);
    const [url, body, config] = vi.mocked(http.post).mock.calls[0];
    expect(url).toBe("/scales/imports/package/preview");
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("file")).toBe(file);
    expect(config).toEqual({ headers: { "Content-Type": "multipart/form-data" } });
  });

  it("confirms only the persisted preview id", async () => {
    vi.mocked(http.post).mockResolvedValueOnce({ data: { data: { importId: 99, scaleId: 100, status: "SUCCESS" } } } as never);

    const result = await confirmScalePackageImport(99);

    expect(result.scaleId).toBe(100);
    expect(http.post).toHaveBeenCalledWith("/scales/imports/package/99/confirm");
  });
});
