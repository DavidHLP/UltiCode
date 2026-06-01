import { getActiveLocale } from '@/i18n'

/**
 * Format a date to locale date string
 * @param date - Date string, Date object, or null/undefined
 * @param locale - Locale string (default: current active locale)
 * @param options - Intl.DateTimeFormatOptions
 * @returns Formatted date string or placeholder
 */
export function formatDate(
  date: string | Date | null | undefined,
  locale?: string,
  options: Intl.DateTimeFormatOptions = {},
): string {
  if (!date) return '-'

  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'

  return dateObj.toLocaleDateString(locale ?? getActiveLocale(), {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    ...options,
  })
}

/**
 * Format a date to locale date and time string
 * @param date - Date string, Date object, or null/undefined
 * @param locale - Locale string (default: current active locale)
 * @param options - Intl.DateTimeFormatOptions
 * @returns Formatted date and time string or placeholder
 */
export function formatDateTime(
  date: string | Date | null | undefined,
  locale?: string,
  options: Intl.DateTimeFormatOptions = {},
): string {
  if (!date) return '-'

  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'

  return dateObj.toLocaleString(locale ?? getActiveLocale(), {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    ...options,
  })
}

/**
 * Format a date to relative time string (e.g., "2 hours ago", "in 3 days")
 * @param date - Date string, Date object, or null/undefined
 * @returns Relative time string or placeholder
 */
export function formatRelativeTime(date: string | Date | null | undefined): string {
  if (!date) return '-'

  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'

  const now = new Date()
  const diffMs = now.getTime() - dateObj.getTime()
  const diffSecs = Math.floor(diffMs / 1000)
  const diffMins = Math.floor(diffSecs / 60)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  const rtf = new Intl.RelativeTimeFormat(getActiveLocale(), { numeric: 'auto' })

  if (diffSecs < 60) {
    return rtf.format(-diffSecs, 'second')
  }
  if (diffMins < 60) {
    return rtf.format(-diffMins, 'minute')
  }
  if (diffHours < 24) {
    return rtf.format(-diffHours, 'hour')
  }
  if (diffDays < 30) {
    return rtf.format(-diffDays, 'day')
  }
  if (diffDays < 365) {
    const months = Math.floor(diffDays / 30)
    return rtf.format(-months, 'month')
  }
  const years = Math.floor(diffDays / 365)
  return rtf.format(-years, 'year')
}

/**
 * Format a date to short time string (e.g., "2:30 PM")
 * @param date - Date string, Date object, or null/undefined
 * @param locale - Locale string (default: current active locale)
 * @returns Formatted time string or placeholder
 */
export function formatTime(date: string | Date | null | undefined, locale?: string): string {
  if (!date) return '-'

  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'

  return dateObj.toLocaleTimeString(locale ?? getActiveLocale(), {
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Format a date to short date string without year (e.g., "Jan 15")
 * @param date - Date string, Date object, or null/undefined
 * @param locale - Locale string (default: current active locale)
 * @returns Formatted short date string or placeholder
 */
export function formatShortDate(date: string | Date | null | undefined, locale?: string): string {
  if (!date) return '-'

  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'

  return dateObj.toLocaleDateString(locale ?? getActiveLocale(), {
    month: 'short',
    day: 'numeric',
  })
}
