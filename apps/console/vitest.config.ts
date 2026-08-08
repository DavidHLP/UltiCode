import { fileURLToPath } from "node:url";
import { configDefaults, defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";
import vueJsx from "@vitejs/plugin-vue-jsx";

export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    // Mock virtual:pwa-register for tests
    {
      name: "virtual-pwa-register-mock",
      resolveId(id) {
        if (id === "virtual:pwa-register") {
          return "\0virtual:pwa-register";
        }
      },
      load(id) {
        if (id === "\0virtual:pwa-register") {
          return `
            export function registerSW(options = {}) {
              const updateSW = (reloadPage = false) => {
                if (reloadPage) {
                  window.location.reload();
                }
              };
              if (options.onNeedRefresh) options.onNeedRefresh();
              if (options.onOfflineReady) options.onOfflineReady();
              if (options.onRegistered) options.onRegistered(undefined);
              return updateSW;
            }
          `;
        }
      },
    },
  ],
  test: {
    environment: "jsdom",
    exclude: [...configDefaults.exclude, "e2e/**", "**/packages/**"],
    root: fileURLToPath(new URL("./src", import.meta.url)),
    globals: true,
  },
  resolve: {
    alias: {
      // @/shared must be matched before the catch-all @ alias
      "@/shared": fileURLToPath(new URL("../../packages", import.meta.url)),
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      // Files inside packages/ (axiosCsrfInterceptor.ts, utils.ts, etc.)
      // import their runtime deps as bare specifiers. Resolve them from
      // apps/console/node_modules rather than letting vite walk up from the
      // packages/ file's physical location (which has no node_modules).
      axios: fileURLToPath(new URL("./node_modules/axios", import.meta.url)),
      clsx: fileURLToPath(new URL("./node_modules/clsx", import.meta.url)),
      "tailwind-merge": fileURLToPath(new URL("./node_modules/tailwind-merge", import.meta.url)),
      "lucide-vue-next": fileURLToPath(new URL("./node_modules/lucide-vue-next", import.meta.url)),
    },
  },
});
