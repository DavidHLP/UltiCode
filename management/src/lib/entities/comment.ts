import { h, type VNode } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from './user'
import { IconFlag, IconTrash, IconMessage, IconFileText } from '@tabler/icons-vue'
import { type CommentType } from '@/api/admin/comments'

/**
 * Returns the badge variant for a comment type
 */
export function getCommentTypeBadgeVariant(type: CommentType): BadgeVariant {
  // All comment types use the same outline variant
  void type
  return 'outline'
}

/**
 * Returns the badge component for a comment type
 * @param t - i18n translation function
 */
export function getCommentTypeBadge(
  type: CommentType,
  t: (key: string, fallback?: string) => string,
): VNode {
  return h(Badge, { variant: getCommentTypeBadgeVariant(type) }, () =>
    t(`comments.type.${type}`, type),
  )
}

/**
 * Returns the icon for a comment type
 */
export function getCommentTypeIcon(type: CommentType): VNode {
  return type === 'forum'
    ? h(IconMessage, { class: 'h-3 w-3' })
    : h(IconFileText, { class: 'h-3 w-3' })
}

/**
 * Returns the badge variant for a comment status
 */
export function getCommentStatusBadgeVariant(isFlagged: boolean, isDeleted: boolean): BadgeVariant {
  if (isDeleted || isFlagged) return 'destructive'
  return 'default'
}

/**
 * Returns the badge component for a comment status
 * @param t - i18n translation function
 */
export function getCommentStatusBadge(
  isFlagged: boolean,
  isDeleted: boolean,
  t: (key: string) => string,
): VNode {
  if (isDeleted) {
    return h(Badge, { variant: 'destructive' }, () => [
      h(IconTrash, { class: 'mr-1 h-3 w-3' }),
      t('comments.status.deleted'),
    ])
  }

  if (isFlagged) {
    return h(Badge, { variant: 'destructive' }, () => [
      h(IconFlag, { class: 'mr-1 h-3 w-3' }),
      t('comments.status.flagged'),
    ])
  }

  return h(Badge, { variant: 'default' }, () => t('comments.status.active'))
}
