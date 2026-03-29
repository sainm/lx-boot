import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router-dom";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import { router } from "./router";
import { SessionProvider } from "./auth/session";
import "./styles.css";

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN}>
      <SessionProvider>
        <QueryClientProvider client={queryClient}>
          <React.Suspense
            fallback={
              <div style={{ padding: 24, fontFamily: "Segoe UI, PingFang SC, Microsoft YaHei, sans-serif" }}>
                正在加载管理端...
              </div>
            }
          >
            <RouterProvider router={router} />
          </React.Suspense>
        </QueryClientProvider>
      </SessionProvider>
    </ConfigProvider>
  </React.StrictMode>
);
