import { fileURLToPath, URL } from "node:url";
import { configDefaults, defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    exclude: [...configDefaults.exclude],
    root: fileURLToPath(new URL("./src", import.meta.url)),
    globals: true,
    coverage: { provider: "v8", reportsDirectory: "../coverage" },
  },
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
});
