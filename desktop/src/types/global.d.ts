export {};

declare global {
  interface Window {
    /** 桌面壳注入的运行配置（见 desktop/src/lib/api/backend-url.ts）。 */
    __METIS_CONFIG__?: { backendUrl?: string };
  }
}
