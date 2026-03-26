import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  forumApi,
  type ForumPost,
  type ForumCommunity,
  type ForumPostQueryParams,
  type BulkForumActionType,
  type ForumPostDetail,
  type AuditEntry,
} from '@/api/admin/forum'

export const useForumStore = defineStore('adminForum', () => {
  // Posts State
  const posts = ref<ForumPost[]>([])
  const totalPosts = ref(0)
  const postsLoading = ref(false)
  const postsError = ref<string | null>(null)

  // Communities State
  const communities = ref<ForumCommunity[]>([])
  const communitiesLoading = ref(false)

  // Post Detail State
  const currentPost = ref<ForumPostDetail | null>(null)
  const postLoading = ref(false)
  const postError = ref<string | null>(null)
  const auditHistory = ref<AuditEntry[]>([])

  // Actions
  async function fetchPosts(params: ForumPostQueryParams = {}) {
    postsLoading.value = true
    postsError.value = null
    try {
      const response = await forumApi.getPosts(params)
      posts.value = response.items
      totalPosts.value = response.total
    } catch (err: unknown) {
      postsError.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch forum posts'
      console.error('Failed to fetch forum posts:', err)
    } finally {
      postsLoading.value = false
    }
  }

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
    postsLoading.value = true
    try {
      await forumApi.deletePost(id)
      // Optimistic update or refresh
      await fetchPosts() // Refreshing is safer for pagination
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to delete post'
      postsError.value = msg
      throw err
    } finally {
      postsLoading.value = false
    }
  }

  async function togglePin(post: ForumPost) {
    postsLoading.value = true
    try {
      if (post.is_pinned) {
        await forumApi.unpinPost(post.id)
      } else {
        await forumApi.pinPost(post.id)
      }
      // Update locally
      const index = posts.value.findIndex((p) => p.id === post.id)
      if (index !== -1 && posts.value[index]) {
        posts.value[index].is_pinned = !post.is_pinned
      }
    } catch (err: unknown) {
      postsError.value = 'Failed to update pin status'
      throw err
    } finally {
      postsLoading.value = false
    }
  }

  async function toggleLock(post: ForumPost) {
    postsLoading.value = true
    try {
      if (post.is_locked) {
        await forumApi.unlockPost(post.id)
      } else {
        await forumApi.lockPost(post.id)
      }
      // Update locally
      const index = posts.value.findIndex((p) => p.id === post.id)
      if (index !== -1 && posts.value[index]) {
        posts.value[index].is_locked = !post.is_locked
      }
    } catch (err: unknown) {
      postsError.value = 'Failed to update lock status'
      throw err
    } finally {
      postsLoading.value = false
    }
  }

  async function bulkAction(ids: string[], action: BulkForumActionType) {
    postsLoading.value = true
    try {
      await forumApi.bulkAction({ ids, action })
      await fetchPosts()
    } catch (err: unknown) {
      postsError.value = 'Failed to perform bulk action'
      throw err
    } finally {
      postsLoading.value = false
    }
  }

  function clearError() {
    postsError.value = null
  }

  // Post Detail Actions
  async function fetchPostDetail(id: string) {
    postLoading.value = true
    postError.value = null
    try {
      currentPost.value = await forumApi.getPostDetail(id)
    } catch (err: unknown) {
      postError.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch post details'
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
        currentPost.value.is_flagged = true
        currentPost.value.flagged_reason = reason
        currentPost.value.flagged_at = new Date().toISOString()
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
        currentPost.value.is_flagged = false
        currentPost.value.flagged_reason = undefined
        currentPost.value.flagged_at = undefined
      }
    } catch (err) {
      console.error('Failed to unflag post:', err)
      throw err
    }
  }

  return {
    posts,
    totalPosts,
    postsLoading,
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
