import { expect, test, type APIRequestContext, type APIResponse } from "@playwright/test";

type ApiEnvelope<T> = { code: string; message: string; data: T };
type LoginData = { accessToken: string; refreshToken: string; expiresIn: number };
type Method = "SIMPLE_SUM" | "REVERSE_SUM" | "WEIGHTED_SUM" | "AVERAGE" | "WEIGHTED_AVERAGE";

type SourceTranslation = {
  scaleName: string;
  purposeText: string;
  resultVisibilityText: string;
  nonDiagnosticText: string;
  helpResourceText: string;
};

type MethodSourcePackage = {
  format: "PSY_SCALE_SOURCE_PACKAGE";
  schemaVersion: 1;
  scale: Record<string, unknown>;
  governance: Record<string, unknown>;
  translations: Record<string, SourceTranslation>;
  dimensions: Array<Record<string, unknown>>;
  questions: Array<Record<string, unknown>>;
  scoring: Record<string, unknown>;
  norms: Record<string, unknown>;
  resultRules: Array<Record<string, unknown>>;
  highRiskRules: Array<Record<string, unknown>>;
  goldenCases: Array<Record<string, unknown>>;
  sourceReferences: Array<Record<string, unknown>>;
  publicationBlockers: string[];
};

const TEST_PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";
const LOCALES = ["zh-CN", "ja-JP", "en"] as const;
const METHODS: Array<{ method: Method; scoreCoefficient: number; expectedTotal: number; expectedDimension: number; expectedEffectiveSum: number }> = [
  { method: "SIMPLE_SUM", scoreCoefficient: 1, expectedTotal: 5, expectedDimension: 5, expectedEffectiveSum: 5 },
  // Keep one non-unit coefficient in the matrix so scaling is verified through
  // import, scoring trace, persisted result and report semantics as well.
  { method: "REVERSE_SUM", scoreCoefficient: 1.25, expectedTotal: 6.25, expectedDimension: 5, expectedEffectiveSum: 5 },
  { method: "WEIGHTED_SUM", scoreCoefficient: 1, expectedTotal: 8, expectedDimension: 8, expectedEffectiveSum: 8 },
  { method: "AVERAGE", scoreCoefficient: 1, expectedTotal: 2.5, expectedDimension: 2.5, expectedEffectiveSum: 5 },
  { method: "WEIGHTED_AVERAGE", scoreCoefficient: 1, expectedTotal: 2.6667, expectedDimension: 2.6667, expectedEffectiveSum: 8 }
];

function authHeaders(loginData: LoginData, locale = "en-US") {
  return { Authorization: `Bearer ${loginData.accessToken}`, "Accept-Language": locale };
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
      deviceId: `playwright-method-matrix-${principal}-${suffix}`,
      deviceType: "WEB",
      deviceName: "Synthetic scoring-method matrix"
    },
    headers: { "Accept-Language": "en-US" }
  }));
}

function localized<T extends Record<string, unknown>>(factory: (locale: typeof LOCALES[number]) => T) {
  return Object.fromEntries(LOCALES.map((locale) => [locale, factory(locale)]));
}

