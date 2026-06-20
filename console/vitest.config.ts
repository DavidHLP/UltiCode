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
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      // The local monorepo layout has `console/src/shared` as a symlink to
      // the repo-root `shared/`. CI's `actions/checkout@v6` does not
      // preserve that symlink, so `@/shared/...` would resolve into a
      // missing path. Alias directly to the real repo-root location so
      // tests pass on machines where the symlink is intact (local dev)
      // and on CI where it has been replaced with a regular directory.
      "@/shared": fileURLToPath(new URL("../shared", import.meta.url)),
      // Files inside shared/ (axiosCsrfInterceptor.ts, utils.ts, etc.)
      // import their runtime deps as bare specifiers. Resolve them from
      // console/node_modules rather than letting vite walk up from the
      // shared/ file's physical location (which has no node_modules and
      // would otherwise fail to resolve axios, clsx, tailwind-merge,
      // lucide-vue-next on CI). Peer deps (vue / vue-i18n / vue-router)
      // are already pinned in console/package.json so vite's normal
      // resolution finds them.
      axios: fileURLToPath(new URL("./node_modules/axios", import.meta.url)),
      clsx: fileURLToPath(new URL("./node_modules/clsx", import.meta.url)),
      "tailwind-merge": fileURLToPath(new URL("./node_modules/tailwind-merge", import.meta.url)),
      "lucide-vue-next": fileURLToPath(new URL("./node_modules/lucide-vue-next", import.meta.url)),
    },
  },
});
