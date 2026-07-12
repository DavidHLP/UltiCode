/**
 * Thin locale-injection wrapper over shared/datetime-utils (arch review #3).
 * Management's active locale from i18n is injected as the default.
 */
import {
  formatDate as _formatDate,
  formatDateTime as _formatDateTime,
  formatRelativeTime as _formatRelativeTime,
  formatTime as _formatTime,
  formatShortDate as _formatShortDate,
  formatPenaltyTime,
  getDurationMinutes,
} from '@/shared/datetime-utils/src'
import { getActiveLocale } from '@/i18n'

export function formatDate(
  date: string | Date | null | undefined,
  locale?: string,
  options?: Intl.DateTimeFormatOptions,
): string {
  return _formatDate(date, locale ?? getActiveLocale(), options)
}

export function formatDateTime(
  date: string | Date | null | undefined,
  locale?: string,
  options?: Intl.DateTimeFormatOptions,
): string {
  return _formatDateTime(date, locale ?? getActiveLocale(), options)
}

export function formatRelativeTime(
  date: string | Date | null | undefined,
  locale?: string,
): string {
  return _formatRelativeTime(date, locale ?? getActiveLocale())
}

export function formatTime(date: string | Date | null | undefined, locale?: string): string {
  return _formatTime(date, locale ?? getActiveLocale())
}

export function formatShortDate(date: string | Date | null | undefined, locale?: string): string {
  return _formatShortDate(date, locale ?? getActiveLocale())
}

export { formatPenaltyTime, getDurationMinutes }
