import { expect, test, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";

type ApiEnvelope<T> = { code: string; message: string; data: T };
type LoginData = { accessToken: string; refreshToken: string; expiresIn: number };
type Locale = "zh-CN" | "ja-JP" | "en";

const TEST_PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";
const LOCALES: Locale[] = ["zh-CN", "ja-JP", "en"];

function authHeaders(loginData: LoginData, locale = "en-US") {
  return { Authorization: `Bearer ${loginData.accessToken}`, "Accept-Language": locale };
}

async function installBrowserSession(page: Page, loginData: LoginData, locale = "zh-CN") {
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

async function expectOk<T>(response: APIResponse): Promise<T> {
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json() as ApiEnvelope<T>).data;
}

async function login(request: APIRequestContext, principal: string, suffix: string): Promise<LoginData> {
  return expectOk<LoginData>(await request.post("/auth/login/password", {
    data: {
      principal,
      password: TEST_PASSWORD,
      deviceId: `playwright-recode-matrix-${principal}-${suffix}`,
      deviceType: "WEB",
      deviceName: "Synthetic recode method matrix"
    },
    headers: { "Accept-Language": "en-US" }
  }));
}

function localized<T>(factory: (locale: Locale) => T): Record<Locale, T> {
  return Object.fromEntries(LOCALES.map((locale) => [locale, factory(locale)])) as Record<Locale, T>;
}

function answerText(locale: Locale, label: string) {
  return ({
    "zh-CN": `合成${label}`,
    "ja-JP": `合成${label}`,
    en: `Synthetic ${label}`
  } as Record<Locale, string>)[locale];
}

function option(score: number) {
  return {
    code: String(score),
    score,
    translations: {
      "zh-CN": `选项${score}`,
      "ja-JP": `選択肢${score}`,
      en: `Option ${score}`
    }
  };
}

function typedOption(code: string, score = 0) {
  return {
    code,
    score,
    translations: {
      "zh-CN": `选项${code}`,
      "ja-JP": `選択肢${code}`,
      en: `Option ${code}`
    }
  };
}

function band(min: number, max: number, value: number) {
  return { min, max, value };
}

function resultRuleTranslation(locale: Locale, level: string) {
  return {
    resultTitle: ({ "zh-CN": `${level}结果`, "ja-JP": `${level}結果`, en: `${level} result` } as Record<Locale, string>)[locale],
    resultDescription: ({ "zh-CN": `合成重编码夹具的${level}区间。`, "ja-JP": `合成リコードフィクスチャの${level}区間です。`, en: `Synthetic recode fixture ${level} band.` } as Record<Locale, string>)[locale],
    suggestionText: ({ "zh-CN": "仅用于技术验证。", "ja-JP": "技術検証だけに使用します。", en: "For technical verification only." } as Record<Locale, string>)[locale]
  };
}

function makeRecodePackage() {
  const dimensionTranslation = (label: string) => localized((locale) => ({
    name: answerText(locale, label),
    description: ({ "zh-CN": "声明式重编码技术维度。", "ja-JP": "宣言型リコードの技術次元です。", en: "Declaration-only recode technical dimension." } as Record<Locale, string>)[locale]
  }));
  const question = (
    questionNo: number,
    dimensionCode: string,
    questionType: string,
    label: string,
    options: Array<Record<string, unknown>> = [],
    extra: Record<string, unknown> = {}
  ) => ({
    questionNo,
    dimensionCode,
    questionType,
    required: true,
    reverseScore: false,
    // psy_scale_question.weight_value is numeric(10,2); keep the fixture
    // inside the persisted precision so the trace proves the DB contract.
    weightValue: questionType === "SLIDER" ? 0.01 : 1,
    translations: localized((locale) => ({ text: answerText(locale, label) })),
    options,
    ...extra
  });
  const allDimensionScores = {
    RECODE_SUM: { score: 1 },
    SLEEP_DURATION: { score: 1 },
    SLEEP_EFFICIENCY: { score: 1 },
    INPUT_TYPES: { score: 0 }
  };
  const mainAnswers = [
    { questionNo: 1, optionCodes: ["1"] },
    { questionNo: 2, optionCodes: ["2"] },
    { questionNo: 3, answerText: "22:30" },
    { questionNo: 4, answerText: "06:30" },
    { questionNo: 5, answerText: "23:00" },
    { questionNo: 6, answerText: "07:00" },
    { questionNo: 7, answerValue: 360 },
    { questionNo: 8, optionCodes: ["A", "B"] },
    { questionNo: 9, optionCodes: ["A"] },
    { questionNo: 10, optionCodes: ["A"] },
    { questionNo: 11, answerText: "synthetic free text" }
  ];
  const validExpected = {
    valid: true,
    totalScore: 6.6,
    riskLevel: "NORMAL",
    metrics: {},
    dimensions: allDimensionScores
  };
  const baseCase = (caseCode: string, caseType: string, input: Record<string, unknown>, expected: Record<string, unknown>) => ({
    caseCode,
    caseType,
    sourceReference: "Synthetic technical fixture: no original instrument content",
    input,
    expected
  });
  return {
    format: "PSY_SCALE_SOURCE_PACKAGE",
    schemaVersion: 1,
    scale: {
      scaleCode: "E2E_RECODE_MATRIX",
      scaleName: "Synthetic declaration-only recode matrix",
      versionNo: "synthetic-recode-v1",
      applicableTarget: "E2E_TECHNICAL_FIXTURE_ONLY",
      scoreMethod: "WEIGHTED_SUM",
      scoreCoefficient: 1,
      assessmentMode: "SELF",
      responseScale: { min: 0, max: 600, labels: [] },
      qualityPolicy: {
        missingAnswerPolicy: "REJECT",
        maxMissingRatio: 0,
        invalidResultAction: "INVALIDATE",
        requireAllRequiredAnswers: true
      },
      reportTemplate: "DIMENSION_PROFILE",
      algorithmBinding: {
        algorithmCode: "GENERIC_SCORE_CALCULATOR",
        algorithmVersion: "1",
        implementationType: "BUILTIN"
      },
      instruction: {
        "zh-CN": "仅用于隔离技术验证。",
        "ja-JP": "隔離された技術検証だけに使用します。",
        en: "For isolated technical verification only."
      }
    },
    governance: {
      sourceTitle: "Synthetic declaration-only recode fixture",
      publisherName: "lx-boot E2E",
      copyrightStatus: "PUBLIC_DOMAIN",
      authorizationStatus: "NOT_REQUIRED",
      authorizationType: "TECHNICAL_FIXTURE_ONLY",
      authorizationScope: "No original instrument items, translations, norms or interpretation claims.",
      authorizedLanguages: "en,zh-CN,ja-JP",
      governanceStatus: "DRAFT",
      targetPopulation: "Isolated test runtime only",
      nonDiagnosticStatement: "This technical fixture is not a psychological measure and is not diagnostic.",
      reviewStatus: "PENDING_REVIEW"
    },
    translations: {
      "zh-CN": { scaleName: "合成声明式重编码矩阵", purposeText: "仅用于重编码算法技术验证。", resultVisibilityText: "仅限隔离 E2E 环境查看。", nonDiagnosticText: "这不是心理测量工具，也不提供诊断。", helpResourceText: "不要用于真实自我评估。" },
      "ja-JP": { scaleName: "合成宣言型リコード行列", purposeText: "リコードアルゴリズムの技術検証だけに使用します。", resultVisibilityText: "隔離されたE2E環境だけで表示されます。", nonDiagnosticText: "心理測定や診断には使用しません。", helpResourceText: "実際の自己評価には使用しないでください。" },
      en: { scaleName: "Synthetic declaration-only recode matrix", purposeText: "A technical fixture for recode algorithm validation only.", resultVisibilityText: "Visible only inside the isolated E2E environment.", nonDiagnosticText: "This is not a psychological measure and is not diagnostic.", helpResourceText: "Do not use for real self-assessment." }
    },
    dimensions: [
      { dimensionCode: "RECODE_SUM", questionNos: [1, 2], translations: dimensionTranslation("总分重编码") , recode: { rule: "RECODE_SUM_TO_0_3", bands: [band(0, 1, 0), band(2, 3, 1), band(4, 5, 2), band(6, 100, 3)] } },
      { dimensionCode: "SLEEP_DURATION", questionNos: [3, 4], translations: dimensionTranslation("睡眠时长重编码"), recode: { rule: "SLEEP_DURATION_RECODE_0_3", startQuestionNo: 3, endQuestionNo: 4, bands: [band(0, 360, 3), band(361, 420, 2), band(421, 480, 1), band(481, 99999, 0)] } },
      { dimensionCode: "SLEEP_EFFICIENCY", questionNos: [5, 6, 7], translations: dimensionTranslation("睡眠效率重编码"), recode: { rule: "SLEEP_EFFICIENCY_RECODE_0_3", startQuestionNo: 5, endQuestionNo: 6, sleepQuestionNo: 7, bands: [band(0, 64, 3), band(65, 74, 2), band(75, 84, 1), band(85, 100, 0)] } },
      { dimensionCode: "INPUT_TYPES", questionNos: [8, 9, 10, 11], translations: dimensionTranslation("通用题型路径") }
    ],
    questions: [
      question(1, "RECODE_SUM", "SINGLE_CHOICE", "总分项目一", [0, 1, 2, 3, 4].map(option)),
      question(2, "RECODE_SUM", "SINGLE_CHOICE", "总分项目二", [0, 1, 2, 3, 4].map(option)),
      question(3, "SLEEP_DURATION", "TIME", "上床时间"),
      question(4, "SLEEP_DURATION", "TIME", "起床时间"),
      question(5, "SLEEP_EFFICIENCY", "TIME", "效率上床时间"),
      question(6, "SLEEP_EFFICIENCY", "TIME", "效率起床时间"),
      question(7, "SLEEP_EFFICIENCY", "SLIDER", "睡眠分钟数", [], { sliderMin: 0, sliderMax: 600, sliderStep: 1 }),
      question(8, "INPUT_TYPES", "MULTI_SELECT", "多选技术题", [typedOption("A"), typedOption("B"), typedOption("C")], { optionSelectionLimit: 2 }),
      question(9, "INPUT_TYPES", "MATRIX", "矩阵技术题", [typedOption("A"), typedOption("B")], { matrixGroupCode: "MATRIX_DEMO", rowCode: "ROW_1", columnCode: "COL_1" }),
      question(10, "INPUT_TYPES", "TEXT_WITH_OPTION", "选项文本技术题", [typedOption("A"), typedOption("B")], { textInputEnabled: false, textInputPlaceholder: "synthetic detail" }),
      question(11, "INPUT_TYPES", "TEXT", "自由文本技术题")
    ],
    // A declaration-only branch fixture: selecting q1=0 skips q2; selecting
    // q1=1 (the main Golden path below) keeps q2 visible. This contains no
    // original instrument content and exercises the same ScalePackage rule
    // consumed by both the Web renderer and the submit-time validator.
    skipRules: [
      { whenQuestionNo: 1, whenOptionCode: "0", skipQuestionNos: [2] }
    ],
    scoring: {
      canonicalConvention: "SYNTHETIC_TECHNICAL_FIXTURE",
      dimensionAggregation: "WEIGHTED_SUM",
      dimensionRule: "Use declared weighted aggregate before the whitelisted dimension recode.",
      indices: {}
    },
    norms: { status: "NOT_APPLICABLE", interpretation: "No norms in a technical fixture." },
    resultRules: [
      { ruleCode: "RECODE_TECH_LOW", riskLevel: "ATTENTION", scoreMin: 0, scoreMax: 6.59, scoreSource: "RAW_SCORE", translations: localized((locale) => resultRuleTranslation(locale, "low")) },
      { ruleCode: "RECODE_TECH_HIGH", riskLevel: "NORMAL", scoreMin: 6.6, scoreMax: 10, scoreSource: "RAW_SCORE", translations: localized((locale) => resultRuleTranslation(locale, "high")) }
    ],
    highRiskRules: [],
    goldenCases: [
      baseCase("RECODE_SUM_MAIN", "NORMAL", { answers: mainAnswers }, validExpected),
      baseCase("SLEEP_DURATION_CROSS_MIDNIGHT", "BOUNDARY", { answers: mainAnswers }, validExpected),
      baseCase("SLEEP_EFFICIENCY_MAIN", "NORMAL", { answers: mainAnswers }, validExpected),
      baseCase("RECODE_MISSING_REQUIRED", "MISSING", { answers: mainAnswers.slice(0, 6) }, { valid: false, errorCode: "MISSING_REQUIRED_ANSWER" }),
      baseCase("RECODE_INVALID_TIME", "INVALID", { answers: mainAnswers.map((answer) => answer.questionNo === 3 ? { questionNo: 3, answerText: "25:00" } : answer) }, { valid: false, errorCode: "TIME_ANSWER_INVALID" })
    ],
    sourceReferences: [
      { title: "Synthetic recode fixture specification", url: "https://example.invalid/lx-boot/recode-fixture", use: "technical fixture" },
      { title: "Synthetic recode algorithm contract", url: "https://example.invalid/lx-boot/recode-contract", use: "algorithm contract" }
    ],
    publicationBlockers: ["TECH_FIXTURE_ONLY", "PROFESSIONAL_REVIEW_PENDING", "BUSINESS_ACCEPTANCE_PENDING"]
  };
}

function approvedPackagePayload(pkg: Record<string, any>) {
  const approve = (items: Array<Record<string, unknown>> = []) => items.map((item) => ({ ...item, reviewStatus: "APPROVED" }));
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

async function findRespondentId(request: APIRequestContext, admin: LoginData): Promise<number> {
  const users = await expectOk<{ list: Array<{ userId: number; username: string }> }>(await request.get("/api/v1/user-admin/users?username=respondent&page=1&size=20", { headers: authHeaders(admin) }));
  const respondent = users.list.find((item) => item.username === "respondent");
  expect(respondent).toBeDefined();
  return respondent!.userId;
}

async function createTask(request: APIRequestContext, business: LoginData, respondentId: number, scaleId: number, suffix: string) {
  const now = Date.now();
  const task = await expectOk<{ id: number }>(await request.post("/api/v1/tasks", {
    headers: authHeaders(business),
    data: {
      taskName: `E2E recode matrix ${suffix}`,
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
  await expectOk<Record<string, unknown>>(await request.post(`/api/v1/tasks/${task.id}/assign-users`, { headers: authHeaders(business), data: { userIds: [respondentId] } }));
  return task.id;
}

test("synthetic generic dimension and time recode matrix runs in isolated PostgreSQL", async ({ page, request }) => {
  test.setTimeout(240_000);
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const source = makeRecodePackage();
  const admin = await login(request, "e2e_admin", suffix);
  const professional = await login(request, "counselor", suffix);
  const business = await login(request, "assessor", suffix);
  const respondent = await login(request, "respondent", suffix);
  const respondentId = await findRespondentId(request, admin);

  const preview = await expectOk<{ importId: number; readyForControlledImport: boolean; errorCount: number; errors: Array<{ errorCode: string; message: string }> }>(await request.post("/api/v1/scales/imports/package/preview", {
    headers: authHeaders(admin),
    multipart: { file: { name: "synthetic-recode-matrix.json", mimeType: "application/json", buffer: Buffer.from(JSON.stringify(source), "utf8") } }
  }));
  expect(preview.readyForControlledImport, JSON.stringify(preview.errors)).toBe(true);
  expect(preview.errorCount, JSON.stringify(preview.errors)).toBe(0);
  const imported = await expectOk<{ scaleId: number; status: string }>(await request.post(`/api/v1/scales/imports/package/${preview.importId}/confirm`, { headers: authHeaders(admin) }));
  expect(imported.status).toBe("SUCCESS");

  const pkg = await expectOk<Record<string, any>>(await request.get(`/api/v1/scales/${imported.scaleId}/package`, { headers: authHeaders(admin) }));
  expect(pkg.algorithmBinding).toMatchObject({ algorithmCode: "GENERIC_SCORE_CALCULATOR", algorithmVersion: "1", implementationType: "BUILTIN", reviewStatus: "DRAFT" });
  expect(JSON.parse(pkg.algorithmBinding.inputSchemaJson).dimensionRecodes).toMatchObject({
    RECODE_SUM: { rule: "RECODE_SUM_TO_0_3" },
    SLEEP_DURATION: { rule: "SLEEP_DURATION_RECODE_0_3", startQuestionId: expect.any(Number), endQuestionId: expect.any(Number) },
    SLEEP_EFFICIENCY: { rule: "SLEEP_EFFICIENCY_RECODE_0_3", sleepQuestionId: expect.any(Number) }
  });
  await expectOk<Record<string, unknown>>(await request.put(`/api/v1/scales/${imported.scaleId}/package`, { headers: authHeaders(admin), data: approvedPackagePayload(pkg) }));

  for (const sourceCase of source.goldenCases) {
    const saved = await expectOk<{ id: number }>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases`, { headers: authHeaders(admin), data: sourceCase }));
    const run = await expectOk<{ passed: boolean; differences: string[] }>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases/${saved.id}/run`, { headers: authHeaders(admin) }));
    expect(run.passed, `${sourceCase.caseCode}: ${run.differences.join(",")}`).toBe(true);
    expect(run.differences).toEqual([]);
    await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases/${saved.id}/approve`, { headers: authHeaders(professional) }));
  }
  await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/reviews/PROFESSIONAL`, {
    headers: authHeaders(professional),
    data: { decision: "APPROVED", reviewToken: `recode-professional-${suffix}`, comment: "Disposable technical fixture approval; not a clinical sign-off.", qualificationReference: "E2E-SYNTHETIC-NOT-A-REAL-CREDENTIAL", evidenceReference: `E2E-SYNTHETIC-RECODE-PROFESSIONAL-${suffix}`, reviewScope: "Synthetic recode workflow only." }
  }));
  await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/reviews/BUSINESS`, {
    headers: authHeaders(business),
    data: { decision: "APPROVED", reviewToken: `recode-business-${suffix}`, comment: "Disposable technical fixture approval; not production acceptance.", evidenceReference: `E2E-SYNTHETIC-RECODE-BUSINESS-${suffix}`, reviewScope: "Synthetic recode workflow only." }
  }));
  const readiness = await expectOk<{ ready: boolean; blockers: string[] }>(await request.get(`/api/v1/scales/${imported.scaleId}/publication/readiness`, { headers: authHeaders(admin) }));
  expect(readiness.ready, readiness.blockers.join(",")).toBe(true);
  await expectOk<{ status: string }>(await request.post(`/api/v1/scales/${imported.scaleId}/publish`, { headers: authHeaders(business) }));

  const taskId = await createTask(request, business, respondentId, imported.scaleId, suffix);
  const questions = await expectOk<{ questions: Array<{ questionNo: number; questionId: number; questionType: string; options: Array<{ optionCode: string; optionId: number }> }> }>(await request.get(`/api/v1/my/tasks/${taskId}/questions`, { headers: authHeaders(respondent) }));
  const byNo = (questionNo: number) => {
    const found = questions.questions.find((item) => item.questionNo === questionNo);
    expect(found).toBeDefined();
    return found!;
  };
  const choice = (questionNo: number, code: string) => {
    const q = byNo(questionNo);
    const selected = q.options.find((item) => item.optionCode === code);
    expect(selected).toBeDefined();
    return { questionId: q.questionId, optionId: selected!.optionId };
  };

  // Exercise the respondent page itself, not only the API payload. This is a
  // synthetic, question-free display contract for every supported input type;
  // it proves the ScalePackage renderer selects the right control and label.
  await installBrowserSession(page, respondent, "zh-CN");
  await page.goto(`/my/tasks/${taskId}`);
  for (const [index, sourceQuestion] of source.questions.entries()) {
    const questionTitle = sourceQuestion.translations["zh-CN"].text;
    await expect(page.getByText(`${sourceQuestion.questionNo}. ${questionTitle}`, { exact: true })).toBeVisible();
    switch (sourceQuestion.questionType) {
      case "SINGLE_CHOICE":
        await expect(page.getByRole("radio")).toHaveCount(sourceQuestion.options.length);
        // Keep q1 on the non-branching option so this display pass walks all
        // seven supported question types. The dedicated branch pass below
        // deliberately chooses q1=0.
        await page.getByRole("radio").nth(sourceQuestion.questionNo === 1 ? 1 : 0).click();
        break;
      case "TIME":
        await expect(page.getByPlaceholder("请选择时间")).toBeVisible();
        await page.getByPlaceholder("请选择时间").fill("22:30");
        await page.getByPlaceholder("请选择时间").press("Enter");
        break;
      case "SLIDER":
        await expect(page.getByRole("slider")).toBeVisible();
        await expect(page.getByText("范围：0 ~ 600", { exact: true })).toBeVisible();
        await page.getByRole("slider").press("Home");
        await page.getByRole("slider").press("ArrowRight");
        break;
      case "MULTI_SELECT":
        await expect(page.getByRole("checkbox")).toHaveCount(sourceQuestion.options.length);
        await expect(page.getByText("最多可选 2 项", { exact: true })).toBeVisible();
        await page.getByRole("checkbox").first().click();
        break;
      case "MATRIX":
        await expect(page.getByText("矩阵题组 MATRIX_DEMO · 行 ROW_1 / 列 COL_1", { exact: true })).toBeVisible();
        await expect(page.getByRole("radio")).toHaveCount(sourceQuestion.options.length);
        await page.getByRole("radio").first().click();
        break;
      case "TEXT_WITH_OPTION":
        await expect(page.getByRole("radio")).toHaveCount(sourceQuestion.options.length);
        await expect(page.getByPlaceholder("请输入你的回答")).toHaveCount(0);
        await page.getByRole("radio").first().click();
        break;
      case "TEXT":
        await expect(page.getByPlaceholder("请输入你的回答")).toBeVisible();
        await page.getByPlaceholder("请输入你的回答").fill("synthetic display navigation");
        break;
      default:
        throw new Error(`Unhandled synthetic question type: ${sourceQuestion.questionType}`);
    }
    if (index < source.questions.length - 1) {
      await page.getByRole("button", { name: "下一题", exact: true }).click();
    }
  }
  console.log("RECODE_RUNTIME_CHECK|question_display|PASS");

  // Exercise the actual respondent branch: q1=0 removes required q2 from
  // the visible question list, the UI reaches review and submits, and the
  // resulting report omits q2 accordingly.
  const skipTaskId = await createTask(request, business, respondentId, imported.scaleId, `${suffix}-skip-branch`);
  const skipQuestions = await expectOk<{ skipRules?: Array<{ whenQuestionNo: number; whenOptionCode: string; skipQuestionNos: number[] }>; questions: Array<{ questionNo: number; questionId: number; questionType: string; options: Array<{ optionCode: string; optionId: number }> }> }>(
    await request.get(`/api/v1/my/tasks/${skipTaskId}/questions`, { headers: authHeaders(respondent) })
  );
  expect(skipQuestions.skipRules).toEqual(source.skipRules);
  await installBrowserSession(page, respondent, "zh-CN");
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/api/v1/answer-sheets/submit")) {
      console.log(`DEBUG_SKIP_SUBMIT_REQUEST|${request.postData() ?? ""}`);
    }
  });
  await page.goto(`/my/tasks/${skipTaskId}`);
  await expect(page.getByText("1. 合成总分项目一", { exact: true })).toBeVisible();
  await page.getByRole("radio").first().click();
  await page.getByRole("button", { name: "下一题", exact: true }).click();
  await expect(page.getByText("2. 合成总分项目二", { exact: true })).toHaveCount(0);
  await expect(page.getByText("3. 合成上床时间", { exact: true })).toBeVisible();
  await expect(page.getByText(/10 道题/, { exact: false })).toBeVisible();

  const branchQuestions = source.questions.filter((question) => question.questionNo >= 3);
  for (const [index, sourceQuestion] of branchQuestions.entries()) {
    switch (sourceQuestion.questionType) {
      case "TIME":
        await expect(page.getByPlaceholder("请选择时间")).toBeVisible();
        await page.getByPlaceholder("请选择时间").fill(sourceQuestion.questionNo === 3 ? "22:30" : sourceQuestion.questionNo === 4 ? "06:30" : sourceQuestion.questionNo === 5 ? "23:00" : "07:00");
        await page.getByPlaceholder("请选择时间").press("Enter");
        break;
      case "SLIDER":
        await expect(page.getByRole("slider")).toBeVisible();
        await page.getByRole("slider").press("Home");
        await page.getByRole("slider").press("ArrowRight");
        break;
      case "MULTI_SELECT":
        await expect(page.getByRole("checkbox")).toHaveCount(sourceQuestion.options.length);
        await page.getByRole("checkbox").first().click();
        await page.getByRole("checkbox").nth(1).click();
        break;
      case "MATRIX":
        await expect(page.getByRole("radio")).toHaveCount(sourceQuestion.options.length);
        await page.getByRole("radio").first().click();
        break;
      case "TEXT_WITH_OPTION":
        await expect(page.getByRole("radio")).toHaveCount(sourceQuestion.options.length);
        await page.getByRole("radio").first().click();
        break;
      case "TEXT":
        await expect(page.getByPlaceholder("请输入你的回答")).toBeVisible();
        await page.getByPlaceholder("请输入你的回答").fill("synthetic skipped branch");
        break;
      default:
        throw new Error(`Unhandled synthetic branch question type: ${sourceQuestion.questionType}`);
    }
    if (index < branchQuestions.length - 1) {
      await page.getByRole("button", { name: "下一题", exact: true }).click();
    }
  }
  await expect(page.getByRole("button", { name: "确认答案", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "确认答案", exact: true }).click();
  await expect(page.getByText("提交前确认", { exact: true })).toBeVisible();

  const browserSkipSubmit = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/api/v1/answer-sheets/submit")
  );
  await page.getByRole("button", { name: "提交", exact: true }).click();
  const browserSkipSubmitResponse = await browserSkipSubmit;
  expect(browserSkipSubmitResponse.status(), await browserSkipSubmitResponse.text()).toBe(200);
  const skippedSubmitted = (await browserSkipSubmitResponse.json() as ApiEnvelope<{ resultId: number; reportId: number; riskLevel: string }>).data;
  expect(skippedSubmitted.resultId).toBeGreaterThan(0);
  await expect(page).toHaveURL(new RegExp(`/reports/${skippedSubmitted.reportId}`));
  console.log("RECODE_RUNTIME_CHECK|skip_branch|PASS");

  const skippedReport = await expectOk<{ answerDetails: Array<{ questionNo: number }> }>(await request.get(`/api/v1/reports/${skippedSubmitted.reportId}`, { headers: authHeaders(respondent) }));
  expect(skippedReport.answerDetails.some((detail) => detail.questionNo === 2)).toBe(false);

  const submitted = await expectOk<{ resultId: number; reportId: number; riskLevel: string }>(await request.post("/api/v1/answer-sheets/submit", {
    headers: authHeaders(respondent),
    data: {
      taskId,
      scaleId: imported.scaleId,
      submitToken: `recode-matrix-${suffix}`,
      answers: [
        choice(1, "1"),
        choice(2, "2"),
        { questionId: byNo(3).questionId, answerText: "22:30" },
        { questionId: byNo(4).questionId, answerText: "06:30" },
        { questionId: byNo(5).questionId, answerText: "23:00" },
        { questionId: byNo(6).questionId, answerText: "07:00" },
        { questionId: byNo(7).questionId, answerValue: 360 },
        choice(8, "A"),
        choice(8, "B"),
        choice(9, "A"),
        choice(10, "A"),
        { questionId: byNo(11).questionId, answerText: "synthetic free text" }
      ]
    }
  }));
  expect(submitted.riskLevel).toBe("NORMAL");
  const report = await expectOk<{ totalScore: number; riskLevel: string; reportTemplate: string; dimensionResults: Array<{ dimensionCode: string; score: number }>; answerDetails: Array<{ questionType: string; answerText?: string; answerValue?: number }> }>(await request.get(`/api/v1/reports/${submitted.reportId}`, { headers: authHeaders(respondent) }));
  expect(report).toMatchObject({ totalScore: 6.6, riskLevel: "NORMAL", reportTemplate: "DIMENSION_PROFILE" });
  expect(report.dimensionResults).toEqual(expect.arrayContaining([
    expect.objectContaining({ dimensionCode: "RECODE_SUM", score: 1 }),
    expect.objectContaining({ dimensionCode: "SLEEP_DURATION", score: 1 }),
    expect.objectContaining({ dimensionCode: "SLEEP_EFFICIENCY", score: 1 })
  ]));
  expect(report.answerDetails).toEqual(expect.arrayContaining([
    expect.objectContaining({ questionType: "TIME", answerText: "22:30" }),
    expect.objectContaining({ questionType: "TIME", answerText: "07:00" }),
    expect.objectContaining({ questionType: "SLIDER", answerValue: 360 }),
    expect.objectContaining({ questionType: "MULTI_SELECT" }),
    expect.objectContaining({ questionType: "MATRIX" }),
    expect.objectContaining({ questionType: "TEXT_WITH_OPTION" }),
    expect.objectContaining({ questionType: "TEXT", answerText: "synthetic free text" })
  ]));

  console.log("RECODE_RUNTIME_CHECK|rule_RECODE_SUM_TO_0_3|PASS");
  console.log("RECODE_RUNTIME_CHECK|rule_SLEEP_DURATION_RECODE_0_3|PASS");
  console.log("RECODE_RUNTIME_CHECK|rule_SLEEP_EFFICIENCY_RECODE_0_3|PASS");
  console.log("RECODE_RUNTIME_CHECK|question_types|PASS");
  console.log("RECODE_RUNTIME_CHECK|all_recode_rules|PASS");
});
