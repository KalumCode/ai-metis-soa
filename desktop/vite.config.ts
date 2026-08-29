import path from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/**
 * 开发时把 /api 代理到本地 Java 后端（server/ 默认 8080），
 * 后端端口可用 VITE_BACKEND_PORT 覆盖。
 */
function resolveDevBackendTarget(): string {
  const backendPort = Number.parseInt(process.env.VITE_BACKEND_PORT || "8080", 10);
  if (!Number.isInteger(backendPort) || backendPort < 1 || backendPort > 65535) {
    throw new Error(`Invalid VITE_BACKEND_PORT: ${backendPort}`);
  }
  return `http://127.0.0.1:${backendPort}`;
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: resolveDevBackendTarget(),
        changeOrigin: true,
      },
    },
  },
});
