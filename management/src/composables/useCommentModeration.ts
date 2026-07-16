import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'

import { useCommentsStore } from '@/stores/admin/comments'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'
import type { Comment, CommentType } from '@/api/admin/comments'

export interface UseCommentModerationOptions {
  refresh?: () => Promise<void> | void
}

export type CommentTypeGroup = Partial<Record<CommentType, string[]>>

export interface UseCommentModerationReturn {
  canModerateForum: ComputedRef<boolean>
  canModerateSolution: ComputedRef<boolean>
  canDeleteForum: ComputedRef<boolean>
  canDeleteSolution: ComputedRef<boolean>
  canModerate: (comment: Pick<Comment, 'type'>) => boolean
  canDelete: (comment: Pick<Comment, 'type'>) => boolean

  selectedCommentId: Ref<string | null>
  selectedCommentType: Ref<CommentType | null>
  selectedCommentContent: Ref<string | null>

  deleteDialogOpen: Ref<boolean>
  flagDialogOpen: Ref<boolean>
  bulkDeleteDialogOpen: Ref<boolean>

  bulkActionLoading: Ref<boolean>

  confirmDelete: (comment: Comment) => void
  openFlagDialog: (comment: Comment) => void
  closeDialogs: () => void
  promptBulkDelete: () => void
  dismissBulkDelete: () => void

  handleDeleteComment: (id: string | number) => Promise<void>
  handleFlagComment: (id: string | number, reason?: string) => Promise<void>

  unflagComment: (comment: Pick<Comment, 'id' | 'type'>) => Promise<void>

  bulkUnflag: (rows: Comment[]) => Promise<boolean>
  bulkDelete: (rows: Comment[]) => Promise<boolean>

  groupByType: (rows: Pick<Comment, 'id' | 'type'>[]) => CommentTypeGroup
}

export function useCommentModeration(
  options: UseCommentModerationOptions = {},
): UseCommentModerationReturn {
  const { t } = useI18n()
  const commentsStore = useCommentsStore()
  const authStore = useAuthStore()
  const refresh = options.refresh

  const canModerateForum = computed(() =>
    authStore.hasPermission(
      PERM.MODERATE_FORUM_COMMENT.action,
      PERM.MODERATE_FORUM_COMMENT.resource,
    ),
  )
  const canModerateSolution = computed(() =>
    authStore.hasPermission(
      PERM.MODERATE_SOLUTION_COMMENT.action,
      PERM.MODERATE_SOLUTION_COMMENT.resource,
    ),
  )
  const canDeleteForum = computed(() =>
    authStore.hasPermission(
      PERM.DELETE_FORUM_COMMENT.action,
      PERM.DELETE_FORUM_COMMENT.resource,
    ),
  )
  const canDeleteSolution = computed(() =>
    authStore.hasPermission(
      PERM.DELETE_SOLUTION_COMMENT.action,
      PERM.DELETE_SOLUTION_COMMENT.resource,
    ),
  )
  function canModerate(comment: Pick<Comment, 'type'>): boolean {
    if (comment.type === 'forum') return canModerateForum.value
    if (comment.type === 'solution') return canModerateSolution.value
    return false
  }
  function canDelete(comment: Pick<Comment, 'type'>): boolean {
    if (comment.type === 'forum') return canDeleteForum.value
    if (comment.type === 'solution') return canDeleteSolution.value
    return false
  }

  const selectedCommentId = ref<string | null>(null)
  const selectedCommentType = ref<CommentType | null>(null)
  const selectedCommentContent = ref<string | null>(null)

  const deleteDialogOpen = ref(false)
  const flagDialogOpen = ref(false)
  const bulkDeleteDialogOpen = ref(false)

  const bulkActionLoading = ref(false)

  function confirmDelete(comment: Comment): void {
    selectedCommentId.value = comment.id
    selectedCommentType.value = comment.type
    selectedCommentContent.value = comment.content
    deleteDialogOpen.value = true
  }

  function openFlagDialog(comment: Comment): void {
    selectedCommentId.value = comment.id
    selectedCommentType.value = comment.type
    selectedCommentContent.value = comment.content
    flagDialogOpen.value = true
  }

  function closeDialogs(): void {
    selectedCommentId.value = null
    selectedCommentType.value = null
    selectedCommentContent.value = null
    deleteDialogOpen.value = false
    flagDialogOpen.value = false
    bulkDeleteDialogOpen.value = false
  }

  function promptBulkDelete(): void {
    bulkDeleteDialogOpen.value = true
  }

  function dismissBulkDelete(): void {
    bulkDeleteDialogOpen.value = false
  }

  async function handleDeleteComment(id: string | number): Promise<void> {
    if (!selectedCommentType.value) {
      throw new Error('Comment type is required')
    }
    await commentsStore.deleteComment(String(id), selectedCommentType.value)
  }

  async function handleFlagComment(id: string | number, reason?: string): Promise<void> {
    if (!selectedCommentType.value) {
      throw new Error('Comment type is required')
    }
    await commentsStore.flagComment(String(id), selectedCommentType.value, reason || '')
  }

  async function unflagComment(comment: Pick<Comment, 'id' | 'type'>): Promise<void> {
    try {
      await commentsStore.unflagComment(comment.id, comment.type)
      toast.success(t('comments.toast.unflaggedSuccessfully'))
      if (refresh) await refresh()
    } catch {
      toast.error(t('comments.toast.failedToUnflag'))
    }
  }

  function groupByType(rows: Pick<Comment, 'id' | 'type'>[]): CommentTypeGroup {
    const grouped: CommentTypeGroup = {}
    for (const row of rows) {
      const list = grouped[row.type] ?? []
      list.push(row.id)
      grouped[row.type] = list
    }
    return grouped
  }

  async function dispatchBulk(
    rows: Comment[],
    action: 'unflag' | 'delete',
    successKey: string,
    failureKey: string,
  ): Promise<boolean> {
    if (rows.length === 0) return true
    const grouped = groupByType(rows)
    bulkActionLoading.value = true
    try {
      await Promise.all(
        Object.entries(grouped).map(([type, ids]) => {
          if (!ids || ids.length === 0) return Promise.resolve()
          return commentsStore.bulkAction({ ids, type: type as CommentType, action })
        }),
      )
      if (refresh) await refresh()
      toast.success(t(successKey))
      return true
    } catch {
      toast.error(t(failureKey))
      return false
    } finally {
      bulkActionLoading.value = false
    }
  }

  function bulkUnflag(rows: Comment[]): Promise<boolean> {
    return dispatchBulk(
      rows,
      'unflag',
      'comments.toast.bulkUnflaggedSuccessfully',
      'comments.toast.failedToBulkUnflag',
    )
  }

  function bulkDelete(rows: Comment[]): Promise<boolean> {
    return dispatchBulk(
      rows,
      'delete',
      'comments.toast.bulkDeletedSuccessfully',
      'comments.toast.failedToBulkDelete',
    )
  }

  return {
    canModerateForum,
    canModerateSolution,
    canDeleteForum,
    canDeleteSolution,
    canModerate,
    canDelete,
    selectedCommentId,
    selectedCommentType,
    selectedCommentContent,
    deleteDialogOpen,
    flagDialogOpen,
    bulkDeleteDialogOpen,
    bulkActionLoading,
    confirmDelete,
    openFlagDialog,
    closeDialogs,
    promptBulkDelete,
    dismissBulkDelete,
    handleDeleteComment,
    handleFlagComment,
    unflagComment,
    bulkUnflag,
    bulkDelete,
    groupByType,
  }
}
