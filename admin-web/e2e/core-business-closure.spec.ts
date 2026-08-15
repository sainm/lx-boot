import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const TEST_PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";
const BACKEND_URL = process.env.PSY_E2E_BACKEND_URL ?? "http://127.0.0.1:8090";
const OBSERVABILITY_TRACE_ID = "11111111111111111111111111111111";
const OBSERVABILITY_TRACEPARENT = `00-${OBSERVABILITY_TRACE_ID}-2222222222222222-01`;

type LoginData = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
};

type PageData<T> = {
  list: T[];
  total: number;
};

type ScaleSummary = {
  id: number;
  scaleCode: string;
};

type UserSummary = {
  userId: number;
  username: string;
};

type TenantSummary = {
  tenantId: number;
  tenantCode: string;
};

type QuestionPayload = {
  taskId: number;
  scaleId: number;
  questions: Array<{
    questionId: number;
    options: Array<{ optionId: number; optionCode: string }>;
  }>;
};

type SubmitResult = {
  answerSheetId: number;
  resultId: number;
  reportId: number;
  riskLevel: string;
};

type WarningSummary = {
  id: number;
  resultId: number;
  status: string;
};

type NotificationSummary = {
  id: number;
  notificationType: string;
  bizType?: string | null;
  bizId?: number | null;
  readFlag: boolean;
};

type NotificationOpsItem = {
  id: number;
  notificationType: string;
  bizType?: string | null;
  bizId?: number | null;
  totalDeliveries: number;
};

type NotificationDelivery = {
  id: number;
  notificationId: number;
  deliveryChannel: string;
  deliveryStatus: string;
  errorMessage?: string | null;
  callbackPayloadJson?: string | null;
};

type ExportJobStatus = {
  jobId: string;
  status: string;
  reportId?: number | null;
  retryCount?: number;
  fileSize?: number | null;
};

async function login(request: APIRequestContext, principal: string): Promise<LoginData> {
  const response = await request.post("/auth/login/password", {
    data: {
      principal,
      password: TEST_PASSWORD,
      deviceId: `playwright-core-${principal}`,
      deviceType: "WEB",
      deviceName: "Core business closure E2E"
    },
    headers: { "Accept-Language": "en-US" }
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json() as ApiEnvelope<LoginData>).data;
}

function authHeaders(loginData: LoginData, locale = "en-US") {
  return {
    Authorization: `Bearer ${loginData.accessToken}`,
    "Accept-Language": locale
  };
}

async function installBrowserSession(page: Page, loginData: LoginData, locale = "en-US") {
  await page.goto("/login");
  await page.evaluate(({ accessToken, refreshToken, expiresIn, localeCode }) => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    window.localStorage.setItem("psy-admin-web.auth-token", accessToken);
    window.localStorage.setItem("psy-admin-web.refresh-token", refreshToken);
    window.localStorage.setItem("psy-admin-web.access-token-expires-at", String(Date.now() + expiresIn * 1000));
    window.localStorage.setItem("psy-admin-web.locale", localeCode);
  }, {
    accessToken: loginData.accessToken,
    refreshToken: loginData.refreshToken,
    expiresIn: loginData.expiresIn,
    localeCode: locale
  });
}

async function findUserId(request: APIRequestContext, administrator: LoginData, username: string) {
  const response = await request.get(`/api/v1/user-admin/users?username=${encodeURIComponent(username)}&page=1&size=20`, {
    headers: authHeaders(administrator)
  });
  expect(response.status(), await response.text()).toBe(200);
  const users = (await response.json() as ApiEnvelope<PageData<UserSummary>>).data.list;
  const user = users.find((item) => item.username === username);
  expect(user, `Missing E2E fixture user ${username}`).toBeDefined();
  return user!.userId;
}

