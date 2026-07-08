import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { setLocale, getStoredLocale } from './i18n'
import { initTheme } from '@/shared/theme/src'
import { applyTypographyDensity } from '@/shared/theme/src'

/**
 * Application Bootstrap
 *
 * Critical: Auth initialization happens BEFORE router installation.
 * This eliminates race conditions where the router guard would
 * need to wait for async auth initialization.
 *
 * Flow:
 * 1. Hydrate theme (singleton + media-query listener)
 * 2. Create Vue app
 * 3. Install Pinia (required for stores)
 * 4. Initialize auth store (async, completes before router)
 * 5. Install router (now auth status is known)
 * 6. Mount app
 */
async function bootstrap() {
  // Hydrate the shared color theme. The matching FOUC script lives at
  // public/theme-bootstrap.js and runs before this bootstrap, so this
  // call is a no-op for the initial paint but registers the OS-preference
  // listener for live updates.
  initTheme()
  // Management is the compact density profile: tables, moderation queues,
  // audit logs, and dashboard metrics all benefit from tighter rows and
  // smaller control text. See shared/theme/src/typography.css +
  // docs/SHARED_TYPOGRAPHY_DESIGN.md §7. This helper is the only place
  // that writes the `data-uc-density` attribute.
  applyTypographyDensity('compact')

  const app = createApp(App)
  const pinia = createPinia()

  // Install Pinia first (required for stores to work)
  app.use(pinia)
  app.use(i18n)

  // Initialize locale from stored preference on app startup
  // This ensures document.documentElement.lang is set even before any component mounts
  // and persists correctly to localStorage
  const storedLocale = getStoredLocale() as 'zh-CN' | 'en-US' | null
  if (storedLocale === 'zh-CN' || storedLocale === 'en-US') {
    setLocale(storedLocale)
  } else {
    // Detect browser preference
    const browserLang = navigator.language
    if (browserLang.startsWith('zh')) {
      setLocale('zh-CN')
    } else {
      setLocale('en-US')
    }
  }

  // Initialize auth BEFORE router installation
  // This ensures auth status is known when router guards run
  const { useAuthStore } = await import('@/stores/auth')
  const authStore = useAuthStore()

  try {
    await authStore.initialize()
  } catch (error) {
    console.error('[Bootstrap] Auth initialization failed:', error)
    // Continue anyway - app will render with unauthenticated state
  }

  // Wire up: 401 → refresh → replay is now ACTIVE (request.ts passes
  // createRefreshAccessToken(csrfManager) as the third arg to
  // createCsrfAxiosInterceptor). The shared interceptor's 401 branch
  // triggers triggerAuthFailure when refresh fails (>7d idle) — this
  // handler bridges to the same clearUser + push('/login') behavior
  // as the legacy 401 path. Note: management/request.ts also has its
  // own 401 → clearUser + push branch (line ~294) that fires in
  // parallel; both are idempotent (clearUser is a no-op when state
  // is already cleared, router.push to the same path is a no-op).
  const { setOnAuthFailure } = await import('@/shared/auth-core/src')
  setOnAuthFailure(async () => {
    const store = useAuthStore()
    if (store.isAuthenticated) {
      store.clearUser()
    }
    if (router.currentRoute.value.name !== 'login') {
      router.push('/login')
    }
  })

  // Now install router (auth status is already determined)
  app.use(router)

  app.mount('#app')
}

// Start the application
bootstrap().catch((error) => {
  console.error('[Bootstrap] Fatal error during bootstrap:', error)
  // Fallback: mount app anyway to show error UI
  const app = createApp(App)
  app.use(createPinia())
  app.use(i18n)
  app.use(router)
  app.mount('#app')
})
