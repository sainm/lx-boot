import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../../services/http";
import { downloadScalePackageExport, fetchScalePackage, replaceScalePackage, type UpdateScalePackageRequest } from "./api";

vi.mock("../../services/http", () => ({
  http: { get: vi.fn(), put: vi.fn() }
}));

const response = { data: { data: { scaleId: 17 } } };

describe("scale package API", () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset().mockResolvedValue(response);
    vi.mocked(http.put).mockReset().mockResolvedValue(response);
  });

  it("loads one tenant-scoped scale package", async () => {
    await fetchScalePackage(17);
    expect(http.get).toHaveBeenCalledWith("/scales/17/package");
  });

  it("replaces the complete package atomically", async () => {
    const payload: UpdateScalePackageRequest = {
      governance: null,
      translations: [],
      dimensionTranslations: [],
      questionTranslations: [],
      optionTranslations: [],
      resultRuleTranslations: [],
      highRiskRuleTranslations: [],
      qualityPolicy: null,
      validityRules: [],
      algorithmBinding: null,
      normGovernance: []
    };
    await replaceScalePackage(17, payload);
    expect(http.put).toHaveBeenCalledWith("/scales/17/package", payload);
  });

  it("downloads the versioned package and sanitizes the server filename", async () => {
    const blob = new Blob(["{}"], { type: "application/vnd.psy-scale-package+json" });
    vi.mocked(http.get).mockResolvedValueOnce({
      data: blob,
      headers: {
        "content-type": "application/vnd.psy-scale-package+json",
        "x-export-file-name": "../TEST-v1.json"
      }
    } as never);

    const artifact = await downloadScalePackageExport(17);

    expect(http.get).toHaveBeenCalledWith("/scales/17/package/export", { responseType: "blob" });
    expect(artifact.blob).toBe(blob);
    expect(artifact.fileName).toBe(".._TEST-v1.json");
    expect(artifact.contentType).toBe("application/vnd.psy-scale-package+json");
  });
});
