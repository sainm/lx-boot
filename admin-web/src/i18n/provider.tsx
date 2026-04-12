import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, translateMessage, type SupportedLocale, type TranslateParams } from "./messages";

type I18nContextValue = {
  locale: SupportedLocale;
  setLocale: (locale: SupportedLocale) => void;
  t: (key: string, params?: TranslateParams) => string;
};

const I18nContext = createContext<I18nContextValue | null>(null);

function readStoredLocale(): SupportedLocale {
  if (typeof window === "undefined") {
    return DEFAULT_LOCALE;
  }
  const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  return stored === "en-US" || stored === "zh-CN" ? stored : DEFAULT_LOCALE;
}

export function I18nProvider({ children }: PropsWithChildren) {
  const [locale, setLocaleState] = useState<SupportedLocale>(readStoredLocale);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    window.document.documentElement.lang = locale;
  }, [locale]);

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale: (nextLocale) => setLocaleState(nextLocale),
      t: (key, params) => translateMessage(locale, key, params)
    }),
    [locale]
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n must be used within I18nProvider");
  }
  return context;
}
