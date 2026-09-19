import type { TranslateParams } from "./messages";

type Translate = (key: string, params?: TranslateParams) => string;

export type TranslateFn = Translate;

/**
 * Backend DTOs carry raw enum codes (for example `IN_PROGRESS`, `LOW`).
 * Resolve them through the shared catalogs and fall back to the code itself so
 * an unmapped value stays visible instead of rendering a missing message key.
 */
function translateFirst(t: Translate, keys: string[], code: string) {
  const trimmed = code?.trim();
  if (!trimmed) {
    return "";
  }
  for (const key of keys) {
    const translated = t(key);
    if (translated !== key) {
      return translated;
    }
  }
  return trimmed;
}

/** Generic resolver for `<prefix>.<CODE>` catalogs. */
export function translateEnum(t: Translate, prefix: string, code: string) {
  const trimmed = code?.trim();
  if (!trimmed) {
    return "";
  }
  return translateFirst(t, [`${prefix}.${trimmed}`], trimmed);
}

export function taskStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "status", code);
}

export function riskLevelLabel(t: Translate, code: string) {
  // Risk labels are shared with the group/user report surfaces so the three
  // cannot drift apart; `userReports.risk.*` keeps the item-level codes.
  const trimmed = code?.trim() ?? "";
  return translateFirst(t, [`groupReports.risk.${trimmed}`, `userReports.risk.${trimmed}`], code);
}

export function warningStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "warning.status", code);
}

export function warningPriorityLabel(t: Translate, code: string) {
  return translateEnum(t, "warning.priority", code);
}

export function reportTypeLabel(t: Translate, code: string) {
  return translateEnum(t, "report.type", code);
}

export function deliveryStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "notifications.deliveryStatusValue", code);
}

export function exportStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "export.status", code);
}

export function appointmentStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "appointment.status", code);
}

export function userStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "user.status", code);
}

export function importSeverityLabel(t: Translate, code: string) {
  return translateEnum(t, "scale.import.severity", code);
}

export function taskModeLabel(t: Translate, code: string) {
  return translateEnum(t, "task.mode", code);
}

export function scoreMethodLabel(t: Translate, code: string) {
  return translateEnum(t, "scale.scoreMethod", code);
}

export function scaleStatusLabel(t: Translate, code: string) {
  return translateFirst(t, [`scale.status.${code?.trim()}`, `status.${code?.trim()}`], code);
}

export function scheduleStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "appointment.scheduleStatus", code);
}

export function governanceOptionLabel(t: Translate, code: string) {
  return translateEnum(t, "governance.option", code);
}

export function goldenCaseTypeLabel(t: Translate, code: string) {
  return translateEnum(t, "scalePublication.caseTypeValue", code);
}

export function safetyPolicyPriorityLabel(t: Translate, code: string) {
  return translateEnum(t, "safetyPolicy.priority", code);
}

export function sessionStatusLabel(t: Translate, code: string) {
  return translateEnum(t, "authAudit.sessionStatus", code);
}

export function auditResultLabel(t: Translate, code: string) {
  return translateEnum(t, "authAudit.resultValue", code);
}

export function deviceTrustLabel(t: Translate, code: string) {
  return translateEnum(t, "authAudit.deviceTrustValue", code);
}

export function autoDispositionLabel(t: Translate, code: string) {
  return translateEnum(t, "authAudit.autoDispositionValue", code);
}

export function exportFormatLabel(t: Translate, code: string) {
  return translateEnum(t, "export.format", code);
}
