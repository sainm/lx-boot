import { expect, test, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";

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

type QuestionPayload = {
  taskId: number;
  scaleId: number;
  scaleName: string;
  anonymousFlag: boolean;
  draftAnswerSheetId?: number | null;
  draftVersionNo?: number | null;
  draftAnswers?: Array<{
    questionId: number;
    optionId?: number | null;
  }>;
  questions: Array<{
    questionId: number;
    questionTitle: string;
    options: Array<{ optionId: number; optionCode: string; optionLabel: string }>;
  }>;
};

type SubmitResult = {
  answerSheetId: number;
  resultId: number;
  reportId: number | null;
  riskLevel: string;
  versionNo: number;
  anonymous: boolean;
};

async function login(request: APIRequestContext, principal: string): Promise<LoginData> {
  const response = await request.post("/auth/login/password", {
    data: {
      principal,
      password: TEST_PASSWORD,
      deviceId: `playwright-integrity-${principal}`,
      deviceType: "WEB",
      deviceName: "Assessment integrity and privacy E2E"
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

async function findScale(request: APIRequestContext, administrator: LoginData): Promise<ScaleSummary> {
  const response = await request.get(
    "/api/v1/scales?scaleName=E2E%20Core%20Risk%20Technical%20Fixture&status=PUBLISHED&page=1&size=20",
    { headers: authHeaders(administrator) }
  );
  expect(response.status(), await response.text()).toBe(200);
  const scales = (await response.json() as ApiEnvelope<PageData<ScaleSummary>>).data.list;
  const scale = scales.find((item) => item.scaleCode === "E2E_CORE_TECH_FIXTURE");
  expect(scale).toBeDefined();
  return scale!;
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

async function createAssignedTask(
  request: APIRequestContext,
  administrator: LoginData,
  scaleId: number,
  taskName: string,
  anonymousFlag: boolean,
  userIds: number[]
) {
  const taskResponse = await request.post("/api/v1/tasks", {
    headers: authHeaders(administrator),
    data: {
      taskName,
      scaleId,
      taskMode: "SCREENING",
      anonymousFlag,
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
    data: { userIds }
  });
  expect(assignmentResponse.status(), await assignmentResponse.text()).toBe(200);
  return taskId;
}

async function loadQuestions(
  request: APIRequestContext,
  respondent: LoginData,
  taskId: number,
  locale = "en-US"
): Promise<QuestionPayload> {
  const response = await request.get(`/api/v1/my/tasks/${taskId}/questions`, {
    headers: authHeaders(respondent, locale)
  });
  expect(response.status(), await response.text()).toBe(200);
  const payload = (await response.json() as ApiEnvelope<QuestionPayload>).data;
  expect(payload.questions).toHaveLength(3);
  return payload;
}

function answersFor(payload: QuestionPayload, optionCode: string) {
  return payload.questions.map((question) => {
    const option = question.options.find((item) => item.optionCode === optionCode);
    expect(option, `Question ${question.questionId} has no option ${optionCode}`).toBeDefined();
    return { questionId: question.questionId, optionId: option!.optionId };
  });
}

async function responseEnvelope(response: APIResponse) {
  return await response.json() as ApiEnvelope<SubmitResult | null>;
}

test("concurrent first and existing draft saves preserve only the optimistic-lock winner", async ({ request }) => {
  test.setTimeout(45_000);
  const administrator = await login(request, "e2e_admin");
  const respondent = await login(request, "respondent");
  const respondentId = await findUserId(request, administrator, "respondent");
  const scale = await findScale(request, administrator);
  const taskId = await createAssignedTask(
    request,
    administrator,
    scale.id,
    `E2E Concurrent Save ${Date.now()}-${test.info().workerIndex}`,
    false,
    [respondentId]
  );
  const payload = await loadQuestions(request, respondent, taskId);
  const lowAnswers = answersFor(payload, "A");
  const highAnswers = answersFor(payload, "D");

  const initialRequests = [lowAnswers, highAnswers].map((answers) =>
    request.post("/api/v1/answer-sheets/save", {
      headers: authHeaders(respondent),
      data: { taskId, scaleId: scale.id, answers }
    })
  );
  const initialResponses = await Promise.all(initialRequests);
  expect(initialResponses.filter((response) => response.status() === 200)).toHaveLength(1);
  expect(initialResponses.filter((response) => response.status() === 400)).toHaveLength(1);
  const initialWinnerIndex = initialResponses.findIndex((response) => response.status() === 200);
  const initialWinner = (await initialResponses[initialWinnerIndex].json() as ApiEnvelope<{
    answerSheetId: number;
    versionNo: number;
  }>).data;
  const initialLoser = await initialResponses.find((response) => response.status() === 400)!.json() as ApiEnvelope<null>;
  expect(initialLoser.code).toBe("ANSWER_SHEET_VERSION_CONFLICT");
  expect(initialWinner.versionNo).toBe(2);

  const afterInitial = await loadQuestions(request, respondent, taskId);
  expect(afterInitial.draftAnswerSheetId).toBe(initialWinner.answerSheetId);
  expect(afterInitial.draftVersionNo).toBe(2);
  expect((afterInitial.draftAnswers ?? []).map((answer) => answer.optionId).sort())
    .toEqual([lowAnswers, highAnswers][initialWinnerIndex].map((answer) => answer.optionId).sort());

  const existingRequests = [highAnswers, lowAnswers].map((answers) =>
    request.post("/api/v1/answer-sheets/save", {
      headers: authHeaders(respondent),
      data: {
        taskId,
        scaleId: scale.id,
        answerSheetId: initialWinner.answerSheetId,
        versionNo: initialWinner.versionNo,
        answers
      }
    })
  );
  const existingResponses = await Promise.all(existingRequests);
  expect(existingResponses.filter((response) => response.status() === 200)).toHaveLength(1);
  expect(existingResponses.filter((response) => response.status() === 400)).toHaveLength(1);
  const existingWinnerIndex = existingResponses.findIndex((response) => response.status() === 200);
  const existingWinner = (await existingResponses[existingWinnerIndex].json() as ApiEnvelope<{
    answerSheetId: number;
    versionNo: number;
  }>).data;
  const existingLoser = await existingResponses.find((response) => response.status() === 400)!.json() as ApiEnvelope<null>;
  expect(existingLoser.code).toBe("ANSWER_SHEET_VERSION_CONFLICT");
  expect(existingWinner.answerSheetId).toBe(initialWinner.answerSheetId);
  expect(existingWinner.versionNo).toBe(3);

  const afterExisting = await loadQuestions(request, respondent, taskId);
  expect(afterExisting.draftVersionNo).toBe(3);
  expect((afterExisting.draftAnswers ?? []).map((answer) => answer.optionId).sort())
    .toEqual([highAnswers, lowAnswers][existingWinnerIndex].map((answer) => answer.optionId).sort());
});

test("different submit tokens racing on one draft create exactly one result", async ({ request }) => {
  test.setTimeout(45_000);
  const administrator = await login(request, "e2e_admin");
  const respondent = await login(request, "respondent");
  const respondentId = await findUserId(request, administrator, "respondent");
  const scale = await findScale(request, administrator);
  const taskId = await createAssignedTask(
    request,
    administrator,
    scale.id,
    `E2E Concurrent Submit ${Date.now()}-${test.info().workerIndex}`,
    false,
    [respondentId]
  );
  const payload = await loadQuestions(request, respondent, taskId);
  const answers = answersFor(payload, "A");

  const savedResponse = await request.post("/api/v1/answer-sheets/save", {
    headers: authHeaders(respondent),
    data: { taskId, scaleId: scale.id, answers }
  });
  expect(savedResponse.status(), await savedResponse.text()).toBe(200);
  const saved = (await savedResponse.json() as ApiEnvelope<{ answerSheetId: number; versionNo: number }>).data;

  const baseSubmission = {
    taskId,
    scaleId: scale.id,
    answerSheetId: saved.answerSheetId,
    versionNo: saved.versionNo,
    answers
  };
  const [first, second] = await Promise.all([
    request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent),
      data: { ...baseSubmission, submitToken: `concurrent-a-${taskId}` }
    }),
    request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent),
      data: { ...baseSubmission, submitToken: `concurrent-b-${taskId}` }
    })
  ]);

  const responses = [first, second];
  expect(responses.filter((response) => response.status() === 200)).toHaveLength(1);
  expect(responses.filter((response) => response.status() === 400)).toHaveLength(1);
  const winner = await responseEnvelope(responses.find((response) => response.status() === 200)!);
  const loser = await responseEnvelope(responses.find((response) => response.status() === 400)!);
  expect(winner.data?.reportId).not.toBeNull();
  expect(["ANSWER_SHEET_VERSION_CONFLICT", "TASK_ALREADY_SUBMITTED"]).toContain(loser.code);
});

test("anonymous high-risk submission has no identifiable report warning notification or export", async ({ page, request }) => {
  test.setTimeout(60_000);
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  const administrator = await login(request, "e2e_admin");
  const respondent = await login(request, "anonymous_respondent");
  const counselor = await login(request, "counselor");
  const respondentId = await findUserId(request, administrator, "anonymous_respondent");
  const peerId = await findUserId(request, administrator, "anonymous_peer");
  const scale = await findScale(request, administrator);
  const taskId = await createAssignedTask(
    request,
    administrator,
    scale.id,
    `E2E Anonymous Privacy ${Date.now()}-${test.info().workerIndex}`,
    true,
    [respondentId, peerId]
  );
  const payload = await loadQuestions(request, respondent, taskId);
  expect(payload.anonymousFlag).toBe(true);

  await installBrowserSession(page, respondent);
  await page.goto(`/my/tasks/${taskId}`);
  await expect(page.getByRole("heading", { name: "E2E Core Risk Technical Fixture" })).toBeVisible();
  for (let index = 0; index < payload.questions.length; index += 1) {
    await page.getByRole("radio", { name: "D. Technical high" }).check();
    await page.getByRole("button", { name: index === payload.questions.length - 1 ? "Review Answers" : "Next" }).click();
  }
  await expect(page.getByText(
    "This task is processed within an anonymous boundary: no identifiable personal report, alert, or notification is created.",
    { exact: true }
  )).toBeVisible();

  const submitResponsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/api/v1/answer-sheets/submit")
  );
  await page.getByRole("button", { name: "Submit", exact: true }).click();
  const submitResponse = await submitResponsePromise;
  expect(submitResponse.status(), await submitResponse.text()).toBe(200);
  const result = (await submitResponse.json() as ApiEnvelope<SubmitResult>).data;
  expect(result.anonymous).toBe(true);
  expect(result.reportId).toBeNull();
  expect(result.riskLevel).toBe("HIGH");
  await expect(page).toHaveURL(/\/my\/tasks$/);

  const myReportsResponse = await request.get("/api/v1/reports/my", {
    headers: authHeaders(respondent)
  });
  expect(myReportsResponse.status(), await myReportsResponse.text()).toBe(200);
  const myReports = (await myReportsResponse.json() as ApiEnvelope<Array<{ taskId: number }>>).data;
  expect(myReports.some((report) => report.taskId === taskId)).toBe(false);

  const staffReportsResponse = await request.get(`/api/v1/reports?taskId=${taskId}&page=1&size=20`, {
    headers: authHeaders(counselor)
  });
  expect(staffReportsResponse.status(), await staffReportsResponse.text()).toBe(200);
  expect((await staffReportsResponse.json() as ApiEnvelope<PageData<unknown>>).data.total).toBe(0);

  const warningsResponse = await request.get("/api/v1/warnings?page=1&size=200", {
    headers: authHeaders(counselor)
  });
  expect(warningsResponse.status(), await warningsResponse.text()).toBe(200);
  const warnings = (await warningsResponse.json() as ApiEnvelope<PageData<{ resultId: number }>>).data.list;
  expect(warnings.some((warning) => warning.resultId === result.resultId)).toBe(false);

  const notificationsResponse = await request.get("/api/v1/my/notifications", {
    headers: authHeaders(respondent)
  });
  expect(notificationsResponse.status(), await notificationsResponse.text()).toBe(200);
  const notifications = (await notificationsResponse.json() as ApiEnvelope<Array<{ notificationType: string }>>).data;
  expect(notifications.some((notification) =>
    notification.notificationType === "REPORT_GENERATED" || notification.notificationType === "REPORT_AUTO_SUBMITTED"
  )).toBe(false);

  const exportResponse = await request.post("/api/v1/exports/reports", {
    headers: authHeaders(counselor),
    data: { resultId: result.resultId, exportFormat: "TEXT", desensitized: true }
  });
  expect(exportResponse.status()).toBe(404);
  expect((await exportResponse.json() as ApiEnvelope<null>).code).toBe("REPORT_NOT_FOUND");
  expect(consoleErrors).toEqual([]);
});

