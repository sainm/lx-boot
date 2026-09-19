/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"]
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true
      },
      // Keep the API prefix narrow: "/auth" (no trailing slash) also matched the
      // SPA route "/auth-audit" and swallowed it into the backend 401.
      "/auth/": {
        target: "http://localhost:8090",
        changeOrigin: true,
        // The SSO callback page is an SPA route living under /auth.
        bypass: (req) => (req.url?.startsWith("/auth/sso/callback") ? "/index.html" : undefined)
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, "/");
          if (!normalizedId.includes("node_modules")) {
            return;
          }

          if (normalizedId.includes("/axios/")) {
            return "data";
          }

          if (
            normalizedId.includes("/react/") ||
            normalizedId.includes("/react-dom/") ||
            normalizedId.includes("/scheduler/") ||
            normalizedId.includes("/react-router-dom/") ||
            normalizedId.includes("/react-router/") ||
            normalizedId.includes("/@remix-run/router/") ||
            normalizedId.includes("/@tanstack/react-query/")
          ) {
            return "framework";
          }
        }
      }
    },
    chunkSizeWarningLimit: 1200
  }
});
