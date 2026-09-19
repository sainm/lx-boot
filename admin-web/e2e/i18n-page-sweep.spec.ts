/**
 * Per-page zh/ja/en rendering sweep.
 *
 * For every page and every supported locale the sweep:
 *  - flags raw i18n keys (``a.b.c``) that reached the DOM,
 *  - flags backend enum codes rendered as-is,
 *  - flags broken values (undefined/null/NaN/Invalid Date),
 *  - verifies the page title (route label) uses the active locale and that the
 *    Chinese label does not leak into ja/en pages (kana leaking into zh/en).
 *
 * Results are written to ``build/reports/i18n-sweep/report.json``.
 */
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { messages } from "../src/i18n/messages";

const API = process.env.PSY_E2E_API_URL ?? "http://127.0.0.1:8090";
const PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";
const SPEC_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPORT_DIR = path.resolve(SPEC_DIR, "../../build/reports/i18n-sweep");
const LOCALES = ["zh-CN", "ja-JP", "en-US"] as const;
type Locale = (typeof LOCALES)[number];

type PageSpec = {
  route: string;
  labelKey: string;
  account: "assessor" | "org_manager" | "respondent";
  dynamic?: "report" | "task";
};

const PAGES: PageSpec[] = [
  { route: "/home", labelKey: "route.user-home", account: "respondent" },
  { route: "/my/tasks", labelKey: "route.my-tasks", account: "respondent" },
  { route: "/my/reports", labelKey: "route.my-reports", account: "respondent" },
  { route: "/my/profile", labelKey: "route.my-profile", account: "respondent" },
  { route: "/dashboard", labelKey: "route.dashboard", account: "assessor" },
  { route: "/scales", labelKey: "route.scales", account: "assessor" },
  { route: "/scale-publication", labelKey: "route.scale-publication", account: "assessor" },
  { route: "/scale-governance", labelKey: "route.scale-governance", account: "assessor" },
  { route: "/tasks", labelKey: "route.tasks", account: "assessor" },
  { route: "/warnings", labelKey: "route.warnings", account: "assessor" },
  { route: "/safety-response-policies", labelKey: "route.safety-policies", account: "assessor" },
  { route: "/group-reports", labelKey: "route.group-reports", account: "assessor" },
  { route: "/user-reports", labelKey: "route.user-reports", account: "assessor" },
  { route: "/appointments", labelKey: "route.appointments", account: "assessor" },
  { route: "/exports-center", labelKey: "route.export-ops", account: "assessor" },
  { route: "/notifications", labelKey: "route.notifications", account: "assessor" },
  { route: "/pending-registrations", labelKey: "route.pending-registrations", account: "assessor" },
  { route: "/session", labelKey: "route.session", account: "assessor" },
  { route: "/user-admin", labelKey: "route.user-admin", account: "org_manager" },
  { route: "/auth-audit", labelKey: "route.auth-audit", account: "org_manager" },
  { route: "/reports", labelKey: "route.report-detail", account: "assessor", dynamic: "report" },
  { route: "/my/tasks", labelKey: "route.task-question", account: "respondent", dynamic: "task" }
];

type Finding = { page: string; locale: Locale; kind: string; detail: string };
const findings: Finding[] = [];
const captured = new Map<string, Set<string>>();