test("approved ScalePackage content and generated reports run end to end in Chinese Japanese and English", async ({ request }) => {
  test.setTimeout(75_000);
  const administrator = await login(request, "e2e_admin");
  const respondent = await login(request, "respondent");
  const respondentId = await findUserId(request, administrator, "respondent");
  const scale = await findScale(request, administrator);
  const localeCases = [
    {
      locale: "zh-CN",
      canonicalLocale: "zh-CN",
      code: "zh",
      scaleName: "E2E 核心风险技术测试量表",
      questionTitle: "E2E 技术问题一",
      optionLabel: "技术低值",
      resultDescription: "E2E 技术正常结果。",
      suggestion: "不具有临床含义。",
      disclaimer: "不等同于临床诊断"
    },
    {
      locale: "ja-JP",
      canonicalLocale: "ja-JP",
      code: "ja",
      scaleName: "E2E コアリスク技術テスト尺度",
      questionTitle: "E2E 技術質問一",
      optionLabel: "技術低値",
      resultDescription: "E2E 技術テストの正常結果です。",
      suggestion: "臨床的な意味はありません。",
      disclaimer: "臨床診断ではありません"
    },
    {
      locale: "en-US",
      canonicalLocale: "en",
      code: "en",
      scaleName: "E2E Core Risk Technical Fixture",
      questionTitle: "E2E technical question one",
      optionLabel: "Technical low",
      resultDescription: "Technical E2E normal result.",
      suggestion: "No clinical meaning.",
      disclaimer: "not a clinical diagnosis"
    }
  ];

  for (const localeCase of localeCases) {
    const taskId = await createAssignedTask(
      request,
      administrator,
      scale.id,
      `E2E Locale ${localeCase.code} ${Date.now()}-${test.info().workerIndex}`,
      false,
      [respondentId]
    );
    const payload = await loadQuestions(request, respondent, taskId, localeCase.locale);
    expect(payload.scaleName).toBe(localeCase.scaleName);
    expect(payload.questions[0].questionTitle).toBe(localeCase.questionTitle);
    expect(payload.questions[0].options.find((option) => option.optionCode === "A")?.optionLabel)
      .toBe(localeCase.optionLabel);

    const submitResponse = await request.post("/api/v1/answer-sheets/submit", {
      headers: authHeaders(respondent, localeCase.locale),
      data: {
        taskId,
        scaleId: scale.id,
        submitToken: `locale-${localeCase.code}-${taskId}`,
        answers: answersFor(payload, "A")
      }
    });
    expect(submitResponse.status(), await submitResponse.text()).toBe(200);
    const submitResult = (await submitResponse.json() as ApiEnvelope<SubmitResult>).data;
    expect(submitResult.riskLevel).toBe("NORMAL");
    expect(submitResult.reportId).not.toBeNull();

    const reportResponse = await request.get(`/api/v1/reports/${submitResult.reportId}`, {
      headers: authHeaders(respondent, localeCase.locale)
    });
    expect(reportResponse.status(), await reportResponse.text()).toBe(200);
    const report = (await reportResponse.json() as ApiEnvelope<{
      scaleName: string;
      content: string;
      localeCode: string;
      answerDetails: Array<{ questionTitle: string; optionLabel: string }>;
    }>).data;
    expect(report.scaleName).toBe(localeCase.scaleName);
    expect(report.localeCode).toBe(localeCase.canonicalLocale);
    expect(report.content).toContain(localeCase.resultDescription);
    expect(report.content).toContain(localeCase.suggestion);
    expect(report.content).toContain(localeCase.disclaimer);
    expect(report.answerDetails[0].questionTitle).toBe(localeCase.questionTitle);
    expect(report.answerDetails[0].optionLabel).toBe(localeCase.optionLabel);
  }
});

