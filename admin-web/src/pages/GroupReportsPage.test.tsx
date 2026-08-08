import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "../i18n/provider";
import { LOCALE_STORAGE_KEY } from "../i18n/messages";
import { GroupReportsPage } from "./GroupReportsPage";

const mocks = vi.hoisted(() => ({
  fetchGroupReports: vi.fn(),
  submitGroupReportExportJob: vi.fn(),
  pollExportJobStatus: vi.fn(),
  downloadExportJobFile: vi.fn()
}));

vi.mock("../components/ReportCharts", () => ({ ChartRenderer: () => <div data-testid="chart" /> }));
vi.mock("../features/statistics/api", () => ({
  fetchGroupReports: mocks.fetchGroupReports,
  submitGroupReportExportJob: mocks.submitGroupReportExportJob
}));
vi.mock("../features/exports/api", () => ({
  pollExportJobStatus: mocks.pollExportJobStatus,
  downloadExportJobFile: mocks.downloadExportJobFile
}));

describe("GroupReportsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn()
      }))
    });
    const getComputedStyle = window.getComputedStyle.bind(window);
    vi.spyOn(window, "getComputedStyle").mockImplementation((element) => getComputedStyle(element));
    window.localStorage.setItem(LOCALE_STORAGE_KEY, "en-US");
    mocks.fetchGroupReports.mockResolvedValue({
      list: [
        {
          taskId: 10,
          taskName: "Spring Survey",
          scaleId: 2,
          scaleName: "PHQ-9",
          groupId: 20,
          groupName: "Class A",
          anonymousFlag: false,
          suppressedFlag: false,
          memberCount: 30,
          submittedCount: 24,
          completionRate: 80,
          averageScore: 8.5,
          highRiskCount: 2,
          warningCount: 2,
          riskDistribution: [],
          compareUserResult: null,
          dimensionStats: [],
          visualizations: []
        }
      ],
      page: 1,
      size: 20,
      total: 1
    });
    mocks.submitGroupReportExportJob.mockResolvedValue({ jobId: "group-job", status: "PENDING" });
    mocks.pollExportJobStatus.mockResolvedValue({
      jobId: "group-job",
      status: "DONE",
      sourceType: "GROUP_REPORT",
      retryCount: 0,
      fileName: "group-report.csv",
      contentType: "text/csv",
      desensitized: true,
      error: null,
      createdAt: "2026-08-08T00:00:00Z",
      completedAt: "2026-08-08T00:00:01Z"
    });
  });

  it("submits, polls, and downloads a group report export job", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <I18nProvider>
          <GroupReportsPage />
        </I18nProvider>
      </QueryClientProvider>
    );

    expect(await screen.findByText("Spring Survey")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /CSV/i }));

    await waitFor(() => expect(mocks.submitGroupReportExportJob).toHaveBeenCalledWith({
      taskId: 10,
      groupId: 20,
      scaleId: 2,
      compareUserId: undefined,
      startDate: undefined,
      endDate: undefined,
      format: "CSV"
    }));
    await waitFor(() => expect(mocks.pollExportJobStatus).toHaveBeenCalledWith("group-job"));
    await waitFor(() => expect(mocks.downloadExportJobFile).toHaveBeenCalledWith(
      "group-job",
      "group-report.csv",
      "text/csv"
    ));
  });
});
