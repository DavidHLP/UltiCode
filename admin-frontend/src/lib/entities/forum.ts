import { h, type VNode } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from './user'
import { IconFlag, IconTrash, IconPinFilled, IconLock, IconMessage } from '@tabler/icons-vue'

/**
 * Forum post status
 */
export type ForumPostStatus = 'ACTIVE' | 'DELETED' | 'LOCKED' | 'PINNED'

/**
 * Returns the badge variant for a forum post status
 */
export function getForumPostStatusBadgeVariant(
  isDeleted: boolean,
  isLocked: boolean,
  isPinned: boolean,
): BadgeVariant {
  if (isDeleted) return 'destructive'
  if (isLocked) return 'secondary'
  if (isPinned) return 'default'
  return 'outline'
}

/**
 * Returns the badge component for a forum post status
 * @param t - i18n translation function
 */
export function getForumPostStatusBadge(
  isDeleted: boolean,
  isLocked: boolean,
  isPinned: boolean,
  t: (key: string) => string,
): VNode {
  if (isDeleted) {
    return h(Badge, { variant: 'destructive' }, () => [
      h(IconTrash, { class: 'mr-1 h-3 w-3' }),
      t('forum.status.deleted'),
    ])
  }

  if (isLocked) {
    return h(Badge, { variant: 'secondary' }, () => [
      h(IconLock, { class: 'mr-1 h-3 w-3' }),
      t('forum.status.locked'),
    ])
  }

  if (isPinned) {
    return h(Badge, { variant: 'default' }, () => [
      h(IconPinFilled, { class: 'mr-1 h-3 w-3' }),
      t('forum.status.pinned'),
    ])
  }

  return h(Badge, { variant: 'outline' }, () => [
    h(IconMessage, { class: 'mr-1 h-3 w-3' }),
    t('forum.status.active'),
  ])
}

/**
 * Returns the badge component for forum post flag status
 * @param t - i18n translation function
 */
export function getForumPostFlagBadge(
  isFlagged: boolean,
  t: (key: string) => string,
): VNode | null {
  if (!isFlagged) return null

  return h(Badge, { variant: 'destructive' }, () => [
    h(IconFlag, { class: 'mr-1 h-3 w-3' }),
    t('forum.status.flagged'),
  ])
}
