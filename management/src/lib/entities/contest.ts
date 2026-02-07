import { h, type VNode } from 'vue'
import { IconCalendar, IconPlayerPlay, IconPlayerStop } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from './user'

export type ContestStatus = 'UPCOMING' | 'RUNNING' | 'FINISHED'
export type ContestType = 'PUBLIC' | 'PRIVATE' | 'VIRTUAL'

/**
 * Returns the icon component for a contest status
 */
export function getContestStatusIcon(status: ContestStatus): VNode {
  switch (status) {
    case 'RUNNING':
      return h(IconPlayerPlay, { class: 'h-4 w-4 text-emerald-500' })
    case 'FINISHED':
      return h(IconPlayerStop, { class: 'h-4 w-4 text-muted-foreground' })
    default:
      return h(IconCalendar, { class: 'h-4 w-4 text-blue-500' })
  }
}

/**
 * Returns the badge variant for a contest type
 */
export function getContestTypeBadgeVariant(type: ContestType): BadgeVariant {
  switch (type) {
    case 'PUBLIC':
      return 'default'
    case 'PRIVATE':
      return 'secondary'
    case 'VIRTUAL':
      return 'outline'
    default:
      return 'outline'
  }
}

/**
 * Returns the badge variant for a contest status
 */
export function getContestStatusBadgeVariant(status: ContestStatus): BadgeVariant {
  switch (status) {
    case 'RUNNING':
      return 'default'
    case 'FINISHED':
      return 'secondary'
    default:
      return 'outline'
  }
}

/**
 * Returns the badge component for a contest status
 * @param t - i18n translation function
 */
export function getContestStatusBadge(status: ContestStatus, t: (key: string) => string): VNode {
  const variant = getContestStatusBadgeVariant(status)
  return h(Badge, { variant }, () => t(`contests.status.${status.toLowerCase()}`))
}

/**
 * Returns the badge component for a contest type
 * @param t - i18n translation function
 */
export function getContestTypeBadge(type: ContestType, t: (key: string) => string): VNode {
  const variant = getContestTypeBadgeVariant(type)
  return h(Badge, { variant }, () => t(`contests.type.${type}`))
}
