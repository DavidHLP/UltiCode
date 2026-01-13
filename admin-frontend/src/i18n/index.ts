import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

// Support same locales as frontend - zh-CN first to match
export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

// Locale configuration
export interface LocaleConfig {
  code: SupportedLocale
  name: string
  nativeName: string
  flag: string
}

export const LOCALE_CONFIGS: Record<SupportedLocale, LocaleConfig> = {
  'zh-CN': {
    code: 'zh-CN',
    name: 'Chinese (Simplified)',
    nativeName: '简体中文',
    flag: '🇨🇳',
  },
  'en-US': {
    code: 'en-US',
    name: 'English (US)',
    nativeName: 'English',
    flag: '🇺🇸',
  },
}

// Default and fallback locale - zh-CN per user decision
export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'
export const FALLBACK_LOCALE: SupportedLocale = 'zh-CN'
export const LOCALE_HEADER_KEY = 'x-locale'

/**
 * Get active locale from localStorage or return default
 */
function getStoredLocale(): string | null {
  try {
    return localStorage.getItem('ulticode-locale')
  } catch {
    return null
  }
}

/**
 * Get initial locale
 */
function getInitialLocale(): SupportedLocale {
  const stored = getStoredLocale()
  if (stored && SUPPORTED_LOCALES.includes(stored as SupportedLocale)) {
    return stored as SupportedLocale
  }
  return DEFAULT_LOCALE
}

// Create i18n instance
export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: getInitialLocale(),
  fallbackLocale: FALLBACK_LOCALE,
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export default i18n
