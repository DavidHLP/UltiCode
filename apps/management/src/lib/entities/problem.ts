import { h, type VNode, type Component } from 'vue'
import { badge, DIFFICULTY_COLOR_MAP } from '@/components/ui/terminal'
import type { Difficulty } from '@/api/admin/problems'
import { IconFlask, IconBrackets, IconSparkles } from '@tabler/icons-vue'

/**
 * Returns the color class for a problem difficulty
 */
export function getDifficultyColor(difficulty: Difficulty): string {
  switch (difficulty) {
    case 'EASY':
      return 'text-foreground-strong'
    case 'MEDIUM':
      return 'text-foreground-strong'
    case 'HARD':
      return 'text-foreground-strong'
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
      return 'bg-surface-highlight text-foreground-strong border border-control'
    case 'MEDIUM':
      return 'bg-surface-highlight text-foreground-strong border border-control'
    case 'HARD':
      return 'bg-surface-highlight text-foreground-strong border border-control'
    default:
      return 'bg-muted text-muted-foreground'
  }
}

/**
 * Returns the icon component for a problem difficulty (raw Component for badge())
 */
export function getDifficultyIconComponent(difficulty: Difficulty): Component {
  switch (difficulty) {
    case 'EASY':
      return IconFlask
    case 'MEDIUM':
      return IconBrackets
    case 'HARD':
      return IconSparkles
    default:
      return IconFlask
  }
}

/**
 * Returns the icon VNode for a problem difficulty (pre-rendered, for non-badge use)
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
  return badge({
    color: DIFFICULTY_COLOR_MAP[difficulty?.toUpperCase()] ?? 'neutral',
    label: t(`problems.difficulty.${difficulty?.toLowerCase()}`, difficulty),
    icon: getDifficultyIconComponent(difficulty),
    dot: true,
  })
}
