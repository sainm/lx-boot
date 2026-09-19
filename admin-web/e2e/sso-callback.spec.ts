/**
 * Browser leg of the SSO flow (MT-NET-003 / MT-AUTH-027).
 *
 * Drives the CAS chain through the API (authorize → IdP login → app callback),
 * then opens the SPA callback route with the one-time ticket and asserts the
 * browser lands in an authenticated shell.
 */
import { expect, test, type APIRequestContext } from "@playwright/test";

const API = process.env.PSY_E2E_API_URL ?? "http://127.0.0.1:8090";
const CAS = process.env.PSY_E2E_CAS_URL ?? "http://127.0.0.1:9200";

async function freshAppTicket(request: APIRequestContext) {
  const authorize = await request.get(`${API}/auth/sso/cas/authorize`, { maxRedirects: 0 });
  expect(authorize.status()).toBe(302);
  const idpLogin = authorize.headers()["location"];
  expect(idpLogin).toContain(`${CAS}/cas/login`);
  const idp = await request.get(idpLogin, { maxRedirects: 0 });
  expect(idp.status()).toBe(302);
  const appCallback = idp.headers()["location"];
  const callback = await request.get(appCallback, { maxRedirects: 0 });
  expect(callback.status()).toBe(302);
  const frontend = callback.headers()["location"];
  const ticket = new URL(frontend).searchParams.get("ticket");
  expect(ticket, "frontend callback must carry a one-time ticket").toBeTruthy();
  return { ticket: ticket as string, frontend };
}

test.setTimeout(2 * 60 * 1000);

test("SSO callback exchanges the ticket and opens the session", async ({ browser, request }) => {
  const { ticket, frontend } = await freshAppTicket(request);
  expect(frontend).toContain("/auth/sso/callback");

  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`/auth/sso/callback?ticket=${ticket}`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4000);

  const url = page.url();
  const text = await page.evaluate(() => document.body.innerText);
  expect(url, "SSO callback must leave the login page").not.toContain("/login");
  expect(text.length, "authenticated shell must render content").toBeGreaterThan(20);
  expect(new URL(url).pathname === "/home" || new URL(url).pathname.startsWith("/home")).toBeTruthy();
  console.log(`sso callback -> ${url}`);
  await context.close();
});
