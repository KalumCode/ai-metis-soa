/**
 * 后端 API 地址。
 *
 * - 桌面壳（WebView2）：壳通过 AddScriptToExecuteOnDocumentCreatedAsync 注入
 *   window.__METIS_CONFIG__ = { backendUrl: "http://..." }（来自 %APPDATA%/Metis/config.json）
 * - Vite 开发模式：index.html 里的占位脚本 backendUrl 为空字符串，走 Vite 代理
 */
export function getBackendUrl(): string {
  return window.__METIS_CONFIG__?.backendUrl ?? "";
}
