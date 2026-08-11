import type { ScaleDetail } from "../scales/api";
import type {
  ScalePackageAlgorithmBinding,
  ScalePackageGovernance,
  ScalePackageQualityPolicy,
  ScalePackageSnapshot,
  UpdateScalePackageRequest
} from "./api";

export const SCALE_PACKAGE_LOCALES = ["zh-CN", "ja-JP", "en"] as const;
export const REVIEW_STATUSES = ["DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED"] as const;

const SCALE_PACKAGE_IMPORT_ISSUE_I18N_KEYS: Record<string, string> = {
  PACKAGE_JSON_INVALID: "scales.packageImport.issue.invalidJson",
  PACKAGE_FORMAT_UNSUPPORTED: "scales.packageImport.issue.formatUnsupported",
  PACKAGE_SCHEMA_UNSUPPORTED: "scales.packageImport.issue.schemaUnsupported",
  PACKAGE_SCALE_ID_MISMATCH: "scales.packageImport.issue.scaleIdMismatch",
  PACKAGE_SCALE_HASH_INVALID: "scales.packageImport.issue.scaleHashInvalid",
  PACKAGE_DIMENSION_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_QUESTION_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_OPTION_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_RESULT_RULE_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_HIGH_RISK_RULE_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_NORM_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_GOLDEN_CASE_REFERENCE_INVALID: "scales.packageImport.issue.referenceInvalid",
  PACKAGE_RELEASE_FINGERPRINT_MISMATCH: "scales.packageImport.issue.releaseFingerprintMismatch",
  PACKAGE_PAYLOAD_HASH_MISMATCH: "scales.packageImport.issue.payloadHashMismatch",
  PACKAGE_TRANSLATION_MISSING: "scales.packageImport.issue.translationMissing",
  PACKAGE_AUTHORIZATION_REVIEW_REQUIRED: "scales.packageImport.issue.authorizationReviewRequired",
  PACKAGE_EXTERNAL_APPROVAL_NOT_TRANSFERRED: "scales.packageImport.issue.externalApprovalNotTransferred"
};

export function resolveScalePackageImportIssueMessage(
  errorCode: string,
  storedMessage: string,
  translate: (key: string) => string
) {
  const key = SCALE_PACKAGE_IMPORT_ISSUE_I18N_KEYS[errorCode];
  return key ? translate(key) : storedMessage;
}

const defaultGovernance: ScalePackageGovernance = {
  copyrightStatus: "PENDING_REVIEW",
  authorizationStatus: "PENDING_REVIEW",
  governanceStatus: "DRAFT"
};

const defaultQualityPolicy: ScalePackageQualityPolicy = {
  missingAnswerPolicy: "REJECT",
  maxMissingRatio: 0,
  minimumDurationSeconds: null,
  maximumDurationSeconds: null,
  invalidResultAction: "INVALIDATE",
  requireAllRequiredAnswers: true
};

const defaultAlgorithmBinding: ScalePackageAlgorithmBinding = {
  algorithmCode: "GENERIC_SCORE_CALCULATOR",
  algorithmVersion: "1",
  implementationType: "BUILTIN",
  inputSchemaJson: "{}",
  outputSchemaJson: "{}",
  implementationChecksum: null,
  reviewStatus: "DRAFT"
};

function byKey<T>(items: T[], key: (item: T) => string) {
  return new Map(items.map((item) => [key(item), item]));
}

