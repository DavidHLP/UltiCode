import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

import App from './App.vue'
import router from './router'
import i18n, { setLocale, getActiveLocale } from './i18n'
import {
  initTheme,
  applyTypographyDensity,
  DEFAULT_APPEARANCE,
  useAppearance,
} from '@ulticode/theme'
import { bootstrapApp } from '@ulticode/app-bootstrap'
import { setOnAuthFailure } from '@ulticode/auth-core'

/**
 * Management application bootstrap.
 *
 * The startup ordering invariant (theme → Pinia/i18n → auth-failure handler →
 * auth init → router → mount) is owned by the shared `bootstrapApp` module so
 * Console and Management cannot diverge; this entry file is the Management
 * adapter uses shared appearance preferences and supplies Management policy:
 * the locale-detection pre-auth step, and the single auth-failure owner.
 */
bootstrapApp({
  density: DEFAULT_APPEARANCE.density,
  initTheme,
  applyTypographyDensity: () => applyTypographyDensity(useAppearance().settings.value.density),
  rootComponent: App,
  preAuthPlugins: [createPinia(), i18n],
  async preAuthInit() {
    // Initialize locale from stored preference on startup so
    // document.documentElement.lang is set before any component mounts and
    // persists correctly to localStorage.
    setLocale(getActiveLocale() as 'zh-CN' | 'en-US')
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