function makeMethodPackage(method: Method): MethodSourcePackage {
  const scaleCode = `E2E_METHOD_MATRIX_${method}`;
  const scaleName = `Synthetic scoring method fixture (${method})`;
  const expectedTotal = METHODS.find((item) => item.method === method)!.expectedTotal;
  const rawMaximum = method === "WEIGHTED_SUM" ? 12 : method.endsWith("SUM") ? 8 : 4;
  const maximum = rawMaximum * METHODS.find((item) => item.method === method)!.scoreCoefficient;
  const translations = {
    "zh-CN": {
      scaleName: `合成计分方法夹具（${method}）`,
      purposeText: "仅用于验证通用计分方法的技术夹具。",
      resultVisibilityText: "仅限隔离 E2E 环境查看。",
      nonDiagnosticText: "这是无量表题目的技术方法夹具，不是心理测量工具，也不提供诊断。",
      helpResourceText: "该夹具不用于真实自我评估。"
    },
    "ja-JP": {
      scaleName: `合成スコアリング方法フィクスチャ（${method}）`,
      purposeText: "汎用スコアリング方式の技術検証だけに使用するフィクスチャです。",
      resultVisibilityText: "隔離されたE2E環境だけで表示されます。",
      nonDiagnosticText: "これは質問項目を含まない技術フィクスチャで、心理測定や診断には使用しません。",
      helpResourceText: "実際の自己評価には使用しないでください。"
    },
    en: {
      scaleName,
      purposeText: "A technical fixture for validating generic scoring methods only.",
      resultVisibilityText: "Visible only inside the isolated E2E environment.",
      nonDiagnosticText: "This is a question-free technical fixture, not a psychological measure and not diagnostic.",
      helpResourceText: "Do not use this fixture for real self-assessment."
    }
  } satisfies Record<typeof LOCALES[number], SourceTranslation>;

  const option = (score: number) => ({
    code: String(score),
    score,
    translations: Object.fromEntries(LOCALES.map((locale) => [locale, ({
      "zh-CN": ["零", "一", "二", "三", "四"][score],
      "ja-JP": ["ゼロ", "一", "二", "三", "四"][score],
      en: `Option ${score}`
    } as Record<string, string>)[locale]]))
  });
  const questions = [
    {
      questionNo: 1,
      dimensionCode: "METHOD_MATRIX_TOTAL",
      questionType: "SINGLE_CHOICE",
      required: true,
      reverseScore: false,
      weightValue: 1,
      translations: localized((locale) => ({
        text: ({ "zh-CN": "合成项目一", "ja-JP": "合成項目一", en: "Synthetic item one" } as Record<string, string>)[locale]
      })),
      options: [0, 1, 2, 3, 4].map(option)
    },
    {
      questionNo: 2,
      dimensionCode: "METHOD_MATRIX_TOTAL",
      questionType: "SINGLE_CHOICE",
      required: true,
      reverseScore: true,
      weightValue: 2,
      translations: localized((locale) => ({
        text: ({ "zh-CN": "合成项目二（反向）", "ja-JP": "合成項目二（逆転）", en: "Synthetic item two (reverse)" } as Record<string, string>)[locale]
      })),
      options: [0, 1, 2, 3, 4].map(option)
    }
  ];
  const resultRuleTranslation = (locale: typeof LOCALES[number], level: string) => ({
    resultTitle: ({ "zh-CN": `${level}结果`, "ja-JP": `${level}結果`, en: `${level} result` } as Record<string, string>)[locale],
    resultDescription: ({ "zh-CN": `合成夹具的${level}区间。`, "ja-JP": `合成フィクスチャの${level}区間です。`, en: `Synthetic fixture ${level} band.` } as Record<string, string>)[locale],
    suggestionText: ({ "zh-CN": "仅用于技术验证。", "ja-JP": "技術検証だけに使用します。", en: "For technical verification only." } as Record<string, string>)[locale]
  });
  const resultRules = [
    {
      ruleCode: "METHOD_MATRIX_LOW",
      riskLevel: "ATTENTION",
      scoreMin: 0,
      scoreMax: 2.49,
      scoreSource: "RAW_SCORE",
      translations: localized((locale) => resultRuleTranslation(locale, "low"))
    },
    {
      ruleCode: "METHOD_MATRIX_HIGH",
      riskLevel: "NORMAL",
      scoreMin: 2.5,
      scoreMax: maximum,
      scoreSource: "RAW_SCORE",
      translations: localized((locale) => resultRuleTranslation(locale, "high"))
    }
  ];
  const mainAnswers = [
    { questionNo: 1, optionCodes: ["2"] },
    { questionNo: 2, optionCodes: ["1"] }
  ];
  const zeroAnswers = [
    { questionNo: 1, optionCodes: ["0"] },
    { questionNo: 2, optionCodes: ["4"] }
  ];
  const baseCase = (caseCode: string, caseType: string, input: Record<string, unknown>, expected: Record<string, unknown>) => ({
    caseCode,
    caseType,
    sourceReference: "Synthetic fixture: no instrument question content",
    input,
    expected
  });
  return {
    format: "PSY_SCALE_SOURCE_PACKAGE",
    schemaVersion: 1,
    scale: {
      scaleCode,
      scaleName,
      versionNo: `synthetic-${method.toLowerCase()}-v1`,
      applicableTarget: "E2E_TECHNICAL_FIXTURE_ONLY",
      scoreMethod: method,
      scoreCoefficient: METHODS.find((item) => item.method === method)!.scoreCoefficient,
      assessmentMode: "SELF",
      responseScale: { min: 0, max: 4, labels: ["0", "1", "2", "3", "4"] },
      qualityPolicy: {
        missingAnswerPolicy: "REJECT",
        maxMissingRatio: 0,
        invalidResultAction: "INVALIDATE",
        requireAllRequiredAnswers: true
      },
      reportTemplate: "SINGLE_SCORE",
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
      sourceTitle: "Synthetic scoring-method fixture",
      publisherName: "lx-boot E2E",
      copyrightStatus: "PUBLIC_DOMAIN",
      authorizationStatus: "NOT_REQUIRED",
      authorizationType: "TECHNICAL_FIXTURE_ONLY",
      authorizationScope: "No original instrument items or interpretation claims.",
      authorizedLanguages: "en,zh-CN,ja-JP",
      governanceStatus: "DRAFT",
      targetPopulation: "Isolated test runtime only",
      nonDiagnosticStatement: "No psychological interpretation or diagnosis.",
      reviewStatus: "PENDING_REVIEW"
    },
    translations,
    dimensions: [{
      dimensionCode: "METHOD_MATRIX_TOTAL",
      questionNos: [1, 2],
      translations: localized((locale) => ({
        name: ({ "zh-CN": "合成总分", "ja-JP": "合成総合点", en: "Synthetic total" } as Record<string, string>)[locale],
        description: ({ "zh-CN": "技术夹具维度。", "ja-JP": "技術フィクスチャの次元です。", en: "Technical fixture dimension." } as Record<string, string>)[locale]
      }))
    }],
    questions,
    scoring: {
      canonicalConvention: "SYNTHETIC_0_TO_4",
      dimensionAggregation: method,
      dimensionRule: "Use the declared generic aggregation method over both synthetic items.",
      indices: {}
    },
    norms: { status: "NOT_APPLICABLE", interpretation: "No norms in a technical fixture." },
    resultRules,
    highRiskRules: [],
    goldenCases: [
      baseCase("METHOD_MATRIX_MAIN", "NORMAL", { answers: mainAnswers }, {
        valid: true,
        totalScore: expectedTotal,
        riskLevel: "NORMAL",
        metrics: {}
      }),
      baseCase("METHOD_MATRIX_REVERSE", "REVERSE", { answers: mainAnswers }, {
        valid: true,
        totalScore: expectedTotal,
        riskLevel: "NORMAL",
        metrics: {}
      }),
      baseCase("METHOD_MATRIX_ZERO", "BOUNDARY", { answers: zeroAnswers }, {
        valid: true,
        totalScore: 0,
        riskLevel: "ATTENTION",
        metrics: {}
      }),
      baseCase("METHOD_MATRIX_MISSING", "MISSING", { answers: [{ questionNo: 1, optionCodes: ["2"] }] }, {
        valid: false,
        errorCode: "MISSING_REQUIRED_ANSWER"
      }),
      baseCase("METHOD_MATRIX_INVALID", "INVALID", { answers: [
        { questionNo: 1, optionCodes: ["2"] },
        { questionNo: 2, optionCodes: ["not-an-option"] }
      ] }, {
        valid: false,
        errorCode: "OPTION_NOT_FOUND"
      })
    ],
    sourceReferences: [{ title: "Synthetic scoring-method fixture (no original content)", url: "https://example.invalid/lx-boot/synthetic-scoring-method-fixture" }],
    publicationBlockers: ["TECH_FIXTURE_ONLY"]
  };
}

