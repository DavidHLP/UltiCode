import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

import App from './App.vue'
import router from './router'
import i18n, { setLocale, getStoredLocale } from './i18n'
import { initTheme, applyTypographyDensity } from '@/shared/theme/src'
import { bootstrapApp } from '@/shared/app-bootstrap/src'
import { setOnAuthFailure } from '@/shared/auth-core/src'

/**
 * Management application bootstrap.
 *
 * The startup ordering invariant (theme → Pinia/i18n → auth-failure handler →
 * auth init → router → mount) is owned by the shared `bootstrapApp` module so
 * Console and Management cannot diverge; this entry file is the Management
 * adapter and supplies only Management-specific policy: the compact density,
 * the locale-detection pre-auth step, and the single auth-failure owner.
 */
bootstrapApp({
  density: 'compact',
  initTheme,
  applyTypographyDensity,
  rootComponent: App,
  plugins: [createPinia(), i18n],
  async preAuthInit() {
    // Initialize locale from stored preference on startup so
    // document.documentElement.lang is set before any component mounts and
    // persists correctly to localStorage.
    const storedLocale = getStoredLocale() as 'zh-CN' | 'en-US' | null
    if (storedLocale === 'zh-CN' || storedLocale === 'en-US') {
      setLocale(storedLocale)
    } else {
      const browserLang = navigator.language
      if (browserLang.startsWith('zh')) {
        setLocale('zh-CN')
      } else {
        setLocale('en-US')
      }
    }
  },
  registerAuthFailureHandler: setOnAuthFailure,
  async onAuthFailure() {
    // Refresh-failed (>7d idle) fans in here; both this handler and the
    // propagated-401 strategy in utils/request.ts funnel through
    // runSessionExpired, the single owner for the session-expired sequence.
    const { runSessionExpired } = await import('@/auth/runSessionExpired')
    runSessionExpired()
  },
  async initializeAuth() {
    const { useAuthStore } = await import('@/stores/auth')
    await useAuthStore().initialize()
  },
  router,
}).catch((error) => {
  console.error('[Bootstrap] Fatal error during bootstrap:', error)
  // Fallback: mount app anyway to show error UI
  const app = createApp(App)
  app.use(createPinia())
  app.use(i18n)
  app.use(router)
  app.mount('#app')
})
