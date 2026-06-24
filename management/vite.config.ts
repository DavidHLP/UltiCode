import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

import { defineConfig, searchForWorkspaceRoot } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: [
      // Most-specific first: @/shared must be matched before the
      // catch-all `@` → ./src alias rewrites the path to <management>/src/shared/...
      // (where `shared` is a broken plain-text file on this checkout, not a
      // symlink). Vite's resolve.alias uses startsWith matching in
      // declaration order, so order matters.
      {
        find: '@/shared',
        replacement: fileURLToPath(new URL('../shared', import.meta.url)),
      },
      // Catch-all `@` → ./src
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      // Files under shared/ (including the auth-ui & sidebar-menu components)
      // import their runtime + peer deps as bare specifiers. The shared
      // packages have no node_modules, so resolve them from the app's own
      // node_modules — clsx/tailwind-merge/axios/lucide-vue-next plus the
      // peer deps vue-router/vue-i18n/reka-ui consumed by shared/auth-ui &
      // shared/sidebar-menu. `vue` itself is handled by the plugin + dedupe.
      {
        find: /^clsx$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/clsx',
        ),
      },
      {
        find: /^tailwind-merge$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/tailwind-merge',
        ),
      },
      {
        find: /^axios$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/axios',
        ),
      },
      {
        find: /^lucide-vue-next$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/lucide-vue-next',
        ),
      },
      {
        find: /^reka-ui$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/reka-ui',
        ),
      },
      {
        find: /^vue-router$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/vue-router',
        ),
      },
      {
        find: /^vue-i18n$/,
        replacement: path.resolve(
          fileURLToPath(new URL('.', import.meta.url)),
          'node_modules/vue-i18n',
        ),
      },
    ],
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