type QualityPolicy = "REJECT" | "ALLOW" | "PRORATE";

function qualityExpectation(method: Method, policy: QualityPolicy) {
  const averageBased = method === "AVERAGE" || method === "WEIGHTED_AVERAGE";
  const weightedSum = method === "WEIGHTED_SUM";
  const weightedAverage = method === "WEIGHTED_AVERAGE";
  const coefficient = METHODS.find((item) => item.method === method)!.scoreCoefficient;
  const prorateFactor = policy === "PRORATE"
    ? weightedSum ? 3 : averageBased ? 1 : 2
    : 1;
  const aggregatedScore = policy === "PRORATE"
    ? weightedSum ? 6 : averageBased ? 2 : 4
    : 2;
  const totalScore = aggregatedScore * coefficient;
  const aggregation = policy === "PRORATE"
    ? weightedAverage ? "PRORATED_WEIGHTED_AVERAGE"
      : averageBased ? "PRORATED_AVERAGE"
        : "PRORATED_SUM"
    : weightedAverage ? "WEIGHTED_AVERAGE"
      : averageBased ? "AVERAGE"
        : "SUM";
  return {
    totalScore,
    dimensionScore: aggregatedScore,
    riskLevel: totalScore >= 2.5 ? "NORMAL" : "ATTENTION",
    prorateFactor,
    aggregation
  } as const;
}

