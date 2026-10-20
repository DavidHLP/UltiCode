import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  base: process.env.VITE_LANDING_BASE || "/landing/",
  plugins: [vue(), tailwindcss()],
  resolve: {
    dedupe: ["vue", "@vue/runtime-core", "reka-ui"],
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    host: true,
    port: 9004,
    strictPort: true,
  },
});