const RAW_KEY = /^[a-z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/;
const ENUM_CODES =
  /^(SCREENING|RETEST|FOLLOW_UP|DRAFT|PUBLISHED|ARCHIVED|IN_PROGRESS|COMPLETED|OVERDUE|PENDING|ASSIGNED|PROCESSING|CLOSED|CREATED|CONFIRMED|CANCELLED|NO_SHOW|LOW|MEDIUM|HIGH|CRITICAL|ATTENTION|RESOLVED|MISSING|SIMPLE_SUM|REVERSE_SUM|WEIGHTED_SUM|AVERAGE|WEIGHTED_AVERAGE|ACTIVE|REVOKED|EXPIRED|SUCCESS|FAIL|ENABLED|DISABLED|SYSTEM|AUTO)$/;
const BROKEN = /(^|\s)(undefined|null|NaN|Invalid Date|\[object Object\])(\s|$)/;
const KANA = /[\u3040-\u30ff]/;
const HAN = /[\u4e00-\u9fff]/;

async function login(request: APIRequestContext, principal: string) {
  const response = await request.post(`${API}/auth/login/password`, {
    data: {
      principal,
      password: PASSWORD,
      deviceId: `i18n-sweep-${principal}`,
      deviceType: "WEB",
      deviceName: "i18n sweep"
    }
  });
  expect(response.ok(), `login ${principal}: ${response.status()}`).toBeTruthy();
  const payload = await response.json();
  return payload.data as { accessToken: string; refreshToken: string; expiresIn?: number };
}

async function prime(page: Page, principal: string, password: string, tokens: { accessToken: string; refreshToken: string }, locale: Locale) {
  await page.addInitScript(
    ({ key, token, refresh, localeKey, localeValue }) => {
      window.localStorage.setItem("psy-admin-web.auth-token", token);
      window.localStorage.setItem("psy-admin-web.refresh-token", refresh);
      window.localStorage.setItem("psy-admin-web.locale", localeValue);
      window.sessionStorage.removeItem(key);
    },
    {
      key: `login:${principal}:${password}`,
      token: tokens.accessToken,
      refresh: tokens.refreshToken,
      localeKey: "psy-admin-web.locale",
      localeValue: locale
    }
  );
}

async function capture(page: Page, spec: PageSpec, locale: Locale) {
  const label = messages[locale][spec.labelKey];
  const lines = (await page.evaluate(() => document.body.innerText))
    .split("\n")
    .map((line: string) => line.trim())
    .filter(Boolean);
  captured.set(`${spec.route}:${locale}`, new Set(lines));

  for (const line of lines) {
    if (RAW_KEY.test(line) && line.split(".").length >= 2 && line.length < 60) {
      findings.push({ page: spec.route, locale, kind: "raw-i18n-key", detail: line });
    }
    if (ENUM_CODES.test(line)) {
      findings.push({ page: spec.route, locale, kind: "enum-code", detail: line });
    }
    if (BROKEN.test(line)) {
      findings.push({ page: spec.route, locale, kind: "broken-value", detail: line });
    }
  }

  const body = lines.join("\n");
  if (label && !body.includes(label) && !spec.dynamic) {
    findings.push({ page: spec.route, locale, kind: "missing-route-label", detail: label });
  }
  if (locale !== "zh-CN") {
    const zhLabel = messages["zh-CN"][spec.labelKey];
    if (zhLabel && HAN.test(zhLabel) && body.includes(zhLabel)) {
      findings.push({ page: spec.route, locale, kind: "chinese-label-leak", detail: zhLabel });
    }
  }
  if (locale !== "ja-JP") {
    const jaLabel = messages["ja-JP"][spec.labelKey];
    if (jaLabel && KANA.test(jaLabel) && body.includes(jaLabel)) {
      findings.push({ page: spec.route, locale, kind: "japanese-label-leak", detail: jaLabel });
    }
  }
  return lines;
}

test.describe.configure({ mode: "serial" });
test.setTimeout(20 * 60 * 1000);

test("sweep every page in zh-CN / ja-JP / en-US", async ({ browser, request }) => {
  const tokens = {
    assessor: await login(request, "assessor"),
    org_manager: await login(request, "org_manager"),
    respondent: await login(request, "respondent")
  };

  // Resolve concrete ids so the detail routes are exercised too.
  const taskList = await request.get(`${API}/api/v1/my/tasks`, {
    headers: { Authorization: `Bearer ${tokens.respondent.accessToken}` }
  });
  const tasks = ((await taskList.json()).data ?? []) as Array<{ taskId: number; status: string }>;
  // An OVERDUE task answers TASK_EXPIRED, which is correct behaviour but not a
  // useful i18n sample; prefer an answerable task and fall back to any.
  const firstTaskId = (tasks.find((task) => task.status === "IN_PROGRESS") ?? tasks[0])?.taskId;
  const reportList = await request.get(`${API}/api/v1/reports/my`, {
    headers: { Authorization: `Bearer ${tokens.respondent.accessToken}` }
  });
  const firstReportId = (await reportList.json()).data?.[0]?.reportId;

  for (const spec of PAGES) {
    for (const locale of LOCALES) {
      const route =
        spec.dynamic === "task" && firstTaskId
          ? `/my/tasks/${firstTaskId}`
          : spec.dynamic === "report" && firstReportId
            ? `/reports/${firstReportId}`
            : spec.route;
      const context = await browser.newContext();
      const page = await context.newPage();
      const consoleErrors: string[] = [];
      page.on("console", (message) => {
        if (message.type() === "error") consoleErrors.push(message.text());
      });
      const apiErrors: string[] = [];
      page.on("response", (response) => {
        const url = response.url();
        if (response.status() >= 400 && url.includes("/api/")) {
          apiErrors.push(`${response.status()} ${response.request().method()} ${url.replace(/^https?:\/\/[^/]+/, "")}`);
        }
      });
      await prime(page, spec.account, PASSWORD, tokens[spec.account], locale);
      await page.goto(route, { waitUntil: "domcontentloaded" });
      await page.waitForSelector(".ant-layout, .ant-result, .ant-card", { timeout: 20_000 }).catch(() => undefined);
      await page.waitForTimeout(900);
      const lines = await capture(page, spec, locale);
      if (lines.length < 2) {
        findings.push({ page: route, locale, kind: "empty-page", detail: "page rendered no text" });
      }
      for (const item of apiErrors) {
        findings.push({ page: route, locale, kind: "api-error", detail: item });
      }
      for (const error of consoleErrors.filter((item) => !item.includes("favicon"))) {
        findings.push({ page: route, locale, kind: "console-error", detail: error.slice(0, 200) });
      }
      await context.close();
    }

    // Cross-locale comparison: short chrome-like lines identical in all three
    // locales are candidates for untranslated text.
    const [zh, ja, en] = LOCALES.map((locale) => captured.get(`${spec.route}:${locale}`) ?? new Set<string>());
    const suspicious = [...zh].filter((line) => {
      const chromeLike = line.length >= 2 && line.length <= 24 && !/\d/.test(line) && !line.startsWith("#") && !/^MT[- \u2019']/.test(line);
      return chromeLike && ja.has(line) && en.has(line);
    });
    for (const line of suspicious) {
      findings.push({ page: spec.route, locale: "zh-CN", kind: "identical-across-locales", detail: line });
    }
  }

  fs.mkdirSync(REPORT_DIR, { recursive: true });
  fs.writeFileSync(
    path.join(REPORT_DIR, "report.json"),
    JSON.stringify({ generatedAt: new Date().toISOString(), pageCount: PAGES.length, findings }, null, 2)
  );
  const summary = new Map<string, number>();
  for (const finding of findings) {
    summary.set(finding.kind, (summary.get(finding.kind) ?? 0) + 1);
  }
  console.log(`pages=${PAGES.length} locales=${LOCALES.length} findings=${findings.length}`);
  for (const [kind, count] of summary) console.log(`  ${kind}: ${count}`);
  expect(findings.filter((item) => ["raw-i18n-key", "broken-value", "missing-route-label", "empty-page"].includes(item.kind))).toEqual([]);
});
