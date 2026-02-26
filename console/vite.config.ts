import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, type PluginOption } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'
import Icons from 'unplugin-icons/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    tailwindcss(),
    vueDevTools(),
    Icons({ compiler: 'vue3', autoInstall: true }) as PluginOption,
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 9002,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Split Monaco Editor into its own chunk (large dependency)
          'monaco-editor': ['monaco-editor'],
          // Vue ecosystem
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          // UI libraries
          'ui-vendor': ['reka-ui', 'lucide-vue-next', '@tabler/icons-vue'],
          // Markdown and code highlighting
          'markdown': ['markdown-it', 'highlight.js', 'katex'],
        },
      },
    },
    // Increase chunk size warning limit
    chunkSizeWarningLimit: 1000,
  },
})
