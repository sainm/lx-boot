import { test } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import {
  runGenericScaleTechnicalClosure,
  type GenericScaleSourcePackage
} from "./support/run-generic-scale-technical-closure";

type Registry = {
  scales: Array<{
    scaleCode: string;
    sourcePackage: string;
    technicalClosure?: {
      profile: string;
      closureGoldenCaseCode: string;
      taskNamePrefix: string;
    };
  }>;
};

test("registered generic ScalePackage completes the reusable technical closure", async ({ page, request }) => {
  const timeoutMs = Number(process.env.PSY_E2E_SCALE_TEST_TIMEOUT_MS ?? "180000");
  test.setTimeout(Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 180_000);
  const targetScaleCode = process.env.PSY_SCALE_REGRESSION_TARGET;
  test.skip(!targetScaleCode, "PSY_SCALE_REGRESSION_TARGET selects one registered package");

  const root = resolve(process.cwd(), "..");
  const registry = JSON.parse(
    await readFile(resolve(root, "doc/scale-packages/scale-adaptation-registry.json"), "utf8")
  ) as Registry;
  const entry = registry.scales.find((item) => item.scaleCode === targetScaleCode);
  if (!entry) throw new Error(`Scale is not registered: ${targetScaleCode}`);
  if (entry.technicalClosure?.profile !== "GENERIC_SINGLE_CHOICE") {
    throw new Error(`Scale ${targetScaleCode} does not declare the GENERIC_SINGLE_CHOICE closure profile`);
  }
  const sourcePath = resolve(root, entry.sourcePackage);
  const sourceBytes = await readFile(sourcePath);
  const source = JSON.parse(sourceBytes.toString("utf8")) as GenericScaleSourcePackage;
  await runGenericScaleTechnicalClosure(
    page,
    request,
    source,
    sourceBytes,
    { sourcePath, ...entry.technicalClosure },
    test.info().workerIndex
  );
});
