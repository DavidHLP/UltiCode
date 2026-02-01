import type { BadgeVariant } from '@/lib/entities/user'

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
 * Returns the badge variant for a contest status
 */
export function getContestStatusBadgeVariant(status: string): BadgeVariant {
  switch (status) {
    case 'RUNNING':
      return 'default'
    case 'FINISHED':
      return 'secondary'
    case 'UPCOMING':
      return 'outline'
    default:
      return 'outline'
  }
}

/**
 * Returns the badge variant for a contest type
 */
export function getContestTypeBadgeVariant(type: string): BadgeVariant {
  switch (type) {
    case 'PUBLIC':
      return 'default'
    case 'PRIVATE':
      return 'secondary'
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

/**
 * Returns the badge variant for problem difficulty
 */
export function getDifficultyBadgeVariant(difficulty: string): BadgeVariant {
  switch (difficulty) {
    case 'Easy':
      return 'default'
    case 'Medium':
      return 'secondary'
    case 'Hard':
      return 'destructive'
    default:
      return 'outline'
  }
}
