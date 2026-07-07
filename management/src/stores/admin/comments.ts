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

  async function bulkAction(data: BulkCommentActionDto) {
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
    bulkAction,
    clearError,
    reset,
  }
})
