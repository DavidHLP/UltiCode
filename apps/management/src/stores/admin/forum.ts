import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  forumApi,
  type ForumPost,
  type ForumCommunity,
  type ForumPostQueryParams,
  type BulkForumActionType,
  type ForumPostDetail,
} from '@/api/admin/forum'
import { extractApiErrorMessage } from '@/utils/error'
import type { AuditLog } from '@/api/admin/audit'
import { createCollectionSlice } from '@/stores/createCollectionSlice'

export const useForumStore = defineStore('adminForum', () => {
  // Posts State
  const collection = createCollectionSlice<ForumPost, ForumPostQueryParams>({
    load: async (params = {}, signal) => {
      const response = await forumApi.getPosts(params, signal)
      return { items: response.items, total: response.total }
    },
  })
  const posts = collection.items
  const totalPosts = collection.total
  const postsLoading = collection.mutationLoading
  const combinedPostsLoading = computed(() => collection.isLoading.value || postsLoading.value)
  const postsError = computed(() => collection.error.value || collection.mutationError.value)
  const fetchPosts = collection.fetch

  // Communities State
  const communities = ref<ForumCommunity[]>([])
  const communitiesLoading = ref(false)

  // Post Detail State
  const currentPost = ref<ForumPostDetail | null>(null)
  const postLoading = ref(false)
  const postError = ref<string | null>(null)
  const auditHistory = ref<AuditLog[]>([])

  // Actions
  async function fetchCommunities() {
    communitiesLoading.value = true
    try {
      // Fetch all communities (or a reasonably large page) for filtering
      // For now, let's fetch the first page. If we have many communities, we might need a search-select.
      const response = await forumApi.getCommunities(1, 100)
      communities.value = response.items
    } catch (err) {
      console.error('Failed to fetch communities:', err)
    } finally {
      communitiesLoading.value = false
    }
  }

  async function deletePost(id: string) {
    return collection.runMutation(async () => {
      await forumApi.deletePost(id)
      // Optimistic update or refresh
      await fetchPosts() // Refreshing is safer for pagination
    }, 'Failed to delete post')
  }

  async function togglePin(post: ForumPost) {
    return collection.runMutation(async () => {
      if (post.isPinned) {
        await forumApi.unpinPost(post.id)
      } else {
        await forumApi.pinPost(post.id)
      }
      // Update locally
      const index = posts.value.findIndex((p) => p.id === post.id)
      if (index !== -1 && posts.value[index]) {
        posts.value[index].isPinned = !post.isPinned
      }
    }, 'Failed to update pin status')
  }

  async function toggleLock(post: ForumPost) {
    return collection.runMutation(async () => {
      if (post.isLocked) {
        await forumApi.unlockPost(post.id)
      } else {
        await forumApi.lockPost(post.id)
      }
      // Update locally
      const index = posts.value.findIndex((p) => p.id === post.id)
      if (index !== -1 && posts.value[index]) {
        posts.value[index].isLocked = !post.isLocked
      }
    }, 'Failed to update lock status')
  }

  async function bulkAction(ids: string[], action: BulkForumActionType) {
    return collection.runMutation(async () => {
      await forumApi.bulkAction({ ids, action })
      await fetchPosts()
    }, 'Failed to perform bulk action')
  }

  function clearError() {
    collection.clearError()
    collection.mutationError.value = null
  }

  // Post Detail Actions
  async function fetchPostDetail(id: string) {
    postLoading.value = true
    postError.value = null
    try {
      currentPost.value = await forumApi.getPostDetail(id)
    } catch (err: unknown) {
      postError.value = extractApiErrorMessage(err, 'Failed to fetch post details')
      console.error('Failed to fetch post details:', err)
      throw err
    } finally {
      postLoading.value = false
    }
  }

  async function fetchPostAuditHistory(id: string) {
    try {
      auditHistory.value = await forumApi.getPostAuditHistory(id)
    } catch (err) {
      console.error('Failed to fetch audit history:', err)
      throw err
    }
  }

  async function flagPost(id: string, reason: string) {
    try {
      await forumApi.flagPost(id, reason)
      if (currentPost.value?.id === id) {
        currentPost.value.isFlagged = true
        currentPost.value.flaggedReason = reason
        currentPost.value.flaggedAt = new Date().toISOString()
      }
    } catch (err) {
      console.error('Failed to flag post:', err)
      throw err
    }
  }

  async function unflagPost(id: string) {
    try {
      await forumApi.unflagPost(id)
      if (currentPost.value?.id === id) {
        currentPost.value.isFlagged = false
        currentPost.value.flaggedReason = undefined
        currentPost.value.flaggedAt = undefined
      }
    } catch (err) {
      console.error('Failed to unflag post:', err)
      throw err
    }
  }

  return {
    items: collection.items,
    total: collection.total,
    isLoading: collection.isLoading,
    error: collection.error,
    fetch: collection.fetch,
    cancel: collection.cancel,
    posts,
    totalPosts,
    postsLoading: combinedPostsLoading,
    postsError,
    communities,
    communitiesLoading,
    currentPost,
    postLoading,
    postError,
    auditHistory,
    fetchPosts,
    fetchCommunities,
    fetchPostDetail,
    fetchPostAuditHistory,
    deletePost,
    togglePin,
    toggleLock,
    flagPost,
    unflagPost,
    bulkAction,
    clearError,
  }
})
