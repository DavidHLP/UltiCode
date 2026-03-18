import { createI18n } from 'vue-i18n'
import { isRef } from 'vue'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

// Re-export types and constants from types.ts (single source of truth)
export {
  SUPPORTED_LOCALES,
  LOCALE_CONFIGS,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
  LOCALE_HEADER_KEY,
} from './types'

export type { SupportedLocale, LocaleConfig, MessageSchema } from './types'

// Get active locale from localStorage or return default
function getStoredLocale(): string | null {
  try {
    return localStorage.getItem('ulticode-locale')
  } catch {
    return null
  }
}

// Get initial locale based on storage or browser preference
function getInitialLocale(): string {
  const stored = getStoredLocale()
  if (stored && ['zh-CN', 'en-US'].includes(stored)) {
    return stored
  }

  // Try to detect browser language
  try {
    const browserLang = navigator.language
    if (browserLang.startsWith('zh')) {
      return 'zh-CN'
    }
    if (browserLang.startsWith('en')) {
      return 'en-US'
    }
  } catch {
    // Ignore if navigator is not available
  }

  return 'zh-CN' // Default locale
}

// Create i18n instance
export const i18n = createI18n({
  legacy: false, // Use Composition API
  globalInjection: true, // Inject $t globally
  locale: getInitialLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

// Get active locale from i18n instance
export function getActiveLocale(): string {
  const localeRef = i18n.global.locale
  const localeValue = isRef(localeRef) ? localeRef.value : localeRef

  if (['zh-CN', 'en-US'].includes(localeValue)) {
    return localeValue
  }

  return 'zh-CN'
}

// Set locale and persist to localStorage
export function setLocale(locale: 'zh-CN' | 'en-US'): void {
  i18n.global.locale.value = locale

  try {
    localStorage.setItem('ulticode-locale', locale)
  } catch {
    // Ignore localStorage errors
  }

  // Update HTML lang attribute
  document.documentElement.lang = locale
}

// Type-safe translation helper
export function t(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params ?? {})
}

export default i18n
