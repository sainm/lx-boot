import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173
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
