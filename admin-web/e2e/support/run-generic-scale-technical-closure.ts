import { expect, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { basename } from "node:path";

const TEST_PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";

type LoginData = { accessToken: string; refreshToken: string; expiresIn: number };
type ApiEnvelope<T> = { code: string; message: string; data: T };
type PageData<T> = { list: T[]; total: number };
type Translation = { scaleName: string; nonDiagnosticText: string };
type ResultTranslation = { resultTitle: string; resultDescription: string; suggestionText: string };
type HighRiskTranslation = ResultTranslation;
type GoldenAnswer = { questionNo: number; optionCodes: string[] };
type GoldenCase = {
  caseCode: string;
  caseType: string;
  sourceReference: string;
  input: { answers: GoldenAnswer[] };
  expected: {
    valid: boolean;
    totalScore?: number;
    riskLevel?: string;
    metrics?: Record<string, string | number>;
    highRiskTriggered?: boolean;
    highRiskRuleCode?: string;
    errorCode?: string;
  };
};
export type GenericScaleSourcePackage = {
  scale: {
    scaleCode: string;
    scaleName: string;
    versionNo: string;
    reportTemplate: string;
  };
  translations: Record<string, Translation>;
  dimensions: Array<Record<string, unknown>>;
  questions: Array<{
    questionNo: number;
    translations: Record<string, { text: string }>;
    options: Array<Record<string, unknown>>;
  }>;
  resultRules: Array<{
    riskLevel: string;
    scoreMin: number;
    scoreMax: number;
    translations: Record<string, ResultTranslation>;
  }>;
  highRiskRules?: Array<{
    ruleCode: string;
    questionNo: number;
    optionCode?: string;
    scoreThreshold?: number;
    warningLevel: string;
    translations: Record<string, HighRiskTranslation>;
  }>;
  skipRules?: Array<{
    whenQuestionNo: number;
    whenOptionCode: string;
    skipQuestionNos: number[];
  }>;
  goldenCases: GoldenCase[];
};
type ScalePackage = {
  governance: Record<string, unknown> | null;
  translations: Array<Record<string, unknown>>;
  dimensionTranslations: Array<Record<string, unknown>>;
  questionTranslations: Array<Record<string, unknown>>;
  optionTranslations: Array<Record<string, unknown>>;
  resultRuleTranslations: Array<{
    localeCode: string;
    resultTitle: string;
    resultDescription: string | null;
    suggestionText: string | null;
  }>;
  highRiskRuleTranslations?: Array<{
    localeCode?: string;
    resultTitle?: string;
    resultDescription?: string;
    suggestionText?: string;
    ruleCode?: string;
    reviewStatus?: string;
  }>;
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
  skipRules?: Array<{
    whenQuestionNo: number;
    whenOptionCode: string;
    skipQuestionNos: number[];
  }>;
};
type SubmitResult = { resultId: number; reportId: number; riskLevel: string };

export type GenericScaleClosureConfig = {
  sourcePath: string;
  closureGoldenCaseCode: string;
  taskNamePrefix: string;
  algorithm?: {
    code: string;
    version: string;
    implementationType: string;
  };
};

async function login(request: APIRequestContext, principal: string, scaleCode: string): Promise<LoginData> {
  const response = await request.post("/auth/login/password", {
    data: {
      principal,
      password: TEST_PASSWORD,
      deviceId: `playwright-generic-scale-${scaleCode}-${principal}-${randomUUID()}`,
      deviceType: "WEB",
      deviceName: "Generic scale technical closure E2E"
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
  const users = await expectOk<PageData<{ userId: number; username: string }>>(
    await request.get(`/api/v1/user-admin/users?username=${encodeURIComponent(username)}&page=1&size=20`, {
      headers: authHeaders(administrator)
    })
  );
  const user = users.list.find((item) => item.username === username);
  expect(user, `Missing E2E fixture user ${username}`).toBeDefined();
  return user!.userId;
}

async function createAssignedTask(
  request: APIRequestContext,
  businessRole: LoginData,
  respondentId: number,
  scaleId: number,
  taskName: string,
  now: number
) {
  const task = await expectOk<{ id: number }>(await request.post("/api/v1/tasks", {
    headers: authHeaders(businessRole),
    data: {
      taskName,
      scaleId,
      taskMode: "SCREENING",
      anonymousFlag: false,
      allowSaveFlag: true,
      allowTimeoutSubmitFlag: false,
      allowRetakeFlag: false,
      startTime: new Date(now - 60_000).toISOString().slice(0, 19),
      endTime: new Date(now + 86_400_000).toISOString().slice(0, 19)
    }
  }));
  await expectOk<Record<string, unknown>>(await request.post(`/api/v1/tasks/${task.id}/assign-users`, {
    headers: authHeaders(businessRole), data: { userIds: [respondentId] }
  }));
  return task;
}

function answersForGolden(payload: QuestionPayload, goldenCase: GoldenCase) {
  const byQuestionNo = new Map(goldenCase.input.answers.map((answer) => [answer.questionNo, answer.optionCodes]));
  return payload.questions.map((question) => {
    const optionCodes = byQuestionNo.get(question.questionNo);
    expect(optionCodes, `Golden Case has no answer for question ${question.questionNo}`).toHaveLength(1);
    const option = question.options.find((item) => item.optionCode === optionCodes![0]);
    expect(option, `Question ${question.questionNo} has no option ${optionCodes![0]}`).toBeDefined();
    return { questionId: question.questionId, optionId: option!.optionId };
  });
}

export async function runGenericScaleTechnicalClosure(
  page: Page,
  request: APIRequestContext,
  source: GenericScaleSourcePackage,
  sourceBytes: Buffer,
  config: GenericScaleClosureConfig,
  workerIndex: number
) {
  const consoleErrors: string[] = [];
  page.on("console", (message) => { if (message.type() === "error") consoleErrors.push(message.text()); });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  const scaleCode = source.scale.scaleCode;
  const expectedAlgorithm = config.algorithm ?? {
    code: "GENERIC_SCORE_CALCULATOR",
    version: "1",
    implementationType: "BUILTIN"
  };
  const importer = await login(request, "e2e_admin", scaleCode);
  const professionalRole = await login(request, "counselor", scaleCode);
  const businessRole = await login(request, "assessor", scaleCode);
  const respondent = await login(request, "respondent", scaleCode);
  const campusAssessor = await login(request, "campus_assessor", scaleCode);
  const respondentId = await findUserId(request, importer, "respondent");
  const suffix = `${Date.now()}-${workerIndex}`;

  const closureCase = source.goldenCases.find((item) => item.caseCode === config.closureGoldenCaseCode);
  expect(closureCase, `Missing closure Golden Case ${config.closureGoldenCaseCode}`).toBeDefined();
  expect(closureCase!.expected.valid).toBe(true);
  expect(closureCase!.expected.totalScore).toBeDefined();
  expect(closureCase!.expected.riskLevel).toBeDefined();
  const expectedTotalScore = Number(closureCase!.expected.totalScore);
  expect(Number.isFinite(expectedTotalScore)).toBe(true);

  const preview = await expectOk<{
    importId: number;
    readyForControlledImport: boolean;
    errorCount: number;
    errors: Array<{ columnName?: string; errorCode: string; message: string }>;
  }>(
    await request.post("/api/v1/scales/imports/package/preview", {
      headers: authHeaders(importer),
      multipart: { file: { name: basename(config.sourcePath), mimeType: "application/json", buffer: sourceBytes } }
    })
  );
  expect(preview.readyForControlledImport, JSON.stringify(preview.errors)).toBe(true);
  expect(preview.errorCount, JSON.stringify(preview.errors)).toBe(0);
  const imported = await expectOk<{ scaleId: number; status: string; importedGoldenCaseRevisionCount: number }>(
    await request.post(`/api/v1/scales/imports/package/${preview.importId}/confirm`, { headers: authHeaders(importer) })
  );
  expect(imported.status).toBe("SUCCESS");
  expect(imported.importedGoldenCaseRevisionCount).toBe(source.goldenCases.length);
  const scaleId = imported.scaleId;

  // Security is part of the reusable scale closure: the imported version is
  // visible to its tenant's administrator, hidden from another tenant, and
  // not exposed to anonymous or respondent-role callers.
  const crossTenantReadiness = await request.get(`/api/v1/scales/${scaleId}/publication/readiness`, {
    headers: authHeaders(campusAssessor)
  });
  expect(crossTenantReadiness.status()).toBe(404);
  expect((await crossTenantReadiness.json() as ApiEnvelope<null>).code).toBe("SCALE_NOT_FOUND");
  const respondentPackage = await request.get(`/api/v1/scales/${scaleId}/package`, {
    headers: authHeaders(respondent)
  });
  expect(respondentPackage.status()).toBe(403);
  const anonymousReadiness = await request.get(`/api/v1/scales/${scaleId}/publication/readiness`);
  expect(anonymousReadiness.status()).toBe(401);

  const pkg = await expectOk<ScalePackage>(
    await request.get(`/api/v1/scales/${scaleId}/package`, { headers: authHeaders(importer) })
  );
  const locales = ["en", "ja-JP", "zh-CN"];
  expect(pkg.translations.map((item) => item.localeCode).sort()).toEqual(locales);
  expect(pkg.dimensionTranslations).toHaveLength(source.dimensions.length * locales.length);
  expect(pkg.questionTranslations).toHaveLength(source.questions.length * locales.length);
  expect(pkg.optionTranslations).toHaveLength(
    source.questions.reduce((count, question) => count + question.options.length, 0) * locales.length
  );
  expect(pkg.resultRuleTranslations).toHaveLength(source.resultRules.length * locales.length);
  expect(pkg.highRiskRuleTranslations ?? []).toHaveLength((source.highRiskRules ?? []).length * locales.length);
  expect(pkg.algorithmBinding).toMatchObject({
    algorithmCode: expectedAlgorithm.code,
    algorithmVersion: expectedAlgorithm.version,
    implementationType: expectedAlgorithm.implementationType,
    reviewStatus: "DRAFT"
  });
  for (const locale of ["zh-CN", "ja-JP", "en"] as const) {
    const expectedRules = source.resultRules.map((rule) => ({
      resultTitle: rule.translations[locale].resultTitle,
      resultDescription: rule.translations[locale].resultDescription,
      suggestionText: rule.translations[locale].suggestionText
    }));
    const actualRules = pkg.resultRuleTranslations
      .filter((translation) => translation.localeCode === locale)
      .map(({ resultTitle, resultDescription, suggestionText }) => ({ resultTitle, resultDescription, suggestionText }));
    expect(actualRules).toEqual(expect.arrayContaining(expectedRules));
    const expectedHighRiskRules = (source.highRiskRules ?? []).map((rule) => ({
      resultTitle: rule.translations[locale].resultTitle,
      resultDescription: rule.translations[locale].resultDescription,
      suggestionText: rule.translations[locale].suggestionText
    }));
    const actualHighRiskRules = (pkg.highRiskRuleTranslations ?? [])
      .filter((translation) => translation.localeCode === locale)
      .map(({ resultTitle, resultDescription, suggestionText }) => ({ resultTitle, resultDescription, suggestionText }));
    expect(actualHighRiskRules).toEqual(expect.arrayContaining(expectedHighRiskRules));
  }
  await expectOk<Record<string, unknown>>(await request.put(`/api/v1/scales/${scaleId}/package`, {
    headers: authHeaders(importer), data: approvedPackagePayload(pkg)
  }));

  for (const sourceCase of source.goldenCases) {
    const saved = await expectOk<{ id: number }>(await request.post(`/api/v1/scales/${scaleId}/publication/golden-cases`, {
      headers: authHeaders(importer), data: sourceCase
    }));
    const run = await expectOk<{ passed: boolean; differences: string[]; actual: { metrics?: Record<string, number> } }>(
      await request.post(`/api/v1/scales/${scaleId}/publication/golden-cases/${saved.id}/run`, {
        headers: authHeaders(importer)
      })
    );
    expect(run.passed, `${sourceCase.caseCode}: ${run.differences.join(",")}`).toBe(true);
    expect(run.differences).toEqual([]);
    if (sourceCase.expected.metrics) {
      const expectedMetrics = Object.fromEntries(
        Object.entries(sourceCase.expected.metrics).map(([code, value]) => [code, Number(value)])
      );
      expect(run.actual.metrics).toMatchObject(expectedMetrics);
    }
    await expectOk<Record<string, unknown>>(
      await request.post(`/api/v1/scales/${scaleId}/publication/golden-cases/${saved.id}/approve`, {
        headers: authHeaders(professionalRole)
      })
    );
  }

  await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${scaleId}/publication/reviews/PROFESSIONAL`, {
    headers: authHeaders(professionalRole),
    data: {
      decision: "APPROVED",
      reviewToken: `${scaleCode}-synthetic-professional-${suffix}`,
      comment: "Disposable E2E professional-role approval only; not a clinical endorsement.",
      qualificationReference: "E2E-SYNTHETIC-COUNSELOR-ROLE-NOT-A-REAL-CREDENTIAL",
      evidenceReference: `E2E-SYNTHETIC-${scaleCode}-PROFESSIONAL-${suffix}`,
      reviewScope: "Synthetic workflow verification only; not clinical sign-off."
    }
  }));
  await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${scaleId}/publication/reviews/BUSINESS`, {
    headers: authHeaders(businessRole),
    data: {
      decision: "APPROVED",
      reviewToken: `${scaleCode}-synthetic-business-${suffix}`,
      comment: "Disposable E2E business-role approval only; not production acceptance.",
      evidenceReference: `E2E-SYNTHETIC-${scaleCode}-BUSINESS-${suffix}`,
      reviewScope: "Synthetic workflow verification only; not production acceptance."
    }
  }));
  const readiness = await expectOk<{ ready: boolean; blockers: string[] }>(
    await request.get(`/api/v1/scales/${scaleId}/publication/readiness`, { headers: authHeaders(importer) })
  );
  expect(readiness.ready, readiness.blockers.join(",")).toBe(true);
  await expectOk<{ status: string }>(
    await request.post(`/api/v1/scales/${scaleId}/publish`, { headers: authHeaders(businessRole) })
  );

  const now = Date.now();
  const task = await createAssignedTask(
    request, businessRole, respondentId, scaleId, `${config.taskNamePrefix} technical closure ${suffix}`, now
  );
  const expectedQuestionNos = source.questions.map((question) => question.questionNo);
  const expectedSkipRules = source.skipRules ?? [];
  let japanesePayload: QuestionPayload | undefined;
  for (const [requestLocale, contentLocale] of [["zh-CN", "zh-CN"], ["ja-JP", "ja-JP"], ["en-US", "en"]] as const) {
    const payload = await expectOk<QuestionPayload>(
      await request.get(`/api/v1/my/tasks/${task.id}/questions`, { headers: authHeaders(respondent, requestLocale) })
    );
    expect(payload.scaleName).toBe(source.translations[contentLocale].scaleName);
    expect(payload.questions.map((item) => item.questionNo)).toEqual(expectedQuestionNos);
    expect(payload.questions.map((item) => item.questionNo)).toHaveLength(new Set(expectedQuestionNos).size);
    expect(payload.skipRules ?? []).toEqual(expectedSkipRules);
    expect(payload.questions.map((item) => item.questionTitle)).toEqual(
      source.questions.map((item) => item.translations[contentLocale].text)
    );
    if (contentLocale === "ja-JP") japanesePayload = payload;
  }
  expect(japanesePayload).toBeDefined();

  // Exercise the shared respondent renderer for every active generic package,
  // rather than treating a question-set API payload as display evidence. The
  // registered generic/SCL-90 technical profiles are single-choice packages;
  // the separate synthetic matrix covers the remaining controlled input types
  // and branching path. Skip this linear walk only when a future package
  // declares branching rules and supplies its own branch-aware fixture.
  if ((source.skipRules ?? []).length === 0) {
    await installBrowserSession(page, respondent, "zh-CN");
    await page.goto(`/my/tasks/${task.id}`);
    for (const [index, sourceQuestion] of source.questions.entries()) {
      const title = sourceQuestion.translations["zh-CN"].text;
      await expect(page.getByText(`${sourceQuestion.questionNo}. ${title}`, { exact: true })).toBeVisible();
      await expect(page.getByRole("radio")).toHaveCount(sourceQuestion.options.length);
      await page.getByRole("radio").first().click();
      if (index < source.questions.length - 1) {
        await page.getByRole("button", { name: "下一题", exact: true }).click();
      }
    }
    console.log("REGISTRY_RUNTIME_CHECK|question_display|PASS");
  }
  const answers = answersForGolden(japanesePayload!, closureCase!);
  const submissionPayload = {
    taskId: task.id,
    scaleId,
    submitToken: `${config.taskNamePrefix.toLowerCase()}-cutoff-${suffix}`,
    answers
  };
  const submitted = await expectOk<SubmitResult>(await request.post("/api/v1/answer-sheets/submit", {
    headers: authHeaders(respondent, "ja-JP"), data: submissionPayload
  }));
  expect(submitted.riskLevel).toBe(closureCase!.expected.riskLevel);
  const repeated = await expectOk<SubmitResult>(await request.post("/api/v1/answer-sheets/submit", {
    headers: authHeaders(respondent, "ja-JP"), data: submissionPayload
  }));
  expect(repeated).toEqual(submitted);

  const matchedRule = source.resultRules.find((rule) =>
    expectedTotalScore >= rule.scoreMin
    && expectedTotalScore <= rule.scoreMax
  );
  const matchedHighRiskRule = closureCase!.expected.highRiskRuleCode
    ? (source.highRiskRules ?? []).find((rule) => rule.ruleCode === closureCase!.expected.highRiskRuleCode)
    : undefined;
  expect(matchedRule, "No result rule matches closure Golden Case").toBeDefined();
  if (closureCase!.expected.highRiskTriggered) {
    expect(matchedHighRiskRule, "No high-risk rule matches closure Golden Case").toBeDefined();
  }
  const japaneseRule = (matchedHighRiskRule ?? matchedRule)!.translations["ja-JP"];
  const report = await expectOk<{
    totalScore: number;
    riskLevel: string;
    scaleName: string;
    resultTitle: string;
    scaleVersionNo: string;
    reportTemplate: string;
    localeCode: string;
    highRiskFlag: boolean;
    highRiskRuleCode: string | null;
    content: string;
    resultDescription: string;
    suggestionText: string;
    metrics: Array<{ code: string; rawValue: number }>;
  }>(await request.get(`/api/v1/reports/${submitted.reportId}`, { headers: authHeaders(respondent, "ja-JP") }));
  expect(report).toMatchObject({
    totalScore: expectedTotalScore,
    riskLevel: closureCase!.expected.riskLevel,
    scaleName: source.translations["ja-JP"].scaleName,
    resultTitle: japaneseRule.resultTitle,
    scaleVersionNo: source.scale.versionNo,
    reportTemplate: source.scale.reportTemplate,
    localeCode: "ja-JP",
    highRiskFlag: closureCase!.expected.highRiskTriggered ?? false,
    highRiskRuleCode: closureCase!.expected.highRiskRuleCode ?? null
  });
  expect(report.resultDescription).toContain(japaneseRule.resultDescription);
  expect(report.suggestionText).toContain(japaneseRule.suggestionText);
  expect(report.content).toContain(source.translations["ja-JP"].nonDiagnosticText);
  for (const [metricCode, metricValue] of Object.entries(closureCase!.expected.metrics ?? {})) {
    expect(report.metrics).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: metricCode, rawValue: Number(metricValue) })
    ]));
  }

  await installBrowserSession(page, respondent, "ja-JP");
  await page.goto(`/reports/${submitted.reportId}?resultId=${submitted.resultId}&taskId=${task.id}`);
  await expect(page.getByText(source.translations["ja-JP"].scaleName, { exact: false }).first()).toBeVisible();
  await expect(page.getByText(japaneseRule.resultTitle, { exact: false }).first()).toBeVisible();
  await expect(page.getByText(japaneseRule.resultDescription, { exact: false }).first()).toBeVisible();
  await expect(page.getByText(japaneseRule.suggestionText, { exact: false }).first()).toBeVisible();
  for (const metricValue of Object.values(closureCase!.expected.metrics ?? {})) {
    await expect(page.getByText(String(metricValue), { exact: true }).first()).toBeVisible();
  }

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
      expect(text).toContain(japaneseRule.resultTitle);
      expect(text).toContain(japaneseRule.resultDescription);
      expect(text).toContain(japaneseRule.suggestionText);
      expect(text).toContain(source.translations["ja-JP"].nonDiagnosticText);
    } else if (format === "PDF") {
      expect(bytes.subarray(0, 5).toString("ascii")).toBe("%PDF-");
    } else {
      expect(bytes.subarray(0, 2).toString("ascii")).toBe("PK");
    }
  }

  for (const [requestLocale, contentLocale] of [["zh-CN", "zh-CN"], ["en-US", "en"]] as const) {
    const localeTask = await createAssignedTask(
      request, businessRole, respondentId, scaleId, `${config.taskNamePrefix} trilingual result ${contentLocale} ${suffix}`, now
    );
    const localeQuestions = await expectOk<QuestionPayload>(
      await request.get(`/api/v1/my/tasks/${localeTask.id}/questions`, { headers: authHeaders(respondent, requestLocale) })
    );
    const localeAnswers = answersForGolden(localeQuestions, closureCase!);
    const localeSubmitted = await expectOk<SubmitResult>(await request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent, requestLocale),
      data: {
        taskId: localeTask.id,
        scaleId,
        submitToken: `${scaleCode}-${contentLocale}-${suffix}`,
        answers: localeAnswers
      }
    }));
    const localeReport = await expectOk<{
      scaleName: string;
      scaleVersionNo: string;
      reportTemplate: string;
      localeCode: string;
      resultTitle: string;
      resultDescription: string;
      suggestionText: string;
      nonDiagnosticText: string;
    }>(await request.get(`/api/v1/reports/${localeSubmitted.reportId}`, {
      headers: authHeaders(respondent, requestLocale)
    }));
    const localeRule = (matchedHighRiskRule ?? matchedRule)!.translations[contentLocale];
    expect(localeReport).toMatchObject({
      scaleName: source.translations[contentLocale].scaleName,
      scaleVersionNo: source.scale.versionNo,
      reportTemplate: source.scale.reportTemplate,
      localeCode: contentLocale,
      resultTitle: localeRule.resultTitle
    });
    expect(localeReport.resultDescription).toContain(localeRule.resultDescription);
    expect(localeReport.suggestionText).toContain(localeRule.suggestionText);
    expect(localeReport.nonDiagnosticText).toContain(source.translations[contentLocale].nonDiagnosticText);
    const textExport = await request.get(
      `/api/v1/exports/reports/download?reportId=${localeSubmitted.reportId}&resultId=${localeSubmitted.resultId}&exportFormat=TEXT&desensitized=false`,
      { headers: authHeaders(businessRole, requestLocale) }
    );
    expect(textExport.status(), await textExport.text()).toBe(200);
    const localeText = (await textExport.body()).toString("utf8");
    expect(localeText).toContain(localeRule.resultTitle);
    expect(localeText).toContain(localeRule.resultDescription);

    // Confirm the same localized result semantics are visible through the Web
    // report surface, not only through the localized API and TEXT export.
    await installBrowserSession(page, respondent, requestLocale);
    await page.goto(`/reports/${localeSubmitted.reportId}?resultId=${localeSubmitted.resultId}&taskId=${localeTask.id}`);
    await expect(page.getByText(source.translations[contentLocale].scaleName, { exact: false }).first()).toBeVisible();
    await expect(page.getByText(localeRule.resultTitle, { exact: false }).first()).toBeVisible();
    await expect(page.getByText(localeRule.resultDescription, { exact: false }).first()).toBeVisible();
    await expect(page.getByText(localeRule.suggestionText, { exact: false }).first()).toBeVisible();
  }

  const concurrentTask = await createAssignedTask(
    request, businessRole, respondentId, scaleId, `${config.taskNamePrefix} concurrent submission ${suffix}`, now
  );
  const concurrentQuestions = await expectOk<QuestionPayload>(
    await request.get(`/api/v1/my/tasks/${concurrentTask.id}/questions`, { headers: authHeaders(respondent, "ja-JP") })
  );
  const concurrentAnswers = answersForGolden(concurrentQuestions, closureCase!);
  const savedDraft = await expectOk<{ answerSheetId: number; versionNo: number }>(
    await request.post("/api/v1/answer-sheets/save", {
      headers: authHeaders(respondent, "ja-JP"),
      data: { taskId: concurrentTask.id, scaleId, answers: concurrentAnswers }
    })
  );
  const concurrentPayload = {
    taskId: concurrentTask.id,
    scaleId,
    answerSheetId: savedDraft.answerSheetId,
    versionNo: savedDraft.versionNo,
    answers: concurrentAnswers
  };
  const concurrentResponses = await Promise.all([
    request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent, "ja-JP"),
      data: { ...concurrentPayload, submitToken: `${scaleCode}-concurrent-a-${suffix}` }
    }),
    request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent, "ja-JP"),
      data: { ...concurrentPayload, submitToken: `${scaleCode}-concurrent-b-${suffix}` }
    })
  ]);
  expect(concurrentResponses.filter((response) => response.status() === 200)).toHaveLength(1);
  expect(concurrentResponses.filter((response) => response.status() === 400)).toHaveLength(1);
  const concurrentLoser = await concurrentResponses.find((response) => response.status() === 400)!.json() as ApiEnvelope<null>;
  expect(["ANSWER_SHEET_VERSION_CONFLICT", "TASK_ALREADY_SUBMITTED"]).toContain(concurrentLoser.code);

  const rescored = await expectOk<{
    resultId: number;
    reportId: number;
    totalScore: number;
    riskLevel: string;
    previousResultId: number;
    calculationVersion: number;
  }>(await request.post(`/api/v1/results/${submitted.resultId}/rescore`, {
    headers: authHeaders(businessRole, "ja-JP")
  }));
  expect(rescored).toMatchObject({
    totalScore: expectedTotalScore,
    riskLevel: closureCase!.expected.riskLevel,
    previousResultId: submitted.resultId,
    calculationVersion: 2
  });
  expect(rescored.resultId).not.toBe(submitted.resultId);
  expect(rescored.reportId).not.toBe(submitted.reportId);
  const originalReportAfterRescore = await expectOk<{
    resultTitle: string;
    resultDescription: string;
    suggestionText: string;
    highRiskFlag: boolean;
    highRiskRuleCode: string | null;
  }>(await request.get(`/api/v1/reports/${submitted.reportId}`, { headers: authHeaders(respondent, "ja-JP") }));
  expect(originalReportAfterRescore).toMatchObject({
    resultTitle: japaneseRule.resultTitle,
    resultDescription: expect.stringContaining(japaneseRule.resultDescription),
    suggestionText: expect.stringContaining(japaneseRule.suggestionText),
    highRiskFlag: closureCase!.expected.highRiskTriggered ?? false,
    highRiskRuleCode: closureCase!.expected.highRiskRuleCode ?? null
  });

  const nextVersion = await expectOk<{ id: number }>(await request.post(`/api/v1/scales/${scaleId}/versions`, {
    headers: authHeaders(importer),
    data: {
      versionNo: `${config.taskNamePrefix.toLowerCase()}-next-${suffix}`,
      scaleName: `${source.scale.scaleName} later draft ${suffix}`,
      description: "Disposable E2E draft proving task version lock."
    }
  }));
  expect(nextVersion.id).not.toBe(scaleId);
  const taskDetail = await expectOk<{ scaleId: number; scaleVersionNo: string }>(
    await request.get(`/api/v1/tasks/${task.id}`, { headers: authHeaders(businessRole) })
  );
  expect(taskDetail.scaleId).toBe(scaleId);
  expect(taskDetail.scaleVersionNo).toBe(source.scale.versionNo);
  console.log("REGISTRY_RUNTIME_CHECK|question_set_path|PASS");
  console.log("REGISTRY_RUNTIME_CHECK|security_boundaries|PASS");
  expect(consoleErrors).toEqual([]);
}
