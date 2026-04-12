import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ConfigProvider } from "antd";
import enUS from "antd/locale/en_US";
import zhCN from "antd/locale/zh_CN";
import { RouterProvider } from "react-router-dom";
import { SessionProvider } from "./auth/session";
import { I18nProvider, useI18n } from "./i18n/provider";
import { router } from "./router";
import "./styles.css";

const queryClient = new QueryClient();

function AppRoot() {
  const { locale, t } = useI18n();

  return (
    <ConfigProvider
      locale={locale === "zh-CN" ? zhCN : enUS}
      theme={{
        token: {
          colorPrimary: "#1f5f86",
          colorInfo: "#1f5f86",
          colorSuccess: "#2d8a5f",
          colorWarning: "#c98a18",
          colorError: "#c24c3b",
          colorBgBase: "#f4f7fb",
          colorTextBase: "#17212b",
          colorBorderSecondary: "#dfe7f0",
          borderRadius: 14,
          borderRadiusLG: 20,
          boxShadowSecondary: "0 14px 36px rgba(20, 51, 74, 0.08)",
          fontFamily: "\"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
        },
        components: {
          Button: {
            borderRadius: 14,
            controlHeightLG: 48,
            primaryShadow: "0 10px 24px rgba(31, 95, 134, 0.22)"
          },
          Card: {
            borderRadiusLG: 20
          },
          Input: {
            borderRadius: 14,
            controlHeightLG: 48
          },
          InputNumber: {
            borderRadius: 14,
            controlHeightLG: 48
          },
          Select: {
            borderRadius: 14,
            controlHeightLG: 48
          }
        }
      }}
    >
      <SessionProvider>
        <QueryClientProvider client={queryClient}>
          <React.Suspense
            fallback={
              <div style={{ padding: 24, fontFamily: "Segoe UI, PingFang SC, Microsoft YaHei, sans-serif" }}>
                {t("app.loading")}
              </div>
            }
          >
            <RouterProvider router={router} />
          </React.Suspense>
        </QueryClientProvider>
      </SessionProvider>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <I18nProvider>
      <AppRoot />
    </I18nProvider>
  </React.StrictMode>
);
