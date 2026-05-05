import { watch } from 'vue'
import { i18n, getActiveLocale, setLocale } from './index'
import { SUPPORTED_LOCALES, LOCALE_CONFIGS, type SupportedLocale } from './types'

/**
 * Get all supported locales with their configurations
 */
export function getSupportedLocales() {
  return SUPPORTED_LOCALES.map((code: SupportedLocale) => ({
    ...LOCALE_CONFIGS[code],
  }))
}

/**
 * Get current locale configuration
 */
export function getCurrentLocaleConfig() {
  const locale = getActiveLocale() as SupportedLocale
  return LOCALE_CONFIGS[locale]
}

/**
 * Check if a locale is supported
 */
export function isLocaleSupported(locale: string): locale is SupportedLocale {
  return SUPPORTED_LOCALES.includes(locale as SupportedLocale)
}

/**
 * Switch to a different locale
 */
export function switchLocale(locale: SupportedLocale): void {
  setLocale(locale)
}

/**
 * Watch locale changes and execute callback
 */
export function watchLocale(callback: (locale: SupportedLocale) => void) {
  return watch(
    () => getActiveLocale(),
    (locale) => {
      if (isLocaleSupported(locale)) {
        callback(locale)
      }
    },
    { immediate: true },
  )
}

/**
 * Format a date according to current locale
 */
export function formatDateByLocale(
  date: Date | string,
  options?: Intl.DateTimeFormatOptions,
): string {
  const locale = getActiveLocale()
  const dateObj = typeof date === 'string' ? new Date(date) : date

  const defaultOptions: Intl.DateTimeFormatOptions = {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    ...options,
  }

  return dateObj.toLocaleDateString(locale, defaultOptions)
}

/**
 * Format a number according to current locale
 */
export function formatNumberByLocale(number: number, options?: Intl.NumberFormatOptions): string {
  const locale = getActiveLocale()

  return number.toLocaleString(locale, options)
}

/**
 * Format a relative time (e.g., "2 hours ago")
 */
export function formatRelativeTime(date: Date | string): string {
  const locale = getActiveLocale()
  const dateObj = typeof date === 'string' ? new Date(date) : date
  const now = new Date()
  const diffInSeconds = Math.floor((now.getTime() - dateObj.getTime()) / 1000)

  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

  if (diffInSeconds < 60) {
    return rtf.format(-diffInSeconds, 'second')
  }

  const diffInMinutes = Math.floor(diffInSeconds / 60)
  if (diffInMinutes < 60) {
    return rtf.format(-diffInMinutes, 'minute')
  }

  const diffInHours = Math.floor(diffInMinutes / 60)
  if (diffInHours < 24) {
    return rtf.format(-diffInHours, 'hour')
  }

  const diffInDays = Math.floor(diffInHours / 24)
  if (diffInDays < 30) {
    return rtf.format(-diffInDays, 'day')
  }

  const diffInMonths = Math.floor(diffInDays / 30)
  if (diffInMonths < 12) {
    return rtf.format(-diffInMonths, 'month')
  }

  const diffInYears = Math.floor(diffInMonths / 12)
  return rtf.format(-diffInYears, 'year')
}

/**
 * Get plural form of a word based on count
 */
export function getPlural(key: string, count: number, params?: Record<string, unknown>): string {
  return i18n.global.t(key, { ...params, count })
}

/**
 * Check if a translation key exists
 */
export function hasTranslation(key: string): boolean {
  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const result = (i18n.global as any).te(key)
    return result as boolean
  } catch {
    return false
  }
}

/**
 * Get translation with fallback
 */
export function tWithFallback(
  key: string,
  fallback: string,
  params?: Record<string, unknown>,
): string {
  if (hasTranslation(key)) {
    return i18n.global.t(key, params ?? {})
  }
  return fallback
}
