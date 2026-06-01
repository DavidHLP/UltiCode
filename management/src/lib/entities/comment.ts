import { h, type VNode } from 'vue'
import { badge } from '@/components/ui/terminal'
import { IconFlag, IconTrash, IconMessage, IconFileText } from '@tabler/icons-vue'
import { type CommentType } from '@/api/admin/comments'

/**
 * Returns the badge component for a comment type
 * @param t - i18n translation function
 */
export function getCommentTypeBadge(
  type: CommentType,
  t: (key: string, fallback?: string) => string,
): VNode {
  return badge({ color: 'neutral', label: t(`comments.type.${type}`, type) })
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
 * Returns the badge component for a comment status
 * @param t - i18n translation function
 */
export function getCommentStatusBadge(
  isFlagged: boolean,
  isDeleted: boolean,
  t: (key: string) => string,
): VNode {
  if (isDeleted)
    return badge({ color: 'error', label: t('comments.status.deleted'), icon: IconTrash })
  if (isFlagged)
    return badge({ color: 'error', label: t('comments.status.flagged'), icon: IconFlag })
  return badge({ color: 'success', label: t('comments.status.active') })
}
