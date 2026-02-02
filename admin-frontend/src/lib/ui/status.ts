import type { BadgeVariant } from '@/lib/entities/user'

// Re-export contest badge utilities from entity file for type safety
export {
  getContestStatusBadgeVariant,
  getContestStatusBadge,
  getContestTypeBadge,
  getContestTypeBadgeVariant,
} from '@/lib/entities/contest'

// Re-export problem badge utilities from entity file for type safety
export {
  getDifficultyBadgeVariant,
  getDifficultyColor,
} from '@/lib/entities/problem'

/**
 * Returns the badge variant for a flag status
 */
export function getFlagStatusBadgeVariant(status: string | null): BadgeVariant {
  switch (status) {
    case 'RESOLVED':
      return 'secondary'
    case 'PENDING':
      return 'destructive'
    default:
      return 'outline'
  }
}

/**
 * Returns the badge variant for a notification type
 */
export function getNotificationTypeBadgeVariant(type: string): BadgeVariant {
  switch (type) {
    case 'SYSTEM':
      return 'destructive'
    case 'CONTEST':
      return 'default'
    case 'PROBLEM':
      return 'secondary'
    default:
      return 'outline'
  }
}