export function buildScalePackageDraft(
  snapshot: ScalePackageSnapshot,
  scale: ScaleDetail
): UpdateScalePackageRequest {
  const scaleTranslations = byKey(snapshot.translations, (item) => item.localeCode);
  const dimensionTranslations = byKey(snapshot.dimensionTranslations, (item) => `${item.dimensionId}:${item.localeCode}`);
  const questionTranslations = byKey(snapshot.questionTranslations, (item) => `${item.questionId}:${item.localeCode}`);
  const optionTranslations = byKey(snapshot.optionTranslations, (item) => `${item.optionId}:${item.localeCode}`);
  const resultTranslations = byKey(snapshot.resultRuleTranslations, (item) => `${item.resultRuleId}:${item.localeCode}`);
  const highRiskTranslations = byKey(snapshot.highRiskRuleTranslations ?? [], (item) => `${item.highRiskRuleId}:${item.localeCode}`);
  const normGovernance = new Map(snapshot.normGovernance.map((item) => [item.normId, item]));

  return {
    governance: { ...defaultGovernance, ...snapshot.governance },
    translations: SCALE_PACKAGE_LOCALES.map((localeCode) =>
      scaleTranslations.get(localeCode) ?? { localeCode, scaleName: "", reviewStatus: "DRAFT" }
    ),
    dimensionTranslations: scale.dimensions.flatMap((dimension) =>
      SCALE_PACKAGE_LOCALES.map((localeCode) =>
        dimensionTranslations.get(`${dimension.id}:${localeCode}`) ?? {
          dimensionId: dimension.id,
          localeCode,
          dimensionName: "",
          description: "",
          reviewStatus: "DRAFT"
        }
      )
    ),
    questionTranslations: scale.questions.flatMap((question) =>
      SCALE_PACKAGE_LOCALES.map((localeCode) =>
        questionTranslations.get(`${question.id}:${localeCode}`) ?? {
          questionId: question.id,
          localeCode,
          questionTitle: "",
          textInputPlaceholder: "",
          reviewStatus: "DRAFT"
        }
      )
    ),
    optionTranslations: scale.questions.flatMap((question) =>
      question.options.flatMap((option) =>
        SCALE_PACKAGE_LOCALES.map((localeCode) =>
          optionTranslations.get(`${option.id}:${localeCode}`) ?? {
            optionId: option.id,
            localeCode,
            optionLabel: "",
            reviewStatus: "DRAFT"
          }
        )
      )
    ),
    resultRuleTranslations: scale.resultRules.flatMap((rule) =>
      SCALE_PACKAGE_LOCALES.map((localeCode) =>
        resultTranslations.get(`${rule.id}:${localeCode}`) ?? {
          resultRuleId: rule.id,
          localeCode,
          resultTitle: "",
          resultDescription: "",
          suggestionText: "",
          reviewStatus: "DRAFT"
        }
      )
    ),
    highRiskRuleTranslations: (scale.highRiskRules ?? []).flatMap((rule) =>
      SCALE_PACKAGE_LOCALES.map((localeCode) =>
        highRiskTranslations.get(`${rule.id}:${localeCode}`) ?? {
          highRiskRuleId: rule.id,
          localeCode,
          resultTitle: "",
          resultDescription: "",
          suggestionText: "",
          reviewStatus: "DRAFT"
        }
      )
    ),
    qualityPolicy: { ...defaultQualityPolicy, ...snapshot.qualityPolicy },
    validityRules: snapshot.validityRules,
    algorithmBinding: { ...defaultAlgorithmBinding, ...snapshot.algorithmBinding },
    normGovernance: scale.norms.map(
      (norm) => normGovernance.get(norm.id) ?? { normId: norm.id, reviewStatus: "PENDING_REVIEW" }
    )
  };
}

export function isValidJson(value: unknown) {
  if (typeof value !== "string") return false;
  try {
    JSON.parse(value);
    return true;
  } catch {
    return false;
  }
}

export function sanitizeScalePackageDraft(draft: UpdateScalePackageRequest): UpdateScalePackageRequest {
  return {
    ...draft,
    translations: draft.translations.filter((item) => item.scaleName.trim()),
    dimensionTranslations: draft.dimensionTranslations.filter((item) => item.dimensionName.trim()),
    questionTranslations: draft.questionTranslations.filter((item) => item.questionTitle.trim()),
    optionTranslations: draft.optionTranslations.filter((item) => item.optionLabel.trim()),
    resultRuleTranslations: draft.resultRuleTranslations.filter((item) => item.resultTitle.trim()),
    highRiskRuleTranslations: draft.highRiskRuleTranslations.filter((item) => item.resultTitle.trim()),
    validityRules: draft.validityRules.filter((item) => item.ruleCode.trim() && item.ruleVersion.trim())
  };
}
