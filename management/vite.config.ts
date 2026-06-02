import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

import { defineConfig, searchForWorkspaceRoot } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Shared `shared/badge-config/src/utils/cn.ts` imports clsx/tailwind-merge
      // directly. The shared package has no node_modules, so we resolve these
      // from the app's own node_modules.
      clsx: path.resolve(fileURLToPath(new URL('.', import.meta.url)), 'node_modules/clsx'),
      'tailwind-merge': path.resolve(
        fileURLToPath(new URL('.', import.meta.url)),
        'node_modules/tailwind-merge',
      ),
    },
    dedupe: ['vue'],
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
        // Vite 7 requires manualChunks to be a function, not an object
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router'))
              return 'vue-vendor'
            if (id.includes('reka-ui') || id.includes('lucide-vue-next') || id.includes('@tabler'))
              return 'ui-vendor'
            if (id.includes('markdown-it')) return 'markdown'
          }
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
})
