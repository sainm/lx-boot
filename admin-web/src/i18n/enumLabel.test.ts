import { describe, expect, it } from "vitest";
import { translateMessage, type SupportedLocale } from "./messages";
import {
  appointmentStatusLabel,
  deliveryStatusLabel,
  exportStatusLabel,
  importSeverityLabel,
  reportTypeLabel,
  riskLevelLabel,
  taskStatusLabel,
  translateEnum,
  userStatusLabel,
  warningPriorityLabel,
  warningStatusLabel
} from "./enumLabel";

function translator(locale: SupportedLocale) {
  return (key: string) => translateMessage(locale, key);
}

describe("enum labels", () => {
  it("localizes task status codes in every locale", () => {
    expect(taskStatusLabel(translator("zh-CN"), "IN_PROGRESS")).toBe("\u8FDB\u884C\u4E2D");
    expect(taskStatusLabel(translator("ja-JP"), "IN_PROGRESS")).toBe("\u9032\u884C\u4E2D");
    expect(taskStatusLabel(translator("en-US"), "IN_PROGRESS")).toBe("In Progress");
    expect(taskStatusLabel(translator("zh-CN"), "CLOSED")).toBe("\u5DF2\u5173\u95ED");
  });

  it("reuses the shared risk catalog for risk levels and warning levels", () => {
    expect(riskLevelLabel(translator("zh-CN"), "LOW")).toBe("\u4F4E\u98CE\u9669");
    expect(riskLevelLabel(translator("ja-JP"), "HIGH")).toBe("\u9AD8");
    expect(riskLevelLabel(translator("en-US"), "CRITICAL")).toBe("Critical");
    // Item-level code only exists in the user report catalog.
    expect(riskLevelLabel(translator("zh-CN"), "HIGH_RISK_ITEM")).toBe("\u9AD8\u5371\u9898\u89E6\u53D1");
  });

  it("localizes warning status, priority, report type and severity codes", () => {
    expect(warningStatusLabel(translator("zh-CN"), "PROCESSING")).toBe("\u5904\u7406\u4E2D");
    expect(warningPriorityLabel(translator("ja-JP"), "P1")).toBe("P1 \u9AD8");
    expect(reportTypeLabel(translator("en-US"), "SYSTEM")).toBe("System generated");
    expect(importSeverityLabel(translator("zh-CN"), "WARNING")).toBe("\u8B66\u544A");
  });

  it("covers delivery, export, appointment and user status catalogs", () => {
    expect(deliveryStatusLabel(translator("zh-CN"), "CLICKED")).toBe("\u5DF2\u70B9\u51FB");
    expect(deliveryStatusLabel(translator("en-US"), "DEAD_LETTER")).toBe("Dead letter");
    expect(exportStatusLabel(translator("ja-JP"), "DONE")).toBe("\u5B8C\u4E86");
    expect(appointmentStatusLabel(translator("zh-CN"), "NO_SHOW")).toBe("\u672A\u5230\u8BBF");
    expect(userStatusLabel(translator("en-US"), "LOCKED")).toBe("Locked");
  });

  it("keeps unmapped codes visible instead of leaking message keys", () => {
    expect(taskStatusLabel(translator("zh-CN"), "SOMETHING_NEW")).toBe("SOMETHING_NEW");
    expect(riskLevelLabel(translator("en-US"), "UNKNOWN")).toBe("UNKNOWN");
    expect(translateEnum(translator("ja-JP"), "warning.status", "NEW_CODE")).toBe("NEW_CODE");
    expect(warningStatusLabel(translator("ja-JP"), "")).toBe("");
  });
});
