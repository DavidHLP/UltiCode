import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  commentsApi,
  type Comment,
  type CommentQueryParams,
  type CommentType,
  type BulkCommentActionDto,
} from '@/api/admin/comments'
import { extractApiErrorMessage } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

/**
 * Grouping shape used by the bulk-moderation workflow. The store owns
 * grouping because bulk dispatch is part of the moderation policy, not the
 * view layer.
 */
export type CommentTypeGroup = Partial<Record<CommentType, string[]>>

export const useCommentsStore = defineStore('adminComments', () => {
  const comments = ref<Comment[]>([])
  const total = ref(0)
  const currentComment = ref<Comment | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const lastParams = ref<CommentQueryParams>({})

  async function fetchComments(params: CommentQueryParams = {}) {
    loading.value = true
    error.value = null
    lastParams.value = { ...params }
    try {
      const response = await commentsApi.getComments(params)
      comments.value = response.items.filter((c): c is Comment => c !== null)
      total.value = response.total
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch comments')
    } finally {
      loading.value = false
    }
  }

  async function fetchComment(id: string, type: CommentType) {
    loading.value = true
    error.value = null
    try {
      const comment = await commentsApi.getComment(id, type)
      currentComment.value = comment
      return comment
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch comment')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function flagComment(id: string, type: CommentType, reason: string) {
    loading.value = true
    error.value = null
    try {
      const updatedComment = await commentsApi.flagComment(id, type, reason)
      const index = comments.value.findIndex((c) => c.id === id)
      if (index !== -1 && updatedComment) {
        comments.value = comments.value.map((c) => (c.id === id ? updatedComment : c))
      }
      return updatedComment
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to flag comment')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function unflagComment(id: string, type: CommentType) {
    loading.value = true
    error.value = null
    try {
      const updatedComment = await commentsApi.unflagComment(id, type)
      const index = comments.value.findIndex((c) => c.id === id)
      if (index !== -1 && updatedComment) {
        comments.value = comments.value.map((c) => (c.id === id ? updatedComment : c))
      }
      return updatedComment
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to unflag comment')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function deleteComment(id: string, type: CommentType) {
    loading.value = true
    error.value = null
    try {
      await commentsApi.deleteComment(id, type)
      const index = comments.value.findIndex((c) => c.id === id)
      if (index !== -1) {
        comments.value = [
          ...comments.value.slice(0, index),
          { ...comments.value[index], isDeleted: true },
          ...comments.value.slice(index + 1),
        ]
      }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to delete comment')
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Internal transport pass-through for a single (type, action) bulk call.
   * Not exposed on the public store surface — the deep module contracts on
   * the {@link bulkModerate} workflow, not on the wire shape.
   */
  async function runBulkAction(data: BulkCommentActionDto): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await commentsApi.bulkAction(data)
      await fetchComments(lastParams.value)
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to perform bulk action')
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Group rows by {@link CommentType}, preserving insertion order within
   * each type. Exposed so the view layer can render grouped summaries
   * without duplicating the grouping policy.
   */
  function groupByType(rows: Pick<Comment, 'id' | 'type'>[]): CommentTypeGroup {
    const grouped: CommentTypeGroup = {}
    for (const row of rows) {
      const list = grouped[row.type] ?? []
      list.push(row.id)
      grouped[row.type] = list
    }
    return grouped
  }

  /**
   * Bulk-moderation workflow: group rows by type and issue one bulk API
   * call per non-empty type, then re-fetch the current view via
   * {@link fetchComments}. Empty input is a no-op so callers can forward
   * user selections without a pre-check. Re-throws on failure so the view
   * layer can surface a toast.
   */
  async function bulkModerate(
    rows: Pick<Comment, 'id' | 'type'>[],
    action: 'delete' | 'unflag',
  ): Promise<void> {
    if (rows.length === 0) return
    const grouped = groupByType(rows)
    await Promise.all(
      Object.entries(grouped).map(([type, ids]) => {
        if (!ids || ids.length === 0) return Promise.resolve()
        return runBulkAction({ ids, type: type as CommentType, action })
      }),
    )
  }

  /**
   * Moderation policy: route the moderate permission by comment type. The
   * store owns this so every view sees one authorization seam instead of
   * each composable re-deriving it from auth constants.
   */
  function canModerate(comment: Pick<Comment, 'type'>): boolean {
    const authStore = useAuthStore()
    if (comment.type === 'forum') {
      return authStore.hasPermission(
        PERM.MODERATE_FORUM_COMMENT.action,
        PERM.MODERATE_FORUM_COMMENT.resource,
      )
    }
    if (comment.type === 'solution') {
      return authStore.hasPermission(
        PERM.MODERATE_SOLUTION_COMMENT.action,
        PERM.MODERATE_SOLUTION_COMMENT.resource,
      )
    }
    return false
  }

  /**
   * Moderation policy: route the delete permission by comment type.
   */
  function canDelete(comment: Pick<Comment, 'type'>): boolean {
    const authStore = useAuthStore()
    if (comment.type === 'forum') {
      return authStore.hasPermission(
        PERM.DELETE_FORUM_COMMENT.action,
        PERM.DELETE_FORUM_COMMENT.resource,
      )
    }
    if (comment.type === 'solution') {
      return authStore.hasPermission(
        PERM.DELETE_SOLUTION_COMMENT.action,
        PERM.DELETE_SOLUTION_COMMENT.resource,
      )
    }
    return false
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    comments.value = []
    total.value = 0
    loading.value = false
    error.value = null
    lastParams.value = {}
  }

  return {
    comments,
    total,
    currentComment,
    loading,
    error,
    fetchComments,
    fetchComment,
    flagComment,
    unflagComment,
    deleteComment,
    bulkModerate,
    canModerate,
    canDelete,
    groupByType,
    clearError,
    reset,
  }
})
