import { fileURLToPath } from "node:url";
import { configDefaults, defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    exclude: [...configDefaults.exclude, "e2e/**"],
    root: fileURLToPath(new URL("./src", import.meta.url)),
    globals: true,
    coverage: {
      provider: "v8",
      include: ["**/*.{js,jsx,ts,tsx,vue}"],
      exclude: ["**/*.d.ts", "**/coverage/**", "**/__tests__/**", "**/*.spec.*", "**/*.test.*"],
      reportsDirectory: "../coverage",
      reporter: ["text", "json-summary", "lcov"],
      thresholds: {
        statements: 46,
        branches: 43,
        functions: 39,
        lines: 49,
      },
    },
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
