import { createI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import zhCN from './locales/zh-CN/'
import enUS from './locales/en-US/'
import { getStoredLocale, setStoredLocale, setStorageNotifier } from '@/shared/i18n-storage/src'

setStorageNotifier((level, message) => {
  if (level === 'warning') toast.warning(message)
  else if (level === 'info') toast.info(message)
  else if (level === 'success') toast.success(message)
})

// Re-export types and constants from types.ts (single source of truth)
export {
  SUPPORTED_LOCALES,
  LOCALE_CONFIGS,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
} from './types'

export type { SupportedLocale, LocaleConfig, MessageSchema } from './types'

export { getStoredLocale, setStoredLocale }

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

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

// Create i18n instance
export const i18n = createI18n({
  legacy: false, // Use Composition API
  globalInjection: true, // Inject $t globally
  locale: getInitialLocale(),
  fallbackLocale: 'zh-CN',
  silentTranslationWarn: true, // Suppress warnings in production
  missingWarn: import.meta.env.DEV, // true in development, false in production
  fallbackWarn: false, // Suppress fallback warnings
  messages,
})

// Get active locale from i18n instance
export function getActiveLocale(): string {
  const locale = i18n.global.locale.value
  if (['zh-CN', 'en-US'].includes(locale)) {
    return locale
  }

  return 'zh-CN'
}

export function setLocale(locale: 'zh-CN' | 'en-US'): void {
  i18n.global.locale.value = locale
  setStoredLocale(locale)
  document.documentElement.lang = locale
}

// Type-safe translation helper
export function t(key: string, params?: Record<string, unknown>): string {
  const result = i18n.global.t(key, params ?? {})
  return result
}

export default i18n
