import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, searchForWorkspaceRoot, type PluginOption } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import Icons from 'unplugin-icons/vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  // Fix for sockjs-client: 'global is not defined' in browser
  define: {
    global: 'globalThis',
  },
  plugins: [
    vue(),
    vueJsx(),
    tailwindcss(),
    Icons({ compiler: 'vue3', autoInstall: true }) as PluginOption,
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.ico', 'robots.txt', 'apple-touch-icon.png'],
      manifest: {
        name: 'UltiCode',
        short_name: 'UltiCode',
        description: 'Online coding competition platform',
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        maximumFileSizeToCacheInBytes: 7 * 1024 * 1024, // 7 MB for Monaco Editor
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-cache',
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 year
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'gstatic-fonts-cache',
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 year
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
        ],
      },
      devOptions: {
        enabled: false, // Disable in dev for faster HMR
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any -- VitePWA plugin types are complex
    }) as any,
  ],
  resolve: {
    alias: [
      // Most-specific first: @/shared must be matched before the
      // catch-all `@` → ./src alias rewrites the path to <console>/src/shared/...
      // (where `shared` is a broken plain-text file on this checkout, not a
      // symlink). Vite's resolve.alias uses startsWith matching in
      // declaration order, so order matters.
      {
        find: '@/shared',
        replacement: fileURLToPath(new URL('../shared', import.meta.url)),
      },
      // Catch-all `@` → ./src
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      // Files inside shared/ (axiosCsrfInterceptor.ts, utils.ts, and the
      // auth-ui / sidebar-menu components) import their runtime + peer deps
      // as bare specifiers. Resolve them from console/node_modules rather
      // than letting vite walk up from the shared/ file's physical location
      // (which has no node_modules and fails to resolve on the Docker CI
      // build). Covers axios, clsx, tailwind-merge, lucide-vue-next, and the
      // peer deps vue-router / vue-i18n / reka-ui consumed by shared/auth-ui
      // & shared/sidebar-menu. `vue` itself is resolved by the plugin, so it
      // needs no alias here.
      { find: /^axios$/, replacement: fileURLToPath(new URL('./node_modules/axios', import.meta.url)) },
      { find: /^clsx$/, replacement: fileURLToPath(new URL('./node_modules/clsx', import.meta.url)) },
      { find: /^tailwind-merge$/, replacement: fileURLToPath(new URL('./node_modules/tailwind-merge', import.meta.url)) },
      { find: /^lucide-vue-next$/, replacement: fileURLToPath(new URL('./node_modules/lucide-vue-next', import.meta.url)) },
      { find: /^vue-router$/, replacement: fileURLToPath(new URL('./node_modules/vue-router', import.meta.url)) },
      { find: /^vue-i18n$/, replacement: fileURLToPath(new URL('./node_modules/vue-i18n', import.meta.url)) },
      { find: /^reka-ui$/, replacement: fileURLToPath(new URL('./node_modules/reka-ui', import.meta.url)) },
    ],
  },
  server: {
    port: 9002,
    fs: {
      // Allow serving files from workspace root for pnpm monorepo
      // This enables access to .pnpm directory for font files (e.g., KaTeX)
      allow: [
        searchForWorkspaceRoot(process.cwd()),
        // Allow pnpm store for KaTeX and other package fonts
        // Note: searchForWorkspaceRoot may return project subdir in monorepo,
        // so we explicitly include the parent workspace root's .pnpm directory
        path.resolve(process.cwd(), '../node_modules/.pnpm'),
      ],
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Vite 7 requires manualChunks to be a function, not an object
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('monaco-editor')) return 'monaco-editor'
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vue-vendor'
            if (id.includes('reka-ui') || id.includes('lucide-vue-next') || id.includes('@tabler')) return 'ui-vendor'
            if (id.includes('markdown-it') || id.includes('highlight.js') || id.includes('katex')) return 'markdown'
          }
        },
      },
    },
    // Increase chunk size warning limit
    chunkSizeWarningLimit: 1000,
    // Disable Vite's modulePreload polyfill: it is the only inline <script>
    // Vite injects at build time. Disabling it keeps the production bundle
    // free of inline scripts so CSP `script-src 'self'` (nginx.conf) applies
    // with zero violations. Modern browsers support modulepreload natively
    // (Chrome 66+, FF 78+, Safari 16+); UltiCode targets only those.
    modulePreload: { polyfill: false },
  },
})
