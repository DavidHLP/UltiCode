import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  commentsApi,
  type Comment,
  type CommentQueryParams,
  type CommentType,
  type BulkCommentActionDto,
} from '@/api/admin/comments'

export const useCommentsStore = defineStore('adminComments', () => {
  const comments = ref<Comment[]>([])
  const total = ref(0)
  const currentComment = ref<Comment | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchComments(params: CommentQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await commentsApi.getComments(params)
      comments.value = response.items
      total.value = response.total
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch comments'
      console.error('Failed to fetch comments:', err)
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
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch comment'
      console.error('Failed to fetch comment:', err)
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
      if (index !== -1) {
        comments.value[index] = updatedComment
      }
      return updatedComment
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to flag comment'
      console.error('Failed to flag comment:', err)
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
      if (index !== -1) {
        comments.value[index] = updatedComment
      }
      return updatedComment
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to unflag comment'
      console.error('Failed to unflag comment:', err)
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
      // For soft delete, we might want to just mark it deleted locally or remove it
      // Assuming we want to reflect the server state (which sets is_deleted=true)
      // But typically lists filter out deleted items unless specifically requested.
      // Let's assume we remove it from the view if we are not viewing deleted items.
      const index = comments.value.findIndex((c) => c.id === id)
      const comment = comments.value[index]
      if (comment) {
        comment.isDeleted = true
      }
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to delete comment'
      console.error('Failed to delete comment:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function bulkAction(data: BulkCommentActionDto) {
    loading.value = true
    error.value = null
    try {
      await commentsApi.bulkAction(data)
      // Refresh list
      await fetchComments() // Note: this might need params if we want to keep current view
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to perform bulk action'
      console.error('Failed to perform bulk action:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    comments.value = []
    total.value = 0
    loading.value = false
    error.value = null
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
    bulkAction,
    clearError,
    reset,
  }
})
