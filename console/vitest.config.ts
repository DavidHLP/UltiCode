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
    exclude: [...configDefaults.exclude, "e2e/**"],
    root: fileURLToPath(new URL("./src", import.meta.url)),
    globals: true,
    setupFiles: ["./test/setup.ts"],
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