/**
 * A question-free fixture for the missing-answer policies declared by the
 * generic method registry.  It deliberately reuses the same two synthetic
 * items and publication path as the method matrix; only the quality policy
 * and the missing Golden Case expectation change.
 */
function makeQualityPolicyPackage(method: Method, policy: QualityPolicy): MethodSourcePackage {
  const pkg = makeMethodPackage(method);
  if (policy === "REJECT") {
    pkg.scale = {
      ...pkg.scale,
      scaleCode: `E2E_QP_${method}_${policy}`,
      scaleName: `Synthetic missing-answer policy fixture (${method}/${policy})`,
      versionNo: `qp-${method.toLowerCase()}-${policy.toLowerCase()}-v1`
    };
    return pkg;
  }
  const expected = qualityExpectation(method, policy);
  pkg.scale = {
    ...pkg.scale,
    scaleCode: `E2E_QP_${method}_${policy}`,
    scaleName: `Synthetic missing-answer policy fixture (${method}/${policy})`,
    versionNo: `qp-${method.toLowerCase()}-${policy.toLowerCase()}-v1`,
    qualityPolicy: {
      missingAnswerPolicy: policy,
      maxMissingRatio: 0.5,
      invalidResultAction: "INVALIDATE",
      requireAllRequiredAnswers: false
    }
  };
  pkg.governance = {
    ...pkg.governance,
    sourceTitle: "Synthetic missing-answer policy fixture",
    authorizationScope: "No original instrument items or interpretation claims."
  };
  pkg.goldenCases = pkg.goldenCases.map((goldenCase) => {
    if (goldenCase.caseCode !== "METHOD_MATRIX_MISSING") return goldenCase;
    return {
      ...goldenCase,
      caseCode: `QUALITY_POLICY_${method}_${policy}_MISSING`,
      expected: {
        valid: true,
        totalScore: expected.totalScore,
        riskLevel: expected.riskLevel,
        metrics: {}
      }
    };
  });
  return pkg;
}

