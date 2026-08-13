import { expect, test, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const TEST_PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";

type LoginData = { accessToken: string; refreshToken: string; expiresIn: number };
type ApiEnvelope<T> = { code: string; message: string; data: T };
type PageData<T> = { list: T[]; total: number };
type K6SourcePackage = {
  scale: { versionNo: string };
  translations: Record<string, { scaleName: string; nonDiagnosticText: string }>;
  questions: Array<{
    questionNo: number;
    translations: Record<string, { text: string }>;
  }>;
  resultRules: Array<{
    riskLevel: string;
    translations: Record<string, { resultDescription: string; suggestionText: string }>;
  }>;
  goldenCases: Array<{
    caseCode: string;
    caseType: string;
    sourceReference: string;
    input: Record<string, unknown>;
    expected: Record<string, unknown>;
  }>;
};
type ScalePackage = {
  governance: Record<string, unknown> | null;
  translations: Array<Record<string, unknown>>;
  dimensionTranslations: Array<Record<string, unknown>>;
  questionTranslations: Array<Record<string, unknown>>;
  optionTranslations: Array<Record<string, unknown>>;
  resultRuleTranslations: Array<Record<string, unknown>>;
  highRiskRuleTranslations?: Array<Record<string, unknown>>;
  qualityPolicy: Record<string, unknown> | null;
  validityRules: Array<Record<string, unknown>>;
  algorithmBinding: Record<string, unknown> | null;
  normGovernance: Array<Record<string, unknown>>;
};
type QuestionPayload = {
  taskId: number;
  scaleId: number;
  scaleName: string;
  questions: Array<{
    questionId: number;
    questionNo: number;
    questionTitle: string;
    options: Array<{ optionId: number; optionCode: string; optionLabel: string }>;
  }>;
};

async function login(request: APIRequestContext, principal: string): Promise<LoginData> {
  const response = await request.post("/auth/login/password", {
    data: {
      principal,
      password: TEST_PASSWORD,
      deviceId: `playwright-k6-${principal}-${randomUUID()}`,
      deviceType: "WEB",
      deviceName: "K6 technical closure E2E"
    },
    headers: { "Accept-Language": "en-US" }
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json() as ApiEnvelope<LoginData>).data;
}

function authHeaders(loginData: LoginData, locale = "en-US") {
  return { Authorization: `Bearer ${loginData.accessToken}`, "Accept-Language": locale };
}

async function expectOk<T>(response: APIResponse): Promise<T> {
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json() as ApiEnvelope<T>).data;
}

async function installBrowserSession(page: Page, loginData: LoginData, locale: string) {
  await page.goto("/login");
  await page.evaluate(({ accessToken, refreshToken, expiresIn, localeCode }) => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    window.localStorage.setItem("psy-admin-web.auth-token", accessToken);
    window.localStorage.setItem("psy-admin-web.refresh-token", refreshToken);
    window.localStorage.setItem("psy-admin-web.access-token-expires-at", String(Date.now() + expiresIn * 1000));
    window.localStorage.setItem("psy-admin-web.locale", localeCode);
  }, { ...loginData, localeCode: locale });
}

function approvedPackagePayload(pkg: ScalePackage) {
  const approve = (items: Array<Record<string, unknown>> = []) =>
    items.map((item) => ({ ...item, reviewStatus: "APPROVED" }));
  return {
    governance: { ...pkg.governance, governanceStatus: "APPROVED" },
    translations: approve(pkg.translations),
    dimensionTranslations: approve(pkg.dimensionTranslations),
    questionTranslations: approve(pkg.questionTranslations),
    optionTranslations: approve(pkg.optionTranslations),
    resultRuleTranslations: approve(pkg.resultRuleTranslations),
    highRiskRuleTranslations: approve(pkg.highRiskRuleTranslations),
    qualityPolicy: pkg.qualityPolicy,
    validityRules: approve(pkg.validityRules),
    algorithmBinding: pkg.algorithmBinding ? { ...pkg.algorithmBinding, reviewStatus: "APPROVED" } : null,
    normGovernance: approve(pkg.normGovernance)
  };
}

