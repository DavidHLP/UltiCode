import type { VNode } from 'vue'
import { badge } from '@/components/ui/terminal'
import { IconFlag, IconTrash, IconPinFilled, IconLock, IconMessage } from '@tabler/icons-vue'

/**
 * Forum post status
 */
export type ForumPostStatus = 'ACTIVE' | 'DELETED' | 'LOCKED' | 'PINNED'

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
  if (isDeleted) return badge({ color: 'error', label: t('forum.status.deleted'), icon: IconTrash })
  if (isLocked) return badge({ color: 'warning', label: t('forum.status.locked'), icon: IconLock })
  if (isPinned)
    return badge({ color: 'success', label: t('forum.status.pinned'), icon: IconPinFilled })
  return badge({ color: 'neutral', label: t('forum.status.active'), icon: IconMessage })
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
  return badge({ color: 'error', label: t('forum.status.flagged'), icon: IconFlag, pulse: true })
}
