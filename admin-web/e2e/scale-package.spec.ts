import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const TEST_PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";

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

async function login(request: APIRequestContext, principal: string): Promise<LoginData> {
  const response = await request.post("/auth/login/password", {
    data: {
      principal,
      password: TEST_PASSWORD,
      // The auth starter revokes an older session when the same device logs in
      // again.  Playwright may run these specs in parallel, so each login must
      // use an isolated device identity or one test invalidates another test's
      // bearer token mid-flow.
      deviceId: `playwright-${principal}-${randomUUID()}`,
      deviceType: "WEB",
      deviceName: "ScalePackage E2E"
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

test("versioned ScalePackage export and controlled cross-tenant import stay isolated and trilingual", async ({ page, request }) => {
  test.setTimeout(60_000);
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  const campusAdmin = await login(request, "campus_assessor");
  const defaultAdmin = await login(request, "assessor");
  const respondent = await login(request, "respondent");
  const uniqueSuffix = `${Date.now()}-${test.info().workerIndex}`;
  const scaleCode = `E2E_PACKAGE_${uniqueSuffix}`;
  const scaleName = `E2E Package ${uniqueSuffix}`;

  const createResponse = await request.post("/api/v1/scales", {
    headers: authHeaders(campusAdmin),
    data: {
      scaleCode,
      scaleName,
      versionNo: "v1",
      scoreMethod: "SIMPLE_SUM",
      scoreCoefficient: 1,
      anonymousSupported: false
    }
  });
  expect(createResponse.status(), await createResponse.text()).toBe(200);
  const sourceScaleId = (await createResponse.json() as ApiEnvelope<{ id: number }>).data.id;

  await installBrowserSession(page, campusAdmin);
  await page.goto(`/scale-governance?scaleId=${sourceScaleId}`);
  await expect(page.getByRole("heading", { name: "Scale Package Governance and Trilingual Content" })).toBeVisible();
  await expect(page.getByText(`${scaleCode} · v1 · DRAFT`)).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export versioned package" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.json$/);
  const packagePath = await download.path();
  expect(packagePath).not.toBeNull();
  const packageBytes = await readFile(packagePath!);
  const exportedDocument = JSON.parse(packageBytes.toString("utf8")) as Record<string, unknown>;
  expect(exportedDocument.format).toBe("PSY_SCALE_PACKAGE");
  expect(exportedDocument.schemaVersion).toBe(2);
  expect(exportedDocument.payloadHash).toMatch(/^[a-f0-9]{64}$/);

  const crossTenantExport = await request.get(`/api/v1/scales/${sourceScaleId}/package/export`, {
    headers: authHeaders(defaultAdmin)
  });
  expect(crossTenantExport.status()).toBe(404);
  expect((await crossTenantExport.json() as ApiEnvelope<null>).code).toBe("SCALE_NOT_FOUND");

  const anonymousPreview = await request.post("/api/v1/scales/imports/package/preview", {
    multipart: {
      file: { name: download.suggestedFilename(), mimeType: "application/json", buffer: packageBytes }
    }
  });
  expect(anonymousPreview.status()).toBe(401);

  const userPreview = await request.post("/api/v1/scales/imports/package/preview", {
    headers: authHeaders(respondent),
    multipart: {
      file: { name: download.suggestedFilename(), mimeType: "application/json", buffer: packageBytes }
    }
  });
  expect(userPreview.status()).toBe(403);

  await installBrowserSession(page, defaultAdmin);
  await page.goto("/scales");
  await expect(page.getByRole("heading", { name: "Scale Management" })).toBeVisible();
  await page.getByRole("button", { name: "Import Scale" }).click();
  const importDialog = page.getByRole("dialog", { name: "Import Scale Template" });
  await expect(importDialog).toBeVisible();
  await importDialog.locator('input[type="file"]').setInputFiles({
    name: download.suggestedFilename(),
    mimeType: "application/json",
    buffer: packageBytes
  });
  await importDialog.getByRole("button", { name: "Parse Import File" }).click();
  await expect(importDialog.getByText("PACKAGE_TRANSLATION_MISSING").first()).toBeVisible();
  await expect(importDialog.getByText("PACKAGE_AUTHORIZATION_REVIEW_REQUIRED")).toBeVisible();
  await expect(importDialog.getByRole("button", { name: "Confirm Import" })).toBeEnabled();
  await importDialog.getByRole("button", { name: "Confirm Import" }).click();

  await expect(page.getByText(new RegExp(`${scaleName} \\(${scaleCode}\\)`))).toBeVisible();
  await expect(page.getByText("DRAFT", { exact: true }).first()).toBeVisible();

  const importedScalesResponse = await request.get(`/api/v1/scales?scaleName=${encodeURIComponent(scaleName)}&page=1&size=20`, {
    headers: authHeaders(defaultAdmin)
  });
  expect(importedScalesResponse.status()).toBe(200);
  const importedScales = (await importedScalesResponse.json() as ApiEnvelope<{ list: Array<{ id: number; scaleCode: string }> }>).data.list;
  expect(importedScales.filter((item) => item.scaleCode === scaleCode)).toHaveLength(1);

  const jobsResponse = await request.get("/api/v1/scales/imports?status=SUCCESS&page=1&size=20", {
    headers: authHeaders(defaultAdmin)
  });
  expect(jobsResponse.status()).toBe(200);
  const jobs = (await jobsResponse.json() as ApiEnvelope<{ list: Array<{ id: number; fileName: string }> }>).data.list;
  const importJob = jobs.find((item) => item.fileName === download.suggestedFilename());
  expect(importJob).toBeDefined();

  const repeatedConfirm = await request.post(`/api/v1/scales/imports/package/${importJob!.id}/confirm`, {
    headers: authHeaders(defaultAdmin)
  });
  expect(repeatedConfirm.status()).toBe(400);
  expect((await repeatedConfirm.json() as ApiEnvelope<null>).code).toBe("SCALE_PACKAGE_IMPORT_NOT_CONFIRMABLE");

  const crossTenantConfirm = await request.post(`/api/v1/scales/imports/package/${importJob!.id}/confirm`, {
    headers: authHeaders(campusAdmin)
  });
  expect(crossTenantConfirm.status()).toBe(404);
  expect((await crossTenantConfirm.json() as ApiEnvelope<null>).code).toBe("SCALE_IMPORT_JOB_NOT_FOUND");

  const userScaleList = await request.get("/api/v1/scales", { headers: authHeaders(respondent) });
  expect(userScaleList.status()).toBe(403);

  await page.getByRole("dialog", { name: new RegExp(scaleName) }).getByRole("button", { name: "Close" }).click();
  const importRecordRow = page.getByRole("row", { name: new RegExp(`${download.suggestedFilename()} SUCCESS`) });
  await importRecordRow.getByRole("button", { name: "View Detail" }).click();
  await expect(page.getByText("A required Chinese, Japanese, or English scale translation is missing.").first()).toBeVisible();
  const englishImportDetail = page.getByRole("dialog", { name: "Import Record Detail" });
  await englishImportDetail.locator("button.ant-drawer-close").click();
  await expect(englishImportDetail).toBeHidden();

  await page.locator(".ant-select-selection-item").filter({ hasText: "English" }).click();
  await page.locator(".ant-select-item-option").filter({ hasText: "日本語" }).click();
  await expect(page.getByRole("heading", { name: "尺度管理" })).toBeVisible();
  await importRecordRow.getByRole("button", { name: "詳細を見る" }).click();
  await expect(page.getByText("必須の中国語、日本語、英語の尺度翻訳が不足しています。").first()).toBeVisible();
  const japaneseImportDetail = page.getByRole("dialog", { name: "インポートレコードの詳細" });
  await japaneseImportDetail.locator("button.ant-drawer-close").click();
  await expect(japaneseImportDetail).toBeHidden();

  await page.locator(".ant-select-selection-item").filter({ hasText: "日本語" }).click();
  await page.locator(".ant-select-item-option").filter({ hasText: "中文" }).click();
  await expect(page.getByRole("heading", { name: "量表管理" })).toBeVisible();
  await importRecordRow.getByRole("button", { name: "查看详情" }).click();
  await expect(page.getByText("缺少必需的中文、日语或英语量表翻译。").first()).toBeVisible();

  expect(consoleErrors).toEqual([]);
});

test("SCL-90 source package imports as a tenant draft with trilingual content and publication gates", async ({ page, request }) => {
  test.setTimeout(90_000);
  const assessor = await login(request, "assessor");
  const sourcePath = resolve(process.cwd(), "../doc/scale-packages/scl90-v1-source-draft.json");
  const sourceBytes = await readFile(sourcePath);

  await installBrowserSession(page, assessor);
  await page.goto("/scales");
  await expect(page.getByRole("heading", { name: "Scale Management" })).toBeVisible();
  await page.getByRole("button", { name: "Import Scale" }).click();
  const importDialog = page.getByRole("dialog", { name: "Import Scale Template" });
  await importDialog.locator('input[type="file"]').setInputFiles({
    name: "scl90-v1-source-draft.json",
    mimeType: "application/json",
    buffer: sourceBytes
  });
  await importDialog.getByRole("button", { name: "Parse Import File" }).click();
  await expect(importDialog.getByText("SCL90_USER_DRAFT", { exact: true })).toBeVisible();
  await expect(importDialog.getByText("90", { exact: true }).first()).toBeVisible();
  await expect(importDialog.getByRole("button", { name: "Confirm Import" })).toBeEnabled();
  const sourceConfirmResponse = page.waitForResponse((response) =>
    response.url().includes("/api/v1/scales/imports/package/") &&
    response.url().endsWith("/confirm") &&
    response.request().method() === "POST"
  );
  await importDialog.getByRole("button", { name: "Confirm Import" }).click();
  const sourceConfirm = await sourceConfirmResponse;
  expect(sourceConfirm.status(), await sourceConfirm.text()).toBe(200);
  await expect(importDialog).toBeHidden();

  const scalesResponse = await request.get("/api/v1/scales?page=1&size=100", {
    headers: authHeaders(assessor)
  });
  expect(scalesResponse.status(), await scalesResponse.text()).toBe(200);
  const scales = (await scalesResponse.json() as ApiEnvelope<{ list: Array<{ id: number; scaleCode: string; status: string }> }>).data.list;
  const imported = scales.find((scale) => scale.scaleCode === "SCL90_USER_DRAFT");
  expect(imported).toBeDefined();
  expect(imported!.status).toBe("DRAFT");

  const packageResponse = await request.get(`/api/v1/scales/${imported!.id}/package`, {
    headers: authHeaders(assessor)
  });
  expect(packageResponse.status(), await packageResponse.text()).toBe(200);
  const scalePackage = (await packageResponse.json() as ApiEnvelope<{
    translations: Array<{ localeCode: string; reviewStatus: string }>;
    dimensionTranslations: Array<{ localeCode: string }>;
    questionTranslations: Array<{ localeCode: string }>;
    optionTranslations: Array<{ localeCode: string }>;
    algorithmBinding?: { algorithmCode: string; algorithmVersion: string; reviewStatus: string } | null;
    governance?: { authorizationStatus: string; governanceStatus: string } | null;
  }>).data;
  expect(scalePackage.translations.map((item) => item.localeCode).sort()).toEqual(["en", "ja-JP", "zh-CN"]);
  expect(scalePackage.dimensionTranslations).toHaveLength(30);
  expect(scalePackage.questionTranslations).toHaveLength(270);
  // 90 questions × 5 response options × 3 locales.
  expect(scalePackage.optionTranslations).toHaveLength(1350);
  expect(scalePackage.algorithmBinding).toMatchObject({ algorithmCode: "SCL90_PROFILE", algorithmVersion: "1", reviewStatus: "DRAFT" });
  expect(scalePackage.governance).toMatchObject({ authorizationStatus: "PENDING_REVIEW", governanceStatus: "DRAFT" });

  const readinessResponse = await request.get(`/api/v1/scales/${imported!.id}/publication/readiness`, {
    headers: authHeaders(assessor)
  });
  expect(readinessResponse.status(), await readinessResponse.text()).toBe(200);
  const readiness = (await readinessResponse.json() as ApiEnvelope<{ ready: boolean; blockers: string[] }>).data;
  expect(readiness.ready).toBe(false);
  expect(readiness.blockers).toContain("AUTHORIZATION_NOT_CLEARED");
  expect(readiness.blockers).toContain("ALGORITHM_NOT_APPROVED");

  const goldenCasesResponse = await request.get(`/api/v1/scales/${imported!.id}/publication/golden-cases`, {
    headers: authHeaders(assessor)
  });
  expect(goldenCasesResponse.status(), await goldenCasesResponse.text()).toBe(200);
  const goldenCases = (await goldenCasesResponse.json() as ApiEnvelope<Array<{ id: number; caseCode: string; caseType: string }>>).data;
  expect(goldenCases.map((item) => item.caseCode).sort()).toEqual([
    "SCL90_ALL_FOUR",
    "SCL90_ALL_ZERO",
    "SCL90_MISSING_REQUIRED",
    "SCL90_SELF_HARM_SIGNAL"
  ]);
  for (const goldenCase of goldenCases) {
    const runResponse = await request.post(`/api/v1/scales/${imported!.id}/publication/golden-cases/${goldenCase.id}/run`, {
      headers: authHeaders(assessor)
    });
    expect(runResponse.status(), await runResponse.text()).toBe(200);
    const run = (await runResponse.json() as ApiEnvelope<{
      passed: boolean;
      actual: { totalScore?: number; highRiskRuleCode?: string; metrics?: Record<string, number>; trace?: { algorithmCode?: string } };
      differences: string[];
    }>).data;
    expect(run.passed, `${goldenCase.caseCode}: ${JSON.stringify(run.differences)}`).toBe(true);
    expect(run.differences).toEqual([]);
    if (goldenCase.caseType !== "MISSING") {
      expect(run.actual.trace?.algorithmCode).toBe("SCL90_PROFILE");
    }
    if (goldenCase.caseCode === "SCL90_ALL_FOUR") {
      expect(run.actual.totalScore).toBe(360);
      expect(run.actual.highRiskRuleCode).toBe("SCL90_SELF_HARM_IDEA");
      expect(run.actual.metrics).toMatchObject({ GSI: 4, PST: 90, PSDI: 4 });
    }
  }
});
