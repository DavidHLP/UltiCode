import { h, type VNode } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from './user'
import type { Difficulty } from '@/api/admin/problems'
import { IconFlask, IconBrackets, IconSparkles } from '@tabler/icons-vue'

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

/**
 * Returns the icon component for a problem difficulty
 */
export function getDifficultyIcon(difficulty: Difficulty): VNode {
  switch (difficulty) {
    case 'EASY':
      return h(IconFlask, { class: 'h-4 w-4' })
    case 'MEDIUM':
      return h(IconBrackets, { class: 'h-4 w-4' })
    case 'HARD':
      return h(IconSparkles, { class: 'h-4 w-4' })
    default:
      return h(IconFlask, { class: 'h-4 w-4' })
  }
}

/**
 * Returns the badge component for a problem difficulty
 * @param t - i18n translation function
 */
export function getDifficultyBadge(
  difficulty: Difficulty,
  t: (key: string, fallback?: string) => string,
): VNode {
  return h(Badge, { variant: getDifficultyBadgeVariant(difficulty) }, () =>
    t(`problems.difficulty.${difficulty.toLowerCase()}`, difficulty),
  )
}
