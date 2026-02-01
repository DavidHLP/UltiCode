import type { BadgeVariant } from './user'
import type { Difficulty } from '@/api/admin/problems'

/**
 * Returns the badge variant for a problem difficulty
 */
export function getDifficultyBadgeVariant(difficulty: Difficulty): BadgeVariant {
  switch (difficulty) {
    case 'EASY':
      return 'default'
    case 'MEDIUM':
      return 'secondary'
    case 'HARD':
      return 'destructive'
    default:
      return 'outline'
  }
}

/**
 * Returns the color class for a problem difficulty
 */
export function getDifficultyColor(difficulty: Difficulty): string {
  switch (difficulty) {
    case 'EASY':
      return 'text-emerald-500'
    case 'MEDIUM':
      return 'text-amber-500'
    case 'HARD':
      return 'text-red-500'
    default:
      return 'text-muted-foreground'
  }
}

/**
 * Returns the background color class for a problem difficulty
 */
export function getDifficultyBgColor(difficulty: Difficulty): string {
  switch (difficulty) {
    case 'EASY':
      return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
    case 'MEDIUM':
      return 'bg-amber-500/10 text-amber-500 border-amber-500/20'
    case 'HARD':
      return 'bg-red-500/10 text-red-500 border-red-500/20'
    default:
      return 'bg-muted text-muted-foreground'
  }
}
