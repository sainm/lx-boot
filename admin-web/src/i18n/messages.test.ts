import { describe, expect, it } from "vitest";
import { isSupportedLocale, messages, SUPPORTED_LOCALES, translateMessage } from "./messages";

function placeholders(value: string) {
  return [...value.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();
}

describe("i18n messages", () => {
  it("recognizes all three supported locales", () => {
    expect(isSupportedLocale("zh-CN")).toBe(true);
    expect(isSupportedLocale("ja-JP")).toBe(true);
    expect(isSupportedLocale("en-US")).toBe(true);
    expect(isSupportedLocale("fr-FR")).toBe(false);
  });

  it("translates Japanese messages and interpolates parameters", () => {
    expect(translateMessage("ja-JP", "login.signIn")).toBe("\u30ED\u30B0\u30A4\u30F3");
    expect(translateMessage("ja-JP", "register.passwordRule", { count: 10 })).toBe(
      "\u30D1\u30B9\u30EF\u30FC\u30C9\u306F 10 \u6587\u5B57\u4EE5\u4E0A\u306B\u3057\u3066\u304F\u3060\u3055\u3044"
    );
  });

  it("keeps every locale catalog complete", () => {
    const canonicalKeys = Object.keys(messages["en-US"]).sort();
    for (const locale of SUPPORTED_LOCALES) {
      expect(Object.keys(messages[locale]).sort(), locale).toEqual(canonicalKeys);
    }
  });

  it("preserves interpolation parameters in every locale", () => {
    for (const [key, englishValue] of Object.entries(messages["en-US"])) {
      for (const locale of SUPPORTED_LOCALES) {
        expect(placeholders(messages[locale][key]), `${locale}:${key}`).toEqual(placeholders(englishValue));
      }
    }
  });
});
