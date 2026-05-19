import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    root: fileURLToPath(new URL('./src', import.meta.url)),
    exclude: ['**/e2e/**', '**/node_modules/**', '**/shared/**'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
    dedupe: ['axios', 'vue'],
  },
})
