import { fileURLToPath, URL } from 'node:url'

import { defineConfig, searchForWorkspaceRoot } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 9003,
    fs: {
      // Allow serving files from workspace root for pnpm monorepo
      // This enables access to .pnpm directory for font files (e.g., KaTeX)
      allow: [searchForWorkspaceRoot(process.cwd())],
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Vue ecosystem
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          // UI libraries
          'ui-vendor': ['reka-ui', 'lucide-vue-next', '@tabler/icons-vue'],
          // Markdown
          markdown: ['markdown-it'],
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
})
