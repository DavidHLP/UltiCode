/**
 * Shared datetime formatting utilities.
 *
 * Merges management's Intl-based locale-aware formatters with console's
 * contest-specific helpers (formatPenaltyTime, getDurationMinutes).
 * Architecture review candidate #3.
 */

/**
 * Format a date to locale date string.
 * @param date - Date string, Date object, or null/undefined
 * @param locale - Locale string (default: browser locale)
 * @param options - Intl.DateTimeFormatOptions
 */
export function formatDate(
  date: string | Date | null | undefined,
  locale?: string,
  options: Intl.DateTimeFormatOptions = {},
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'
  return dateObj.toLocaleDateString(locale ?? undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    ...options,
  })
}

/**
 * Format a date to locale date and time string.
 */
export function formatDateTime(
  date: string | Date | null | undefined,
  locale?: string,
  options: Intl.DateTimeFormatOptions = {},
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'
  return dateObj.toLocaleString(locale ?? undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    ...options,
  })
}

/**
 * Format a date to relative time string (e.g., "2 hours ago", "in 3 days").
 * Uses Intl.RelativeTimeFormat for locale-aware output.
 */
export function formatRelativeTime(
  date: string | Date | null | undefined,
  locale?: string,
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'

  const now = new Date()
  const diffMs = now.getTime() - dateObj.getTime()
  const diffSecs = Math.floor(diffMs / 1000)
  const diffMins = Math.floor(diffSecs / 60)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

  if (diffSecs < 60) return rtf.format(-diffSecs, 'second')
  if (diffMins < 60) return rtf.format(-diffMins, 'minute')
  if (diffHours < 24) return rtf.format(-diffHours, 'hour')
  if (diffDays < 30) return rtf.format(-diffDays, 'day')
  if (diffDays < 365) {
    const months = Math.floor(diffDays / 30)
    return rtf.format(-months, 'month')
  }
  const years = Math.floor(diffDays / 365)
  return rtf.format(-years, 'year')
}

/**
 * Format a date to short time string (e.g., "2:30 PM").
 */
export function formatTime(
  date: string | Date | null | undefined,
  locale?: string,
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'
  return dateObj.toLocaleTimeString(locale ?? undefined, {
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Format a date to short date string without year (e.g., "Jan 15").
 */
export function formatShortDate(
  date: string | Date | null | undefined,
  locale?: string,
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'
  return dateObj.toLocaleDateString(locale ?? undefined, {
    month: 'short',
    day: 'numeric',
  })
}

/**
 * Format a date to a 24-hour clock time string (e.g., "14:30").
 * Locale controls digit rendering; the 24-hour cycle is fixed.
 */
export function formatTime24(
  date: string | Date | null | undefined,
  locale?: string,
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'
  return dateObj.toLocaleTimeString(locale ?? undefined, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/**
 * Format a date to a weekday + short date string (e.g., "Mon, Jan 15").
 */
export function formatWeekdayShortDate(
  date: string | Date | null | undefined,
  locale?: string,
): string {
  if (!date) return '-'
  const dateObj = typeof date === 'string' ? new Date(date) : date
  if (isNaN(dateObj.getTime())) return '-'
  return dateObj.toLocaleDateString(locale ?? undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })
}

/**
 * Format contest penalty time from total seconds.
 * Output: "H:MM:SS" for hours, "M:SS" for minutes only.
 *
 * Console-origin; kept here so both apps share the same contest formatting.
 */
export function formatPenaltyTime(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${secs
      .toString()
      .padStart(2, '0')}`
  }
  return `${minutes}:${secs.toString().padStart(2, '0')}`
}

/**
 * Calculate duration in minutes between two timestamps.
 * Returns 0 if either is missing or invalid.
 *
 * Console-origin; used by contest views.
 */
export function getDurationMinutes(
  startTime?: string,
  endTime?: string | null,
): number {
  if (!startTime || !endTime) return 0
  const start = new Date(startTime).getTime()
  const end = new Date(endTime).getTime()
  if (isNaN(start) || isNaN(end)) return 0
  return Math.floor((end - start) / (1000 * 60))
}