function approvedPackagePayload(pkg: Record<string, any>) {
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

async function findRespondentId(request: APIRequestContext, admin: LoginData): Promise<number> {
  const users = await expectOk<{ list: Array<{ userId: number; username: string }> }>(
    await request.get("/api/v1/user-admin/users?username=respondent&page=1&size=20", {
      headers: authHeaders(admin)
    })
  );
  const respondent = users.list.find((item) => item.username === "respondent");
  expect(respondent).toBeDefined();
  return respondent!.userId;
}

async function createTask(
  request: APIRequestContext,
  business: LoginData,
  respondentId: number,
  scaleId: number,
  method: Method,
  suffix: string,
  taskLabel = method
) {
  const now = Date.now();
  const task = await expectOk<{ id: number }>(await request.post("/api/v1/tasks", {
    headers: authHeaders(business),
    data: {
      taskName: `E2E method matrix ${taskLabel} ${suffix}`,
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
    headers: authHeaders(business),
    data: { userIds: [respondentId] }
  }));
  return task.id;
}

test("synthetic generic scoring method matrix runs all five methods in isolated PostgreSQL", async ({ request }) => {
  test.setTimeout(180_000);
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const admin = await login(request, "e2e_admin", suffix);
  const professional = await login(request, "counselor", suffix);
  const business = await login(request, "assessor", suffix);
  const respondent = await login(request, "respondent", suffix);
  const respondentId = await findRespondentId(request, admin);

  for (const methodCase of METHODS) {
    const source = makeMethodPackage(methodCase.method);
    const sourceBytes = Buffer.from(JSON.stringify(source), "utf8");
    const preview = await expectOk<{
      importId: number;
      readyForControlledImport: boolean;
      errorCount: number;
      errors: Array<{ errorCode: string; message: string }>;
    }>(await request.post("/api/v1/scales/imports/package/preview", {
      headers: authHeaders(admin),
      multipart: { file: { name: `synthetic-${methodCase.method}.json`, mimeType: "application/json", buffer: sourceBytes } }
    }));
    expect(preview.readyForControlledImport, JSON.stringify(preview.errors)).toBe(true);
    expect(preview.errorCount, JSON.stringify(preview.errors)).toBe(0);
    const imported = await expectOk<{ scaleId: number; status: string }>(
      await request.post(`/api/v1/scales/imports/package/${preview.importId}/confirm`, { headers: authHeaders(admin) })
    );
    expect(imported.status).toBe("SUCCESS");

    const pkg = await expectOk<Record<string, any>>(
      await request.get(`/api/v1/scales/${imported.scaleId}/package`, { headers: authHeaders(admin) })
    );
    expect(pkg.algorithmBinding).toMatchObject({
      algorithmCode: "GENERIC_SCORE_CALCULATOR",
      algorithmVersion: "1",
      implementationType: "BUILTIN",
      reviewStatus: "DRAFT"
    });
    await expectOk<Record<string, unknown>>(await request.put(`/api/v1/scales/${imported.scaleId}/package`, {
      headers: authHeaders(admin),
      data: approvedPackagePayload(pkg)
    }));

    for (const sourceCase of source.goldenCases) {
      const saved = await expectOk<{ id: number }>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases`, {
        headers: authHeaders(admin), data: sourceCase
      }));
      const run = await expectOk<{ passed: boolean; differences: string[] }>(
        await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases/${saved.id}/run`, {
          headers: authHeaders(admin)
        })
      );
      expect(run.passed, `${methodCase.method}/${sourceCase.caseCode}: ${run.differences.join(",")}`).toBe(true);
      expect(run.differences).toEqual([]);
      await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases/${saved.id}/approve`, {
        headers: authHeaders(professional)
      }));
    }
    await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/reviews/PROFESSIONAL`, {
      headers: authHeaders(professional),
      data: {
        decision: "APPROVED",
        reviewToken: `${methodCase.method}-synthetic-professional-${suffix}`,
        comment: "Disposable technical fixture approval; not a clinical sign-off.",
        qualificationReference: "E2E-SYNTHETIC-NOT-A-REAL-CREDENTIAL",
        evidenceReference: `E2E-SYNTHETIC-${methodCase.method}-PROFESSIONAL-${suffix}`,
        reviewScope: "Synthetic scoring-method workflow only."
      }
    }));
    await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/reviews/BUSINESS`, {
      headers: authHeaders(business),
      data: {
        decision: "APPROVED",
        reviewToken: `${methodCase.method}-synthetic-business-${suffix}`,
        comment: "Disposable technical fixture approval; not production acceptance.",
        evidenceReference: `E2E-SYNTHETIC-${methodCase.method}-BUSINESS-${suffix}`,
        reviewScope: "Synthetic scoring-method workflow only."
      }
    }));
    const readiness = await expectOk<{ ready: boolean; blockers: string[] }>(
      await request.get(`/api/v1/scales/${imported.scaleId}/publication/readiness`, { headers: authHeaders(admin) })
    );
    expect(readiness.ready, readiness.blockers.join(",")).toBe(true);
    await expectOk<{ status: string }>(await request.post(`/api/v1/scales/${imported.scaleId}/publish`, {
      headers: authHeaders(business)
    }));

    const taskId = await createTask(request, business, respondentId, imported.scaleId, methodCase.method, suffix);
    const questions = await expectOk<{
      questions: Array<{ questionNo: number; questionId: number; options: Array<{ optionCode: string; optionId: number }> }>;
    }>(await request.get(`/api/v1/my/tasks/${taskId}/questions`, { headers: authHeaders(respondent) }));
    const answers = [1, 2].map((questionNo) => {
      const question = questions.questions.find((item) => item.questionNo === questionNo);
      expect(question).toBeDefined();
      const code = questionNo === 1 ? "2" : "1";
      const option = question!.options.find((item) => item.optionCode === code);
      expect(option).toBeDefined();
      return { questionId: question!.questionId, optionId: option!.optionId };
    });
    const submitted = await expectOk<{ resultId: number; reportId: number; riskLevel: string }>(
      await request.post("/api/v1/answer-sheets/submit", {
        headers: authHeaders(respondent),
        data: { taskId, scaleId: imported.scaleId, submitToken: `method-matrix-${methodCase.method}-${suffix}`, answers }
      })
    );
    expect(submitted.riskLevel).toBe("NORMAL");
    const report = await expectOk<{
      totalScore: number;
      riskLevel: string;
      dimensionResults: Array<{ dimensionCode: string; score: number }>;
      reportTemplate: string;
    }>(await request.get(`/api/v1/reports/${submitted.reportId}`, { headers: authHeaders(respondent) }));
    expect(report).toMatchObject({
      // psy_assessment_result.total_score is numeric(10,2); the scoring trace
      // and dimension row retain the four-decimal calculator value.
      totalScore: Number(methodCase.expectedTotal.toFixed(2)),
      riskLevel: "NORMAL",
      reportTemplate: "SINGLE_SCORE"
    });
    expect(report.dimensionResults).toEqual(expect.arrayContaining([
      expect.objectContaining({ dimensionCode: "METHOD_MATRIX_TOTAL", score: methodCase.expectedDimension })
    ]));
  }
});

test("synthetic generic missing-answer policy matrix verifies every method REJECT, ALLOW and PRORATE traces", async ({ request }) => {
  test.setTimeout(600_000);
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const admin = await login(request, "e2e_admin", suffix);
  const professional = await login(request, "counselor", suffix);
  const business = await login(request, "assessor", suffix);
  const respondent = await login(request, "respondent", suffix);
  const respondentId = await findRespondentId(request, admin);

  for (const methodCase of METHODS) {
    for (const policy of ["REJECT", "ALLOW", "PRORATE"] as const) {
      const source = makeQualityPolicyPackage(methodCase.method, policy);
    const sourceBytes = Buffer.from(JSON.stringify(source), "utf8");
    const preview = await expectOk<{
      importId: number;
      readyForControlledImport: boolean;
      errorCount: number;
      errors: Array<{ errorCode: string; message: string }>;
    }>(await request.post("/api/v1/scales/imports/package/preview", {
      headers: authHeaders(admin),
      multipart: { file: { name: `synthetic-quality-${methodCase.method}-${policy}.json`, mimeType: "application/json", buffer: sourceBytes } }
    }));
    expect(preview.readyForControlledImport, JSON.stringify(preview.errors)).toBe(true);
    expect(preview.errorCount, JSON.stringify(preview.errors)).toBe(0);
    const imported = await expectOk<{ scaleId: number; status: string }>(
      await request.post(`/api/v1/scales/imports/package/${preview.importId}/confirm`, { headers: authHeaders(admin) })
    );
    expect(imported.status).toBe("SUCCESS");

    const pkg = await expectOk<Record<string, any>>(
      await request.get(`/api/v1/scales/${imported.scaleId}/package`, { headers: authHeaders(admin) })
    );
    await expectOk<Record<string, unknown>>(await request.put(`/api/v1/scales/${imported.scaleId}/package`, {
      headers: authHeaders(admin),
      data: approvedPackagePayload(pkg)
    }));
    for (const sourceCase of source.goldenCases) {
      const saved = await expectOk<{ id: number }>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases`, {
        headers: authHeaders(admin), data: sourceCase
      }));
      const run = await expectOk<{ passed: boolean; differences: string[] }>(
        await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases/${saved.id}/run`, {
          headers: authHeaders(admin)
        })
      );
      expect(run.passed, `${methodCase.method}/${policy}/${sourceCase.caseCode}: ${run.differences.join(",")}`).toBe(true);
      expect(run.differences).toEqual([]);
      await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/golden-cases/${saved.id}/approve`, {
        headers: authHeaders(professional)
      }));
    }
    await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/reviews/PROFESSIONAL`, {
      headers: authHeaders(professional),
      data: {
        decision: "APPROVED",
        reviewToken: `${methodCase.method}-${policy}-quality-professional-${suffix}`,
        comment: "Disposable technical fixture approval; not a clinical sign-off.",
        qualificationReference: "E2E-SYNTHETIC-NOT-A-REAL-CREDENTIAL",
        evidenceReference: `E2E-SYNTHETIC-QUALITY-${methodCase.method}-${policy}-PROFESSIONAL-${suffix}`,
        reviewScope: "Synthetic missing-answer policy workflow only."
      }
    }));
    await expectOk<Record<string, unknown>>(await request.post(`/api/v1/scales/${imported.scaleId}/publication/reviews/BUSINESS`, {
      headers: authHeaders(business),
      data: {
        decision: "APPROVED",
        reviewToken: `${methodCase.method}-${policy}-quality-business-${suffix}`,
        comment: "Disposable technical fixture approval; not production acceptance.",
        evidenceReference: `E2E-SYNTHETIC-QUALITY-${methodCase.method}-${policy}-BUSINESS-${suffix}`,
        reviewScope: "Synthetic missing-answer policy workflow only."
      }
    }));
    const readiness = await expectOk<{ ready: boolean; blockers: string[] }>(
      await request.get(`/api/v1/scales/${imported.scaleId}/publication/readiness`, { headers: authHeaders(admin) })
    );
    expect(readiness.ready, readiness.blockers.join(",")).toBe(true);
    await expectOk<{ status: string }>(await request.post(`/api/v1/scales/${imported.scaleId}/publish`, {
      headers: authHeaders(business)
    }));

    const taskId = await createTask(
      request,
      business,
      respondentId,
      imported.scaleId,
      methodCase.method,
      suffix,
      `${methodCase.method} ${policy}`
    );
    const questions = await expectOk<{
      questions: Array<{ questionNo: number; questionId: number; options: Array<{ optionCode: string; optionId: number }> }>;
    }>(await request.get(`/api/v1/my/tasks/${taskId}/questions`, { headers: authHeaders(respondent) }));
    const first = questions.questions.find((question) => question.questionNo === 1);
    expect(first).toBeDefined();
    const selected = first!.options.find((option) => option.optionCode === "2");
    expect(selected).toBeDefined();
    const submitResponse = await request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent),
      data: {
        taskId,
        scaleId: imported.scaleId,
        submitToken: `quality-policy-${methodCase.method}-${policy}-${suffix}`,
        answers: [{ questionId: first!.questionId, optionId: selected!.optionId }]
      }
    });
    if (policy === "REJECT") {
      expect(submitResponse.status(), await submitResponse.text()).toBe(400);
      const rejected = await submitResponse.json() as ApiEnvelope<null>;
      expect(rejected.code).toBe("ANSWER_REQUIRED_MISSING");
      continue;
    }
    const submitted = await expectOk<{ resultId: number; reportId: number; riskLevel: string }>(submitResponse);
    const expected = qualityExpectation(methodCase.method, policy);
    expect(submitted.riskLevel).toBe(expected.riskLevel);
    const report = await expectOk<{
      totalScore: number;
      riskLevel: string;
      dimensionResults: Array<{ dimensionCode: string; score: number }>;
      reportTemplate: string;
    }>(await request.get(`/api/v1/reports/${submitted.reportId}`, { headers: authHeaders(respondent) }));
    expect(report).toMatchObject({
      totalScore: expected.totalScore,
      riskLevel: expected.riskLevel,
      reportTemplate: "SINGLE_SCORE"
    });
    expect(report.dimensionResults).toEqual(expect.arrayContaining([
      expect.objectContaining({ dimensionCode: "METHOD_MATRIX_TOTAL", score: expected.dimensionScore })
    ]));
    }
  }
});
