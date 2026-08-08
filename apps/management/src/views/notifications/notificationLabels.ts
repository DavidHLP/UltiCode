import type { NotificationCategory, NotificationType } from '@/api/admin/notifications'

type Translate = (key: string) => string

export function getNotificationTypeLabel(type: NotificationType, translate: Translate): string {
  return translate(`notifications.types.${type}`)
}

export function getNotificationCategoryLabel(
  category: NotificationCategory,
  translate: Translate,
): string {
  return translate(`notifications.categories.${category}`)
}
