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
    exclude: [...configDefaults.exclude, "e2e/**", "**/shared/**"],
    root: fileURLToPath(new URL("./src", import.meta.url)),
    globals: true,
    server: {
      // shared/ is reached through the @/shared/... alias, but its real
      // files live at the repo root (console/src/shared -> ../../shared).
      // Without this inline list, vitest resolves bare imports inside
      // shared/* (e.g. axiosCsrfInterceptor.ts -> `import axios from
      // 'axios'`) starting from the file's physical location — which
      // has no node_modules — and fails with "Failed to resolve import".
      // Marking the prefix `shared/` as inline forces vitest to bundle
      // it from this app's resolve root so console/node_modules wins.
      deps: {
        inline: [/^shared\//],
      },
    },
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
