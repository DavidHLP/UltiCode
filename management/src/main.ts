import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { setLocale } from './i18n'
import { initTheme } from '@/composables/useTheme'

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

  const app = createApp(App)
  const pinia = createPinia()

  // Install Pinia first (required for stores to work)
  app.use(pinia)
  app.use(i18n)

  // Initialize locale from stored preference on app startup
  // This ensures document.documentElement.lang is set even before any component mounts
  // and persists correctly to localStorage
  const storedLocale = localStorage.getItem('ulticode-locale') as 'zh-CN' | 'en-US' | null
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

  // Register auth-failure handler for shared/auth-core's 401-refresh path.
  // Currently `csrfInterceptors` in request.ts is created WITHOUT a
  // refreshAccessToken callback, so the shared interceptor's 401 branch
  // never triggers `triggerAuthFailure` — this handler is a no-op today.
  // It is registered anyway so that when management's request.ts is
  // updated to pass `createRefreshAccessToken(...)` (follow-up), the
  // existing 401 → clearUser + push('/login') behavior moves into here
  // with zero new code.
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
