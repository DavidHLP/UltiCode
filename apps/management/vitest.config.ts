import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    root: fileURLToPath(new URL('./src', import.meta.url)),
    exclude: ['**/e2e/**', '**/node_modules/**', '**/packages/**'],
  },
  resolve: {
    alias: {
      // @/shared must be matched before the catch-all @ alias
      '@/shared': fileURLToPath(new URL('../../packages', import.meta.url)),
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Files inside packages/ import their runtime deps as bare specifiers.
      // Resolve them from apps/management/node_modules.
      axios: fileURLToPath(new URL('./node_modules/axios', import.meta.url)),
      clsx: fileURLToPath(new URL('./node_modules/clsx', import.meta.url)),
      'tailwind-merge': fileURLToPath(new URL('./node_modules/tailwind-merge', import.meta.url)),
      'lucide-vue-next': fileURLToPath(new URL('./node_modules/lucide-vue-next', import.meta.url)),
    },
    dedupe: ['axios', 'vue'],
  },
})
