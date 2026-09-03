import { fileURLToPath } from 'node:url'
import { configDefaults, defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    // jsdom: shared components are Vue SFCs that need DOM to mount.
    environment: 'jsdom',
    exclude: [...configDefaults.exclude, 'e2e/**'],
    root: fileURLToPath(new URL('./src', import.meta.url)),
    globals: true,
    // Register a global RouterLink stub before any spec runs, so
    // <component :is="'router-link'"> renders in jsdom without warnings and
    // the `as='link'` / `:to` / SidebarParentItem Mode-A production paths
    // become testable (see src/__tests__/setup.ts).
    setupFiles: [fileURLToPath(new URL('./src/__tests__/setup.ts', import.meta.url))],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // reka-ui is a peerDependency; resolve it from the console workspace
      // (mirrors the tsconfig.json `paths` convention).
      'reka-ui': fileURLToPath(new URL('../../apps/console/node_modules/reka-ui', import.meta.url)),
    },
  },
})
