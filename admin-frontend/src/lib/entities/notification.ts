import { h, type VNode } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from './user'
import { IconMail, IconBell, IconAlertCircle, IconInfoCircle } from '@tabler/icons-vue'

/**
 * Notification type
 */
export type NotificationType = 'SYSTEM' | 'CONTEST' | 'PROBLEM' | 'FORUM' | 'ACCOUNT'

/**
 * Notification priority
 */
export type NotificationPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

/**
 * Returns the badge variant for a notification type
 */
export function getNotificationTypeBadgeVariant(type: NotificationType): BadgeVariant {
  switch (type) {
    case 'SYSTEM':
      return 'destructive'
    case 'CONTEST':
      return 'default'
    case 'PROBLEM':
      return 'secondary'
    case 'FORUM':
      return 'outline'
    case 'ACCOUNT':
      return 'outline'
    default:
      return 'outline'
  }
}

/**
 * Returns the icon component for a notification type
 */
export function getNotificationTypeIcon(type: NotificationType): VNode {
  switch (type) {
    case 'SYSTEM':
      return h(IconAlertCircle, { class: 'h-4 w-4 text-destructive' })
    case 'CONTEST':
      return h(IconBell, { class: 'h-4 w-4 text-primary' })
    case 'PROBLEM':
      return h(IconInfoCircle, { class: 'h-4 w-4 text-secondary-foreground' })
    case 'FORUM':
      return h(IconMail, { class: 'h-4 w-4 text-muted-foreground' })
    case 'ACCOUNT':
      return h(IconInfoCircle, { class: 'h-4 w-4 text-muted-foreground' })
    default:
      return h(IconInfoCircle, { class: 'h-4 w-4 text-muted-foreground' })
  }
}

/**
 * Returns the badge component for a notification type
 * @param t - i18n translation function
 */
export function getNotificationTypeBadge(
  type: NotificationType,
  t: (key: string) => string,
): VNode {
  const variant = getNotificationTypeBadgeVariant(type)
  return h(Badge, { variant }, () => t(`notifications.type.${type.toLowerCase()}`))
}

/**
 * Returns the badge variant for a notification priority
 */
export function getNotificationPriorityBadgeVariant(priority: NotificationPriority): BadgeVariant {
  switch (priority) {
    case 'URGENT':
      return 'destructive'
    case 'HIGH':
      return 'default'
    case 'MEDIUM':
      return 'secondary'
    case 'LOW':
      return 'outline'
    default:
      return 'outline'
  }
}

/**
 * Returns the color class for a notification priority
 */
export function getNotificationPriorityColor(priority: NotificationPriority): string {
  switch (priority) {
    case 'URGENT':
      return 'text-red-500'
    case 'HIGH':
      return 'text-amber-500'
    case 'MEDIUM':
      return 'text-blue-500'
    case 'LOW':
      return 'text-muted-foreground'
    default:
      return 'text-muted-foreground'
  }
}

/**
 * Returns the badge component for a notification priority
 * @param t - i18n translation function
 */
export function getNotificationPriorityBadge(
  priority: NotificationPriority,
  t: (key: string) => string,
): VNode {
  const variant = getNotificationPriorityBadgeVariant(priority)
  return h(Badge, { variant }, () => t(`notifications.priority.${priority.toLowerCase()}`))
}
