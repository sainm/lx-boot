import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { buildExportFileName, downloadExportFile, getExportFileExtension } from "./api";
import type { DownloadedExportReport, ExportReportResponse } from "./api";

describe("getExportFileExtension", () => {
  it("returns 'pdf' for PDF format", () => {
    expect(getExportFileExtension("PDF")).toBe("pdf");
  });

  it("returns 'txt' for TEXT format", () => {
    expect(getExportFileExtension("TEXT")).toBe("txt");
  });
});

describe("buildExportFileName", () => {
  const base = {
    fileName: "",
    reportId: 1,
    resultId: 2,
    exportFormat: "TEXT",
    downloadExtension: "txt"
  };

  it("uses provided fileName and appends extension when missing", () => {
    const result = buildExportFileName({ ...base, fileName: "my-report" });
    expect(result).toBe("my-report.txt");
  });

  it("does not double-append extension when fileName already ends with it", () => {
    const result = buildExportFileName({ ...base, fileName: "my-report.txt" });
    expect(result).toBe("my-report.txt");
  });

  it("is case-insensitive when checking existing extension", () => {
    const result = buildExportFileName({ ...base, fileName: "My-Report.TXT" });
    expect(result).toBe("My-Report.TXT");
  });

  it("falls back to reportId-based name when fileName is empty", () => {
    const result = buildExportFileName({ ...base, fileName: "", reportId: 42 });
    expect(result).toBe("psy-report-42.txt");
  });

  it("falls back to resultId when reportId is 0", () => {
    const result = buildExportFileName({ ...base, fileName: "", reportId: 0, resultId: 7 });
    expect(result).toBe("psy-report-7.txt");
  });

  it("uses PDF extension for PDF format", () => {
    const result = buildExportFileName({
      ...base,
      fileName: "report",
      exportFormat: "PDF",
      downloadExtension: "pdf"
    });
    expect(result).toBe("report.pdf");
  });

  it("falls back to format-derived extension when downloadExtension is blank", () => {
    const result = buildExportFileName({
      ...base,
      fileName: "report",
      exportFormat: "PDF",
      downloadExtension: "   "
    });
    expect(result).toBe("report.pdf");
  });
});

describe("downloadExportFile", () => {
  beforeEach(() => {
    Object.defineProperty(window.URL, "createObjectURL", {
      configurable: true,
      value: vi.fn()
    });
    Object.defineProperty(window.URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn()
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("triggers download with correct filename for blob-based result", () => {
    const clickSpy = vi.fn();
    const appendSpy = vi.spyOn(document.body, "appendChild").mockImplementation(() => document.body);
    const removeSpy = vi.spyOn(document.body, "removeChild").mockImplementation(() => document.body);
    vi.spyOn(document, "createElement").mockReturnValue({
      href: "",
      download: "",
      style: { display: "" },
      click: clickSpy
    } as unknown as HTMLAnchorElement);
    vi.spyOn(window.URL, "createObjectURL").mockReturnValue("blob:mock-url");
    vi.spyOn(window.URL, "revokeObjectURL").mockImplementation(() => {});

    const report: DownloadedExportReport = {
      exportId: "e1",
      fileName: "report",
      exportFormat: "TEXT",
      downloadExtension: "txt",
      contentType: "text/plain",
      generatedAt: "2026-01-01T00:00:00Z",
      reportId: 1,
      resultId: 2,
      blob: new Blob(["hello"], { type: "text/plain" })
    };

    downloadExportFile(report);

    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(appendSpy).toHaveBeenCalledTimes(1);
    expect(removeSpy).toHaveBeenCalledTimes(1);
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock-url");
  });

  it("decodes BASE64 content and triggers download for non-blob result", () => {
    const clickSpy = vi.fn();
    vi.spyOn(document.body, "appendChild").mockImplementation(() => document.body);
    vi.spyOn(document.body, "removeChild").mockImplementation(() => document.body);
    vi.spyOn(document, "createElement").mockReturnValue({
      href: "",
      download: "",
      style: { display: "" },
      click: clickSpy
    } as unknown as HTMLAnchorElement);
    vi.spyOn(window.URL, "createObjectURL").mockReturnValue("blob:mock-url-2");
    vi.spyOn(window.URL, "revokeObjectURL").mockImplementation(() => {});

    const report: ExportReportResponse = {
      exportId: "e2",
      fileName: "report",
      exportFormat: "TEXT",
      downloadExtension: "txt",
      contentType: "text/plain",
      contentEncoding: "BASE64",
      generatedAt: "2026-01-01T00:00:00Z",
      reportId: 1,
      resultId: 2,
      content: btoa("hello")
    };

    downloadExportFile(report);

    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock-url-2");
  });
});