test("task submission creates one report and warning that closes only with intervention evidence", async ({ page, request }) => {
  const timeoutMs = Number(process.env.PSY_E2E_CORE_TEST_TIMEOUT_MS ?? "90000");
  test.setTimeout(Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 90_000);
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  const administrator = await login(request, "e2e_admin");
  const respondent = await login(request, "respondent");
  const counselor = await login(request, "counselor");
  const campusAdministrator = await login(request, "campus_assessor");
  const globalAdministrator = await login(request, "global_admin");
  const tenantlessAssessor = await login(request, "tenantless_assessor");

  const anonymousMetricsResponse = await request.get(`${BACKEND_URL}/actuator/prometheus`);
  expect(anonymousMetricsResponse.status()).toBe(401);

  const correlationId = "e2e-observability-core";

  const globalTenantsResponse = await request.get("/api/v1/user-admin/tenants", {
    headers: { ...authHeaders(globalAdministrator), "X-Correlation-Id": correlationId }
  });
  expect(globalTenantsResponse.status(), await globalTenantsResponse.text()).toBe(200);
  expect(globalTenantsResponse.headers()["x-correlation-id"]).toBe(correlationId);
  const globalTenants = (await globalTenantsResponse.json() as ApiEnvelope<TenantSummary[]>).data;
  expect(globalTenants.map((item) => item.tenantCode)).toEqual(expect.arrayContaining(["DEFAULT", "CAMPUS_DEMO"]));
  const campusTenantId = globalTenants.find((item) => item.tenantCode === "CAMPUS_DEMO")!.tenantId;

  const tenantAdminTenantsResponse = await request.get("/api/v1/user-admin/tenants", {
    headers: authHeaders(administrator)
  });
  expect(tenantAdminTenantsResponse.status(), await tenantAdminTenantsResponse.text()).toBe(200);
  expect((await tenantAdminTenantsResponse.json() as ApiEnvelope<TenantSummary[]>).data.map((item) => item.tenantCode))
    .toEqual(["DEFAULT"]);

  const tenantScopeSpoof = await request.get(
    `/api/v1/user-admin/users?tenantId=${campusTenantId}&username=campus_assessor&page=1&size=20`,
    { headers: authHeaders(administrator) }
  );
  expect(tenantScopeSpoof.status(), await tenantScopeSpoof.text()).toBe(200);
  expect((await tenantScopeSpoof.json() as ApiEnvelope<PageData<UserSummary>>).data.list).toEqual([]);

  const globalCampusUsers = await request.get(
    `/api/v1/user-admin/users?tenantId=${campusTenantId}&username=campus_assessor&page=1&size=20`,
    { headers: authHeaders(globalAdministrator) }
  );
  expect(globalCampusUsers.status(), await globalCampusUsers.text()).toBe(200);
  expect((await globalCampusUsers.json() as ApiEnvelope<PageData<UserSummary>>).data.list)
    .toContainEqual(expect.objectContaining({ username: "campus_assessor" }));

  const tenantlessExternalReview = await request.get("/api/v1/admin/external-registrations/pending", {
    headers: authHeaders(tenantlessAssessor)
  });
  expect(tenantlessExternalReview.status()).toBe(400);
  expect((await tenantlessExternalReview.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const respondentId = await findUserId(request, administrator, "respondent");

  const tenantlessScaleList = await request.get("/api/v1/scales?page=1&size=20", {
    headers: {
      ...authHeaders(tenantlessAssessor),
      "X-Correlation-Id": "e2e-observability-error",
      traceparent: OBSERVABILITY_TRACEPARENT
    }
  });
  expect(tenantlessScaleList.status()).toBe(400);
  expect(tenantlessScaleList.headers()["x-correlation-id"]).toBe("e2e-observability-error");
  expect((await tenantlessScaleList.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const globalScaleList = await request.get(
    "/api/v1/scales?scaleName=E2E%20Core%20Risk%20Technical%20Fixture&status=PUBLISHED&page=1&size=20",
    { headers: authHeaders(globalAdministrator) }
  );
  expect(globalScaleList.status(), await globalScaleList.text()).toBe(200);
  expect((await globalScaleList.json() as ApiEnvelope<PageData<ScaleSummary>>).data.list)
    .toContainEqual(expect.objectContaining({ scaleCode: "E2E_CORE_TECH_FIXTURE" }));

  const scalesResponse = await request.get(
    "/api/v1/scales?scaleName=E2E%20Core%20Risk%20Technical%20Fixture&status=PUBLISHED&page=1&size=20",
    { headers: authHeaders(administrator) }
  );
  expect(scalesResponse.status(), await scalesResponse.text()).toBe(200);
  const scales = (await scalesResponse.json() as ApiEnvelope<PageData<ScaleSummary>>).data.list;
  const scale = scales.find((item) => item.scaleCode === "E2E_CORE_TECH_FIXTURE");
  expect(scale).toBeDefined();

  const crossTenantScale = await request.get(`/api/v1/scales/${scale!.id}`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantScale.status()).toBe(404);
  expect((await crossTenantScale.json() as ApiEnvelope<null>).code).toBe("SCALE_NOT_FOUND");

  const globalScale = await request.get(`/api/v1/scales/${scale!.id}`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalScale.status(), await globalScale.text()).toBe(200);

  const uniqueSuffix = `${Date.now()}-${test.info().workerIndex}`;
  const taskName = `E2E Core Closure ${uniqueSuffix}`;
  const taskResponse = await request.post("/api/v1/tasks", {
    headers: authHeaders(administrator),
    data: {
      taskName,
      scaleId: scale!.id,
      taskMode: "SCREENING",
      anonymousFlag: false,
      allowSaveFlag: true,
      allowTimeoutSubmitFlag: false,
      allowRetakeFlag: false,
      startTime: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().slice(0, 19),
      endTime: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString().slice(0, 19)
    }
  });
  expect(taskResponse.status(), await taskResponse.text()).toBe(200);
  const taskId = (await taskResponse.json() as ApiEnvelope<{ id: number }>).data.id;

  const assignmentResponse = await request.post(`/api/v1/tasks/${taskId}/assign-users`, {
    headers: authHeaders(administrator),
    data: { userIds: [respondentId] }
  });
  expect(assignmentResponse.status(), await assignmentResponse.text()).toBe(200);

  const crossTenantTask = await request.get(`/api/v1/tasks/${taskId}`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantTask.status()).toBe(404);
  expect((await crossTenantTask.json() as ApiEnvelope<null>).code).toBe("TASK_NOT_FOUND");

  const globalTask = await request.get(`/api/v1/tasks/${taskId}`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalTask.status(), await globalTask.text()).toBe(200);

  const questionsResponse = await request.get(`/api/v1/my/tasks/${taskId}/questions`, {
    headers: authHeaders(respondent)
  });
  expect(questionsResponse.status(), await questionsResponse.text()).toBe(200);
  const questionPayload = (await questionsResponse.json() as ApiEnvelope<QuestionPayload>).data;
  expect(questionPayload.questions).toHaveLength(3);

  await installBrowserSession(page, respondent);
  await page.goto(`/my/tasks/${taskId}`);
  await expect(page.getByRole("heading", { name: "E2E Core Risk Technical Fixture" })).toBeVisible();

  await page.getByRole("radio", { name: "D. Technical high" }).check();
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByText("Draft saved")).toBeVisible();

  await page.reload();
  await expect(page.getByRole("radio", { name: "D. Technical high" })).toBeChecked();
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("radio", { name: "D. Technical high" }).check();
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("radio", { name: "D. Technical high" }).check();
  await page.getByRole("button", { name: "Review Answers" }).click();
  await expect(page.getByText("Confirm Before Submit", { exact: true })).toBeVisible();
  await expect(page.getByText("3 answered")).toBeVisible();

  const submitResponsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/api/v1/answer-sheets/submit")
  );
  await page.getByRole("button", { name: "Submit", exact: true }).click();
  const browserSubmitResponse = await submitResponsePromise;
  expect(browserSubmitResponse.status(), await browserSubmitResponse.text()).toBe(200);
  const submitResult = (await browserSubmitResponse.json() as ApiEnvelope<SubmitResult>).data;
  const submittedPayload = browserSubmitResponse.request().postDataJSON() as Record<string, unknown>;
  expect(submitResult.riskLevel).toBe("HIGH");
  await expect(page).toHaveURL(new RegExp(`/reports/${submitResult.reportId}`));
  await expect(page.getByRole("heading", { name: "My Report" })).toBeVisible();
  await expect(page.getByText("This assessment is a mental health screening tool and does not constitute a clinical diagnosis.", { exact: false })).toBeVisible();

  const repeatedSubmit = await request.post("/api/v1/answer-sheets/submit", {
    headers: authHeaders(respondent),
    data: submittedPayload
  });
  expect(repeatedSubmit.status(), await repeatedSubmit.text()).toBe(200);
  const repeatedResult = (await repeatedSubmit.json() as ApiEnvelope<SubmitResult>).data;
  expect(repeatedResult.resultId).toBe(submitResult.resultId);
  expect(repeatedResult.reportId).toBe(submitResult.reportId);

  const tenantlessRescore = await request.post(`/api/v1/results/${submitResult.resultId}/rescore`, {
    headers: authHeaders(tenantlessAssessor)
  });
  expect(tenantlessRescore.status()).toBe(400);
  expect((await tenantlessRescore.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const crossTenantRescore = await request.post(`/api/v1/results/${submitResult.resultId}/rescore`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantRescore.status()).toBe(404);
  expect((await crossTenantRescore.json() as ApiEnvelope<null>).code).toBe("RESULT_NOT_FOUND");

  const globalRescore = await request.post("/api/v1/results/990003/rescore", {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalRescore.status(), await globalRescore.text()).toBe(200);
  const globalRescoreData = (await globalRescore.json() as ApiEnvelope<{
    resultId: number;
    reportId: number;
    previousResultId?: number | null;
    calculationVersion?: number | null;
  }>).data;
  expect(globalRescoreData.previousResultId).toBe(990003);
  expect(globalRescoreData.resultId).not.toBe(990003);
  expect(globalRescoreData.reportId).toBeGreaterThan(0);
  expect(globalRescoreData.calculationVersion).toBe(2);

  const warningsResponse = await request.get("/api/v1/warnings?page=1&size=200", {
    headers: authHeaders(counselor)
  });
  expect(warningsResponse.status(), await warningsResponse.text()).toBe(200);
  const warnings = (await warningsResponse.json() as ApiEnvelope<PageData<WarningSummary>>).data.list;
  const warning = warnings.find((item) => item.resultId === submitResult.resultId);
  expect(warning).toBeDefined();
  expect(warning!.status).toBe("PENDING");

  const campusWarningsResponse = await request.get("/api/v1/warnings?page=1&size=200", {
    headers: authHeaders(campusAdministrator)
  });
  expect(campusWarningsResponse.status(), await campusWarningsResponse.text()).toBe(200);
  expect((await campusWarningsResponse.json() as ApiEnvelope<PageData<WarningSummary>>).data.list)
    .not.toContainEqual(expect.objectContaining({ id: warning!.id }));

  const globalWarningsResponse = await request.get("/api/v1/warnings?page=1&size=200", {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalWarningsResponse.status(), await globalWarningsResponse.text()).toBe(200);
  expect((await globalWarningsResponse.json() as ApiEnvelope<PageData<WarningSummary>>).data.list)
    .toContainEqual(expect.objectContaining({ id: warning!.id }));

  const tenantlessIntervention = await request.post("/api/v1/interventions", {
    headers: authHeaders(tenantlessAssessor),
    data: { warningId: warning!.id, planText: "Tenantless access must be rejected before mutation." }
  });
  expect(tenantlessIntervention.status()).toBe(400);
  expect((await tenantlessIntervention.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const crossTenantIntervention = await request.post("/api/v1/interventions", {
    headers: authHeaders(campusAdministrator),
    data: { warningId: warning!.id, planText: "Cross-tenant access must not reveal the warning." }
  });
  expect(crossTenantIntervention.status()).toBe(404);
  expect((await crossTenantIntervention.json() as ApiEnvelope<null>).code).toBe("WARNING_NOT_FOUND");

  const globalClaimResponse = await request.post(`/api/v1/warnings/${warning!.id}/claim`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalClaimResponse.status()).toBe(400);
  expect((await globalClaimResponse.json() as ApiEnvelope<null>).code).toBe("WARNING_ASSIGNEE_FORBIDDEN");

  const crossTenantReport = await request.get(`/api/v1/reports/${submitResult.reportId}`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantReport.status()).toBe(404);
  expect((await crossTenantReport.json() as ApiEnvelope<null>).code).toBe("REPORT_NOT_FOUND");

  const globalReport = await request.get(`/api/v1/reports/${submitResult.reportId}`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalReport.status(), await globalReport.text()).toBe(200);

  const tenantlessExport = await request.post("/api/v1/exports/reports", {
    headers: authHeaders(tenantlessAssessor),
    data: { reportId: submitResult.reportId, exportFormat: "TEXT", desensitized: true }
  });
  expect(tenantlessExport.status()).toBe(400);
  expect((await tenantlessExport.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const crossTenantExport = await request.post("/api/v1/exports/reports", {
    headers: authHeaders(campusAdministrator),
    data: { reportId: submitResult.reportId, exportFormat: "TEXT", desensitized: true }
  });
  expect(crossTenantExport.status()).toBe(404);
  expect((await crossTenantExport.json() as ApiEnvelope<null>).code).toBe("REPORT_NOT_FOUND");

  const globalExport = await request.post("/api/v1/exports/reports", {
    headers: authHeaders(globalAdministrator),
    data: { reportId: submitResult.reportId, exportFormat: "TEXT", desensitized: true }
  });
  expect(globalExport.status(), await globalExport.text()).toBe(200);
  expect((await globalExport.json() as ApiEnvelope<{ reportId: number }>).data.reportId).toBe(submitResult.reportId);

  const submitExportJob = await request.post("/api/v1/exports/reports/jobs", {
    headers: authHeaders(administrator),
    data: { reportId: submitResult.reportId, exportFormat: "TEXT", desensitized: true }
  });
  expect(submitExportJob.status(), await submitExportJob.text()).toBe(200);
  const exportJobId = (await submitExportJob.json() as ApiEnvelope<ExportJobStatus>).data.jobId;

  const crossTenantExportJob = await request.get(`/api/v1/exports/reports/jobs/${exportJobId}`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantExportJob.status()).toBe(404);
  expect((await crossTenantExportJob.json() as ApiEnvelope<null>).code).toBe("JOB_NOT_FOUND");

  const globalExportJob = await request.get(`/api/v1/exports/reports/jobs/${exportJobId}`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalExportJob.status(), await globalExportJob.text()).toBe(200);
  expect((await globalExportJob.json() as ApiEnvelope<ExportJobStatus>).data.reportId).toBe(submitResult.reportId);

  await expect.poll(async () => {
    const response = await request.get(`/api/v1/exports/reports/jobs/${exportJobId}`, {
      headers: authHeaders(administrator)
    });
    if (response.status() !== 200) return `HTTP_${response.status()}`;
    return (await response.json() as ApiEnvelope<ExportJobStatus>).data.status;
  }, { timeout: 15_000 }).toBe("DONE");

  const crossTenantExportDownload = await request.get(`/api/v1/exports/reports/jobs/${exportJobId}/download`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantExportDownload.status()).toBe(404);
  expect((await crossTenantExportDownload.json() as ApiEnvelope<null>).code).toBe("JOB_NOT_FOUND");

  const exportDownload = await request.get(`/api/v1/exports/reports/jobs/${exportJobId}/download`, {
    headers: authHeaders(administrator)
  });
  expect(exportDownload.status(), await exportDownload.text()).toBe(200);
  expect((await exportDownload.body()).length).toBeGreaterThan(0);

  const crossTenantReplay = await request.post("/api/v1/exports/reports/jobs/e2e-dead-export-default/retry", {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantReplay.status()).toBe(404);
  expect((await crossTenantReplay.json() as ApiEnvelope<null>).code).toBe("JOB_NOT_FOUND");

  const replayedExport = await request.post("/api/v1/exports/reports/jobs/e2e-dead-export-default/retry", {
    headers: authHeaders(administrator)
  });
  expect(replayedExport.status(), await replayedExport.text()).toBe(200);
  expect((await replayedExport.json() as ApiEnvelope<ExportJobStatus>).data.status).toBe("PENDING");

  await expect.poll(async () => {
    const response = await request.get("/api/v1/exports/reports/jobs/e2e-dead-export-default", {
      headers: authHeaders(administrator)
    });
    if (response.status() !== 200) return `HTTP_${response.status()}`;
    const job = (await response.json() as ApiEnvelope<ExportJobStatus>).data;
    return job.status === "DONE" ? `${job.status}:${job.retryCount}` : job.status;
  }, { timeout: 15_000 }).toBe("DONE:0");

  const crossTenantReplayedDownload = await request.get(
    "/api/v1/exports/reports/jobs/e2e-dead-export-default/download",
    { headers: authHeaders(campusAdministrator) }
  );
  expect(crossTenantReplayedDownload.status()).toBe(404);
  const replayedDownload = await request.get(
    "/api/v1/exports/reports/jobs/e2e-dead-export-default/download",
    { headers: authHeaders(administrator) }
  );
  expect(replayedDownload.status(), await replayedDownload.text()).toBe(200);
  expect((await replayedDownload.body()).length).toBeGreaterThan(0);

  const tenantlessExportJobs = await request.get("/api/v1/exports/reports/jobs?limit=100", {
    headers: authHeaders(tenantlessAssessor)
  });
  expect(tenantlessExportJobs.status()).toBe(400);
  expect((await tenantlessExportJobs.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const campusExportJobs = await request.get("/api/v1/exports/reports/jobs?limit=100", {
    headers: authHeaders(campusAdministrator)
  });
  expect(campusExportJobs.status(), await campusExportJobs.text()).toBe(200);
  expect((await campusExportJobs.json() as ApiEnvelope<ExportJobStatus[]>).data.map((job) => job.jobId))
    .not.toContain(exportJobId);

  const respondentNotifications = await request.get("/api/v1/my/notifications", {
    headers: authHeaders(respondent)
  });
  expect(respondentNotifications.status(), await respondentNotifications.text()).toBe(200);
  const reportNotification = (await respondentNotifications.json() as ApiEnvelope<NotificationSummary[]>).data
    .find((item) => item.notificationType === "REPORT_GENERATED" && item.bizId === submitResult.reportId);
  expect(reportNotification).toBeDefined();

  const sameTenantOtherUserRead = await request.post(`/api/v1/my/notifications/${reportNotification!.id}/read`, {
    headers: authHeaders(counselor)
  });
  expect(sameTenantOtherUserRead.status()).toBe(404);
  expect((await sameTenantOtherUserRead.json() as ApiEnvelope<null>).code).toBe("NOTIFICATION_NOT_FOUND");

  const ownerRead = await request.post(`/api/v1/my/notifications/${reportNotification!.id}/read`, {
    headers: authHeaders(respondent)
  });
  expect(ownerRead.status(), await ownerRead.text()).toBe(200);
  expect((await ownerRead.json() as ApiEnvelope<{ notificationId: number; readFlag: boolean }>).data)
    .toEqual(expect.objectContaining({ notificationId: reportNotification!.id, readFlag: true }));

  const tenantNotificationFeed = await request.get(
    "/api/v1/notifications/ops/feed?notificationType=REPORT_GENERATED&bizType=REPORT&limit=100",
    { headers: authHeaders(administrator) }
  );
  expect(tenantNotificationFeed.status(), await tenantNotificationFeed.text()).toBe(200);
  const opsNotification = (await tenantNotificationFeed.json() as ApiEnvelope<NotificationOpsItem[]>).data
    .find((item) => item.bizId === submitResult.reportId);
  expect(opsNotification).toBeDefined();
  expect(opsNotification!.totalDeliveries).toBeGreaterThan(0);

  const tenantlessNotificationFeed = await request.get("/api/v1/notifications/ops/feed?limit=100", {
    headers: authHeaders(tenantlessAssessor)
  });
  expect(tenantlessNotificationFeed.status()).toBe(400);
  expect((await tenantlessNotificationFeed.json() as ApiEnvelope<null>).code).toBe("TENANT_CONTEXT_REQUIRED");

  const campusNotificationFeed = await request.get("/api/v1/notifications/ops/feed?limit=100", {
    headers: authHeaders(campusAdministrator)
  });
  expect(campusNotificationFeed.status(), await campusNotificationFeed.text()).toBe(200);
  expect((await campusNotificationFeed.json() as ApiEnvelope<NotificationOpsItem[]>).data.map((item) => item.id))
    .not.toContain(opsNotification!.id);

  const globalNotificationFeed = await request.get("/api/v1/notifications/ops/feed?limit=100", {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalNotificationFeed.status(), await globalNotificationFeed.text()).toBe(200);
  expect((await globalNotificationFeed.json() as ApiEnvelope<NotificationOpsItem[]>).data.map((item) => item.id))
    .toContain(opsNotification!.id);

  const crossTenantDeliveries = await request.get(`/api/v1/notifications/${opsNotification!.id}/deliveries`, {
    headers: authHeaders(campusAdministrator)
  });
  expect(crossTenantDeliveries.status(), await crossTenantDeliveries.text()).toBe(200);
  expect((await crossTenantDeliveries.json() as ApiEnvelope<unknown[]>).data).toEqual([]);

  const globalDeliveries = await request.get(`/api/v1/notifications/${opsNotification!.id}/deliveries`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(globalDeliveries.status(), await globalDeliveries.text()).toBe(200);
  expect((await globalDeliveries.json() as ApiEnvelope<NotificationDelivery[]>).data.length).toBeGreaterThan(0);

  await expect.poll(async () => {
    const response = await request.get(`/api/v1/notifications/${opsNotification!.id}/deliveries`, {
      headers: authHeaders(administrator)
    });
    if (response.status() !== 200) return `HTTP_${response.status()}`;
    const deliveries = (await response.json() as ApiEnvelope<NotificationDelivery[]>).data;
    return deliveries.find((delivery) => delivery.deliveryChannel === "PUSH")?.deliveryStatus ?? "MISSING";
  }, { timeout: 15_000 }).toBe("SENT");

  const tenantDeliveriesResponse = await request.get(`/api/v1/notifications/${opsNotification!.id}/deliveries`, {
    headers: authHeaders(administrator)
  });
  const pushDelivery = (await tenantDeliveriesResponse.json() as ApiEnvelope<NotificationDelivery[]>).data
    .find((delivery) => delivery.deliveryChannel === "PUSH");
  expect(pushDelivery).toBeDefined();

  const crossTenantCallback = await request.post(`/api/v1/notifications/deliveries/${pushDelivery!.id}/callbacks`, {
    headers: authHeaders(campusAdministrator),
    data: { deliveryStatus: "FAILED", providerName: "e2e-provider" }
  });
  expect(crossTenantCallback.status()).toBe(404);
  expect((await crossTenantCallback.json() as ApiEnvelope<null>).code).toBe("NOTIFICATION_NOT_FOUND");

  const failedCallback = await request.post(`/api/v1/notifications/deliveries/${pushDelivery!.id}/callbacks`, {
    headers: authHeaders(administrator),
    data: {
      deliveryStatus: "FAILED",
      providerName: "e2e-provider",
      providerMessageId: "e2e-message",
      errorMessage: "Authorization: Bearer e2e-secret-token",
      callbackPayloadJson: JSON.stringify({ token: "e2e-secret-token", reason: "technical failure" })
    }
  });
  expect(failedCallback.status(), await failedCallback.text()).toBe(200);
  expect((await failedCallback.json() as ApiEnvelope<{ deliveryStatus: string }>).data.deliveryStatus).toBe("FAILED");

  const crossTenantRetry = await request.post("/api/v1/notifications/deliveries/retry-batch", {
    headers: authHeaders(campusAdministrator),
    data: { notificationIds: [opsNotification!.id], deliveryChannel: "PUSH" }
  });
  expect(crossTenantRetry.status(), await crossTenantRetry.text()).toBe(200);
  expect((await crossTenantRetry.json() as ApiEnvelope<{ retriedCount: number }>).data.retriedCount).toBe(0);

  const tenantRetry = await request.post("/api/v1/notifications/deliveries/retry-batch", {
    headers: authHeaders(administrator),
    data: { notificationIds: [opsNotification!.id], deliveryChannel: "PUSH" }
  });
  expect(tenantRetry.status(), await tenantRetry.text()).toBe(200);
  expect((await tenantRetry.json() as ApiEnvelope<{ retriedCount: number }>).data.retriedCount).toBe(1);

  await installBrowserSession(page, counselor);
  await page.goto("/warnings");
  await expect(page.getByRole("heading", { name: "Warnings" })).toBeVisible();
  const warningRow = page.locator("tbody tr").filter({
    has: page.getByText(String(warning!.id), { exact: true })
  }).first();
  await expect(warningRow).toBeVisible();
  await warningRow.getByRole("button", { name: "Intervention Record" }).click();

  const interventionDialog = page.getByRole("dialog", { name: "Intervention Record / Close Case" });
  await expect(interventionDialog).toBeVisible();
  await interventionDialog.getByLabel("Contact channel").click();
  await page.getByRole("option", { name: "Phone" }).click();
  await interventionDialog.getByLabel("Contact outcome").fill("Reached the E2E respondent and confirmed the technical test contact.");
  await interventionDialog.getByLabel("Safety-assessment summary").fill("Technical E2E evidence: no imminent danger; continue controlled follow-up.");
  await interventionDialog.getByLabel("Responsible-handoff record").fill("E2E counselor retains responsibility until the scheduled follow-up.");
  const followUpInput = interventionDialog.getByLabel("Next follow-up time");
  await followUpInput.fill("2099-12-31 10:00:00");
  await followUpInput.press("Enter");
  await interventionDialog.getByLabel("Intervention Plan").fill("Record the technical contact, safety assessment, handoff, and follow-up evidence.");
  await interventionDialog.getByRole("tab", { name: "Close Case" }).click();
  await interventionDialog.getByLabel("Close Summary").fill("Technical E2E workflow completed with all mandatory closure evidence.");
  await interventionDialog.getByRole("button", { name: "Close Case" }).click();
  await expect(interventionDialog).toBeHidden();

  const closedWarningsResponse = await request.get("/api/v1/warnings?status=CLOSED&page=1&size=200", {
    headers: authHeaders(counselor)
  });
  expect(closedWarningsResponse.status(), await closedWarningsResponse.text()).toBe(200);
  const closedWarnings = (await closedWarningsResponse.json() as ApiEnvelope<PageData<WarningSummary>>).data.list;
  expect(closedWarnings.find((item) => item.id === warning!.id)?.status).toBe("CLOSED");

  const metricsResponse = await request.get(`${BACKEND_URL}/actuator/prometheus`, {
    headers: authHeaders(globalAdministrator)
  });
  expect(metricsResponse.status(), await metricsResponse.text()).toBe(200);
  const metrics = await metricsResponse.text();
  expect(metrics).toContain("jvm_memory_used_bytes");
  expect(metrics).toContain("hikaricp_connections_active");
  expect(metrics).toContain("psy_assessment_submissions_total");
  expect(metrics).toContain("psy_scoring_runs_total");
  expect(metrics).toContain("psy_warning_actions_total");

  expect(consoleErrors).toEqual([]);
});
