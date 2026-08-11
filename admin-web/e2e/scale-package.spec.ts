import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { readFile } from "node:fs/promises";

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
      deviceId: `playwright-${principal}`,
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
