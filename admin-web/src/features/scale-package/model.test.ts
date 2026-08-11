import { describe, expect, it } from "vitest";
import type { ScaleDetail } from "../scales/api";
import type { ScalePackageSnapshot } from "./api";
import {
  buildScalePackageDraft,
  isValidJson,
  resolveScalePackageImportIssueMessage,
  sanitizeScalePackageDraft
} from "./model";

const scale = {
  id: 7,
  dimensions: [{ id: 10, dimensionCode: "D", dimensionName: "Dimension" }],
  questions: [{ id: 20, questionNo: 1, questionTitle: "Question", options: [{ id: 30, optionCode: "A", optionLabel: "A" }] }],
  resultRules: [{ id: 40, riskLevel: "NORMAL" }],
  norms: [{ id: 50, normCode: "N" }]
} as ScaleDetail;

const emptySnapshot: ScalePackageSnapshot = {
  scaleId: 7,
  governance: null,
  translations: [],
  dimensionTranslations: [],
  questionTranslations: [],
  optionTranslations: [],
  resultRuleTranslations: [],
  highRiskRuleTranslations: [],
  qualityPolicy: null,
  validityRules: [],
  algorithmBinding: null,
  normGovernance: []
};

describe("scale package draft", () => {
  it("creates explicit empty rows for all three locales without inventing translations", () => {
    const draft = buildScalePackageDraft(emptySnapshot, scale);
    expect(draft.translations).toHaveLength(3);
    expect(draft.dimensionTranslations).toHaveLength(3);
    expect(draft.questionTranslations).toHaveLength(3);
    expect(draft.optionTranslations).toHaveLength(3);
    expect(draft.resultRuleTranslations).toHaveLength(3);
    expect(draft.highRiskRuleTranslations).toHaveLength(0);
    expect(draft.translations.every((item) => item.scaleName === "" && item.reviewStatus === "DRAFT")).toBe(true);
  });

  it("preserves saved governance and translation evidence", () => {
    const draft = buildScalePackageDraft({
      ...emptySnapshot,
      governance: { copyrightStatus: "AUTHORIZED", authorizationStatus: "AUTHORIZED", governanceStatus: "APPROVED" },
      translations: [{ localeCode: "ja-JP", scaleName: "尺度", reviewStatus: "APPROVED" }]
    }, scale);
    expect(draft.governance?.copyrightStatus).toBe("AUTHORIZED");
    expect(draft.translations.find((item) => item.localeCode === "ja-JP")?.scaleName).toBe("尺度");
  });

  it("validates JSON fields before submission", () => {
    expect(isValidJson("{}" )).toBe(true);
    expect(isValidJson("{" )).toBe(false);
  });

  it("omits untouched translation placeholders from incremental draft saves", () => {
    const draft = buildScalePackageDraft(emptySnapshot, scale);
    draft.translations[0].scaleName = "中文名称";
    const sanitized = sanitizeScalePackageDraft(draft);
    expect(sanitized.translations).toHaveLength(1);
    expect(sanitized.dimensionTranslations).toHaveLength(0);
    expect(sanitized.questionTranslations).toHaveLength(0);
  });

  it("localizes persisted ScalePackage issues by stable code and preserves unknown messages", () => {
    const translate = (key: string) => `translated:${key}`;
    expect(resolveScalePackageImportIssueMessage("PACKAGE_TRANSLATION_MISSING", "旧中文消息", translate))
      .toBe("translated:scales.packageImport.issue.translationMissing");
    expect(resolveScalePackageImportIssueMessage("CUSTOM_IMPORT_ERROR", "Original detail", translate))
      .toBe("Original detail");
  });
});
