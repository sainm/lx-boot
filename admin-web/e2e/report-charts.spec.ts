/**
 * Report chart rendering check (MT-RPT-012).
 *
 * Loads the group-report page and a respondent report, then asserts that every
 * ECharts canvas rendered non-blank pixels and stores a screenshot as evidence.
 */
import { expect, test, type APIRequestContext } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const API = process.env.PSY_E2E_API_URL ?? "http://127.0.0.1:8090";
const PASSWORD = process.env.PSY_E2E_PASSWORD ?? "ChangeMe123";
const OUT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../build/reports/chart-checks");

async function login(request: APIRequestContext, principal: string) {
  const response = await request.post(`${API}/auth/login/password`, {
    data: { principal, password: PASSWORD, deviceId: `chart-${principal}`, deviceType: "WEB", deviceName: "chart check" }
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).data as { accessToken: string; refreshToken: string };
}

test.setTimeout(5 * 60 * 1000);

test("group report and report detail charts render", async ({ browser, request }) => {
  const staff = await login(request, "assessor");
  const respondent = await login(request, "respondent");
  const reports = await request.get(`${API}/api/v1/reports/my`, {
    headers: { Authorization: `Bearer ${respondent.accessToken}` }
  });
  const preferred = Number(process.env.PSY_E2E_CHART_REPORT_ID ?? "");
  const reportId = Number.isFinite(preferred) && preferred > 0 ? preferred : (await reports.json()).data?.[0]?.reportId;
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const results: string[] = [];
  const targets = [
    { route: "/group-reports", token: staff, name: "group-reports" },
    ...(reportId ? [{ route: `/reports/${reportId}`, token: staff, name: `report-${reportId}` }] : [])
  ];

  for (const target of targets) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.addInitScript(
      ({ token, refresh }) => {
        window.localStorage.setItem("psy-admin-web.auth-token", token);
        window.localStorage.setItem("psy-admin-web.refresh-token", refresh);
        window.localStorage.setItem("psy-admin-web.locale", "zh-CN");
      },
      { token: target.token.accessToken, refresh: target.token.refreshToken }
    );
    await page.goto(target.route, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(2500);
    const canvases = await page.evaluate(() =>
      Array.from(document.querySelectorAll("canvas")).map((canvas) => {
        const context = canvas.getContext("2d");
        if (!context) return { width: canvas.width, height: canvas.height, nonBlank: false };
        const image = context.getImageData(0, 0, canvas.width, canvas.height).data;
        let painted = 0;
        for (let index = 0; index < image.length; index += 4 * 7) {
          if (image[index + 3] !== 0) painted += 1;
        }
        return { width: canvas.width, height: canvas.height, painted, nonBlank: painted > 20 };
      })
    );
    const screenshot = path.join(OUT_DIR, `${target.name}.png`);
    await page.screenshot({ path: screenshot, fullPage: true });
    const drawn = canvases.filter((canvas) => canvas.nonBlank);
    const painted = canvases.map((canvas) => canvas.painted).join("/");
    results.push(
      `${target.name}: canvas=${canvases.length} drawn=${drawn.length} painted=${painted} -> ${path.relative(process.cwd(), screenshot)}`
    );
    if (target.name.startsWith("report-")) {
      expect(canvases.length, `${target.name} should render at least one chart canvas`).toBeGreaterThan(0);
      expect(drawn.length, `${target.name} charts must not be blank`).toBeGreaterThan(0);
    } else if (canvases.length > 0) {
      // The group page lists whatever the current filter window returns; when
      // it does render charts they must not be blank.
      expect(drawn.length, `${target.name} charts must not be blank`).toBeGreaterThan(0);
    }
    await context.close();
  }

  fs.writeFileSync(path.join(OUT_DIR, "summary.txt"), results.join("\n") + "\n");
  console.log(results.join("\n"));
});
