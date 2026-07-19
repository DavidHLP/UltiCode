import { watch } from 'vue'
import { i18n, getActiveLocale, setLocale } from './index'
import { SUPPORTED_LOCALES, LOCALE_CONFIGS, type SupportedLocale } from './types'
import {
  formatDate,
  formatDateTime,
  formatRelativeTime as _formatRelativeTime,
} from '@/lib/format/date'

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
 * Format a date according to current locale.
 * Delegates to the locale-injected wrapper in lib/format/date.
 */
export function formatDateByLocale(
  date: Date | string | null | undefined,
  options?: Intl.DateTimeFormatOptions,
): string {
  return formatDate(date, undefined, options)
}

/**
 * Format a number according to current locale
 */
export function formatNumberByLocale(number: number, options?: Intl.NumberFormatOptions): string {
  const locale = getActiveLocale()

  return number.toLocaleString(locale, options)
}

/**
 * Format a date and time according to current locale.
 * Delegates to the locale-injected wrapper in lib/format/date.
 */
export function formatDateTimeByLocale(
  date: Date | string | null | undefined,
  options?: Intl.DateTimeFormatOptions,
): string {
  return formatDateTime(date, undefined, options)
}

/**
 * Format a number in compact notation
 * English: 1.2K, 3.4M
 * Chinese: 1.2万, 3.4万 (uses 万 at ≥10000, no 千)
 */
export function formatCompactNumber(num: number): string {
  const locale = getActiveLocale()
  const isZh = locale.startsWith('zh')

  if (isZh) {
    if (num >= 10_000) {
      return (num / 10_000).toLocaleString(locale, { maximumFractionDigits: 1 }) + '万'
    }
    return num.toLocaleString(locale)
  }

  if (num >= 1_000_000) {
    return (num / 1_000_000).toLocaleString(locale, { maximumFractionDigits: 1 }) + 'M'
  }
  if (num >= 1_000) {
    return (num / 1_000).toLocaleString(locale, { maximumFractionDigits: 1 }) + 'K'
  }
  return num.toLocaleString(locale)
}

/**
 * Format a relative time (e.g., "2 hours ago").
 * Delegates to the locale-injected wrapper in lib/format/date.
 */
export function formatRelativeTime(date: Date | string | null | undefined): string {
  return _formatRelativeTime(date)
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
