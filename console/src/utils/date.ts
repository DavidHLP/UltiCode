/**
 * Re-export from shared/datetime-utils (arch review candidate #3).
 * Console gains locale-aware Intl formatters; penalty/duration helpers preserved.
 */
export {
  formatDateTime,
  formatRelativeTime,
  formatPenaltyTime,
  getDurationMinutes,
  formatDate,
  formatTime,
  formatShortDate,
} from '@/shared/datetime-utils/src'