async function findUserId(request: APIRequestContext, administrator: LoginData, username: string) {
  const response = await request.get(`/api/v1/user-admin/users?username=${encodeURIComponent(username)}&page=1&size=20`, {
    headers: authHeaders(administrator)
  });
  const users = await expectOk<PageData<{ userId: number; username: string }>>(response);
  const user = users.list.find((item) => item.username === username);
  expect(user, `Missing E2E fixture user ${username}`).toBeDefined();
  return user!.userId;
}

test("official-use K6 completes the isolated technical path through Web and text PDF Word reports", async ({ page, request }) => {
  test.setTimeout(180_000);
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  const importer = await login(request, "e2e_admin");
  const professionalRole = await login(request, "counselor");
  const businessRole = await login(request, "assessor");
  const respondent = await login(request, "respondent");
  const respondentId = await findUserId(request, importer, "respondent");
  const suffix = `${Date.now()}-${test.info().workerIndex}`;
  const sourcePath = resolve(process.cwd(), "../doc/scale-packages/k6-v1-source-official-draft.json");
  const sourceBytes = await readFile(sourcePath);
  const source = JSON.parse(sourceBytes.toString("utf8")) as K6SourcePackage;

  const preview = await expectOk<{ importId: number; readyForControlledImport: boolean; errorCount: number }>(
    await request.post("/api/v1/scales/imports/package/preview", {
      headers: authHeaders(importer),
      multipart: {
        file: { name: "k6-v1-source-official-draft.json", mimeType: "application/json", buffer: sourceBytes }
      }
    })
  );
  expect(preview.readyForControlledImport).toBe(true);
  expect(preview.errorCount).toBe(0);
  const imported = await expectOk<{ scaleId: number; status: string; importedGoldenCaseRevisionCount: number }>(
    await request.post(`/api/v1/scales/imports/package/${preview.importId}/confirm`, {
      headers: authHeaders(importer)
    })
  );
  expect(imported.status).toBe("SUCCESS");
  expect(imported.importedGoldenCaseRevisionCount).toBe(6);
  const scaleId = imported.scaleId;

  const pkg = await expectOk<ScalePackage>(
    await request.get(`/api/v1/scales/${scaleId}/package`, { headers: authHeaders(importer) })
  );
  expect(pkg.translations.map((item) => item.localeCode).sort()).toEqual(["en", "ja-JP", "zh-CN"]);
  expect(new Set(pkg.translations.map((item) => item.nonDiagnosticText))).toEqual(
    new Set(Object.values(source.translations).map((item) => item.nonDiagnosticText))
  );
  await expectOk<Record<string, unknown>>(
    await request.put(`/api/v1/scales/${scaleId}/package`, {
      headers: authHeaders(importer),
      data: approvedPackagePayload(pkg)
    })
  );

  // Updating review statuses changes the release fingerprint. Re-save every
  // imported case as a new revision before running it. The role approvals in
  // this disposable schema prove workflow mechanics only; they are explicitly
  // not a real clinical endorsement or production sign-off.
  const caseIds: number[] = [];
  for (const sourceCase of source.goldenCases) {
    const saved = await expectOk<{ id: number }>(
      await request.post(`/api/v1/scales/${scaleId}/publication/golden-cases`, {
        headers: authHeaders(importer),
        data: sourceCase
      })
    );
    caseIds.push(saved.id);
    const run = await expectOk<{ passed: boolean; differences: string[] }>(
      await request.post(`/api/v1/scales/${scaleId}/publication/golden-cases/${saved.id}/run`, {
        headers: authHeaders(importer)
      })
    );
    expect(run.passed, `${sourceCase.caseCode}: ${run.differences.join(",")}`).toBe(true);
    await expectOk<Record<string, unknown>>(
      await request.post(`/api/v1/scales/${scaleId}/publication/golden-cases/${saved.id}/approve`, {
        headers: authHeaders(professionalRole)
      })
    );
  }
  expect(caseIds).toHaveLength(6);

  await expectOk<Record<string, unknown>>(
    await request.post(`/api/v1/scales/${scaleId}/publication/reviews/PROFESSIONAL`, {
      headers: authHeaders(professionalRole),
      data: {
        decision: "APPROVED",
        reviewToken: `k6-test-professional-${suffix}`,
        comment: "Disposable E2E professional-role approval only; not a clinical endorsement.",
        qualificationReference: "E2E-SYNTHETIC-COUNSELOR-ROLE-NOT-A-REAL-CREDENTIAL",
        evidenceReference: `E2E-SYNTHETIC-K6-PROFESSIONAL-${suffix}`,
        reviewScope: "Synthetic workflow check of K6 source, translations, scoring boundary, result text, and reports; not clinical sign-off."
      }
    })
  );
  await expectOk<Record<string, unknown>>(
    await request.post(`/api/v1/scales/${scaleId}/publication/reviews/BUSINESS`, {
      headers: authHeaders(businessRole),
      data: {
        decision: "APPROVED",
        reviewToken: `k6-test-business-${suffix}`,
        comment: "Disposable E2E business-role approval only; not production acceptance.",
        evidenceReference: `E2E-SYNTHETIC-K6-BUSINESS-${suffix}`,
        reviewScope: "Synthetic workflow acceptance of the isolated K6 technical path; not production business acceptance."
      }
    })
  );
  const readiness = await expectOk<{ ready: boolean; blockers: string[] }>(
    await request.get(`/api/v1/scales/${scaleId}/publication/readiness`, { headers: authHeaders(importer) })
  );
  expect(readiness.ready, readiness.blockers.join(",")).toBe(true);
  await expectOk<{ status: string }>(
    await request.post(`/api/v1/scales/${scaleId}/publish`, { headers: authHeaders(businessRole) })
  );

  const now = Date.now();
  const task = await expectOk<{ id: number }>(
    await request.post("/api/v1/tasks", {
      headers: authHeaders(businessRole),
      data: {
        taskName: `K6 technical closure ${suffix}`,
        scaleId,
        taskMode: "SCREENING",
        anonymousFlag: false,
        allowSaveFlag: true,
        allowTimeoutSubmitFlag: false,
        allowRetakeFlag: false,
        startTime: new Date(now - 60_000).toISOString().slice(0, 19),
        endTime: new Date(now + 86_400_000).toISOString().slice(0, 19)
      }
    })
  );
  await expectOk<Record<string, unknown>>(
    await request.post(`/api/v1/tasks/${task.id}/assign-users`, {
      headers: authHeaders(businessRole),
      data: { userIds: [respondentId] }
    })
  );

  let japanesePayload: QuestionPayload | undefined;
  for (const [requestLocale, contentLocale] of [
    ["zh-CN", "zh-CN"],
    ["ja-JP", "ja-JP"],
    ["en-US", "en"]
  ] as const) {
    const payload = await expectOk<QuestionPayload>(
      await request.get(`/api/v1/my/tasks/${task.id}/questions`, { headers: authHeaders(respondent, requestLocale) })
    );
    expect(payload.scaleName).toBe(source.translations[contentLocale].scaleName);
    expect(payload.questions).toHaveLength(6);
    expect(payload.questions.map((item) => item.questionTitle)).toEqual(
      source.questions.map((item) => item.translations[contentLocale].text)
    );
    if (contentLocale === "ja-JP") japanesePayload = payload;
  }
  expect(japanesePayload).toBeDefined();

  const cutoffCodes = ["1", "1", "2", "3", "5", "5"];
  const answers = japanesePayload!.questions.map((question, index) => {
    const option = question.options.find((item) => item.optionCode === cutoffCodes[index]);
    expect(option).toBeDefined();
    return { questionId: question.questionId, optionId: option!.optionId };
  });
  const submitted = await expectOk<{ resultId: number; reportId: number; riskLevel: string }>(
    await request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent, "ja-JP"),
      data: {
        taskId: task.id,
        scaleId,
        submitToken: `k6-cutoff-${suffix}`,
        answers
      }
    })
  );
  expect(submitted.riskLevel).toBe("ATTENTION");

  const report = await expectOk<{
    totalScore: number;
    riskLevel: string;
    scaleName: string;
    scaleVersionNo: string;
    reportTemplate: string;
    localeCode: string;
    content: string;
    resultDescription: string;
    suggestionText: string;
    dimensionResults: Array<{ dimensionCode: string; score: number }>;
  }>(await request.get(`/api/v1/reports/${submitted.reportId}`, { headers: authHeaders(respondent, "ja-JP") }));
  const elevatedJapanese = source.resultRules.find((rule) => rule.riskLevel === "ATTENTION")!.translations["ja-JP"];
  expect(report).toMatchObject({
    totalScore: 13,
    riskLevel: "ATTENTION",
    scaleName: source.translations["ja-JP"].scaleName,
    scaleVersionNo: source.scale.versionNo,
    reportTemplate: "SINGLE_SCORE",
    localeCode: "ja-JP"
  });
  expect(report.resultDescription).toContain(elevatedJapanese.resultDescription);
  expect(report.suggestionText).toContain(elevatedJapanese.suggestionText);
  expect(report.content).toContain(source.translations["ja-JP"].nonDiagnosticText);
  expect(report.dimensionResults).toEqual(expect.arrayContaining([
    expect.objectContaining({ dimensionCode: "K6_TOTAL", score: 13 })
  ]));

  await installBrowserSession(page, respondent, "ja-JP");
  await page.goto(`/reports/${submitted.reportId}?resultId=${submitted.resultId}&taskId=${task.id}`);
  await expect(page.getByText(source.translations["ja-JP"].scaleName, { exact: false }).first()).toBeVisible();
  await expect(page.getByText(elevatedJapanese.resultDescription, { exact: false }).first()).toBeVisible();
  await expect(page.getByText(elevatedJapanese.suggestionText, { exact: false }).first()).toBeVisible();

  const exportExpectations = {
    TEXT: { contentType: "text/plain", extension: "txt" },
    PDF: { contentType: "application/pdf", extension: "pdf" },
    WORD: { contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", extension: "docx" }
  } as const;
  for (const [format, expected] of Object.entries(exportExpectations)) {
    const response = await request.get(
      `/api/v1/exports/reports/download?reportId=${submitted.reportId}&resultId=${submitted.resultId}&exportFormat=${format}&desensitized=false`,
      { headers: authHeaders(businessRole, "ja-JP") }
    );
    expect(response.status(), await response.text()).toBe(200);
    expect(response.headers()["content-type"]).toContain(expected.contentType);
    expect(response.headers()["x-export-format"]).toBe(format);
    expect(response.headers()["x-download-extension"]).toBe(expected.extension);
    const bytes = await response.body();
    expect(bytes.length).toBeGreaterThan(500);
    if (format === "TEXT") {
      const text = bytes.toString("utf8");
      expect(text).toContain(elevatedJapanese.resultDescription);
      expect(text).toContain(elevatedJapanese.suggestionText);
      expect(text).toContain(source.translations["ja-JP"].nonDiagnosticText);
    } else if (format === "PDF") {
      expect(bytes.subarray(0, 5).toString("ascii")).toBe("%PDF-");
    } else {
      expect(bytes.subarray(0, 2).toString("ascii")).toBe("PK");
    }
  }

  const nextVersion = await expectOk<{ id: number }>(
    await request.post(`/api/v1/scales/${scaleId}/versions`, {
      headers: authHeaders(importer),
      data: {
        versionNo: `k6-next-${suffix}`,
        scaleName: `K6 later draft ${suffix}`,
        description: "Disposable E2E draft proving the task remains version locked."
      }
    })
  );
  expect(nextVersion.id).not.toBe(scaleId);
  const taskDetail = await expectOk<{ scaleId: number; scaleVersionNo: string }>(
    await request.get(`/api/v1/tasks/${task.id}`, { headers: authHeaders(businessRole) })
  );
  expect(taskDetail.scaleId).toBe(scaleId);
  expect(taskDetail.scaleVersionNo).toBe(source.scale.versionNo);
  expect(consoleErrors).toEqual([]);
});
