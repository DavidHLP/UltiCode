import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { setLocale } from './i18n'

/**
 * Application Bootstrap
 *
 * Critical: Auth initialization happens BEFORE router installation.
 * This eliminates race conditions where the router guard would
 * need to wait for async auth initialization.
 *
 * Flow:
 * 1. Create Vue app
 * 2. Install Pinia (required for stores)
 * 3. Initialize auth store (async, completes before router)
 * 4. Install router (now auth status is known)
 * 5. Mount app
 */
async function bootstrap() {
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