test("approved Japanese high-risk rule translation drives the identified report", async ({ request }) => {
  test.setTimeout(45_000);
  const administrator = await login(request, "e2e_admin");
  const respondent = await login(request, "respondent");
  const respondentId = await findUserId(request, administrator, "respondent");
  const scale = await findScale(request, administrator);
  const taskId = await createAssignedTask(
    request,
    administrator,
    scale.id,
    `E2E High Risk Locale ja ${Date.now()}-${test.info().workerIndex}`,
    false,
    [respondentId]
  );
  const payload = await loadQuestions(request, respondent, taskId, "ja-JP");
  const submitResponse = await request.post("/api/v1/answer-sheets/submit", {
    headers: authHeaders(respondent, "ja-JP"),
    data: {
      taskId,
      scaleId: scale.id,
      submitToken: `high-risk-ja-${taskId}`,
      answers: answersFor(payload, "D")
    }
  });
  expect(submitResponse.status(), await submitResponse.text()).toBe(200);
  const submitted = (await submitResponse.json() as ApiEnvelope<SubmitResult>).data;
  expect(submitted.riskLevel).toBe("HIGH");
  expect(submitted.reportId).not.toBeNull();

  const reportResponse = await request.get(`/api/v1/reports/${submitted.reportId}`, {
    headers: authHeaders(respondent, "ja-JP")
  });
  expect(reportResponse.status(), await reportResponse.text()).toBe(200);
  const report = (await reportResponse.json() as ApiEnvelope<{ localeCode: string; content: string }>).data;
  expect(report.localeCode).toBe("ja-JP");
  expect(report.content).toContain("臨床的な意味を持たない技術自動化トリガーです。");
  expect(report.content).toContain("管理されたアラートと介入フローを確認してください。");
});
