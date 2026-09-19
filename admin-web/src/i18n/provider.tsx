import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, isSupportedLocale, translateMessage, type SupportedLocale, type TranslateParams } from "./messages";

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
  return isSupportedLocale(stored) ? stored : DEFAULT_LOCALE;
}

export function I18nProvider({ children }: PropsWithChildren) {
  const [locale, setLocaleState] = useState<SupportedLocale>(readStoredLocale);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    window.document.documentElement.lang = locale;
  }, [locale]);

  const setLocale = useCallback(
    (nextLocale: SupportedLocale) => {
      if (nextLocale === locale) {
        return;
      }
      setLocaleState(nextLocale);
      // Backend-localized payloads (dashboard labels, enum text) must be
      // refetched with the new Accept-Language header.
      void queryClient.invalidateQueries();
    },
    [locale, queryClient]
  );

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale,
      t: (key, params) => translateMessage(locale, key, params)
    }),
    [locale, setLocale]
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
