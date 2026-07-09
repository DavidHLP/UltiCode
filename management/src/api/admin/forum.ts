import type { PageResult } from "@/shared/domain-types/src"
import { apiGet, apiPost, apiDelete } from '@/utils/request'
import type { AuditLog } from '@/api/admin/audit'

export interface ForumUser {
  id: string
  username: string
  avatar?: string
}

export interface ForumCommunity {
  id: string
  name: string
  slug: string
  description?: string
  postCount?: number
  memberCount?: number
  createdAt: string
}

export interface ForumPost {
  id: string
  title: string
  excerpt: string
  content?: string // Full content might not be in list view
  userId: string
  communityId: string
  viewCount: number
  commentCount: number
  upvotes: number
  downvotes: number
  isPinned: boolean
  isLocked: boolean
  isFlagged: boolean
  flaggedReason?: string
  flaggedAt?: string
  isDeleted: boolean
  deletedAt?: string
  createdAt: string
  updatedAt: string

  author: ForumUser
  community: {
    id: string
    name: string
    slug: string
  }
}

export interface ForumPostQueryParams {
  page?: number
  limit?: number
  search?: string
  communityId?: string
  authorId?: string
  isFlagged?: boolean
  isPinned?: boolean
  isLocked?: boolean
  isDeleted?: boolean
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface ForumPostsResponse {
  data: ForumPost[]
  meta: {
    total: number
    page: number
    limit: number
    totalPages: number
  }
  // The backend seems to return top-level props instead of meta object in some cases,
  // but let's check the controller again.
  // Controller returns: { data, total, page, limit, totalPages } at top level.
  // I should adjust the client to match or map it.
}

// Standard PageResult interface for paginated responses

export type BulkForumActionType = 'delete' | 'pin' | 'unpin' | 'lock' | 'unlock' | 'unflag'

export interface BulkForumActionDto {
  ids: string[]
  action: BulkForumActionType
}

export interface BulkActionResult {
  id: string
  success: boolean
  error?: string
}

export interface ForumPostDetail extends ForumPost {
  fullContent?: string
  moderationHistory?: AuditLog[]
}

export const forumApi = {
  async getPosts(params: ForumPostQueryParams): Promise<PageResult<ForumPost>> {
    const response = await apiGet<
      PageResult<
        ForumPost & {
          username?: string
          avatar?: string
          communityName?: string
          communitySlug?: string
        }
      >
    >('/admin/forum/posts', { params })
    return {
      ...response,
      items: response.items.map((post) => ({
        ...post,
        author: {
          id: post.userId,
          username: post.username ?? post.userId,
          avatar: post.avatar,
        },
        community: {
          id: post.communityId,
          name: post.communityName ?? 'Unknown',
          slug: post.communitySlug ?? '',
        },
      })),
    }
  },

  async getCommunities(page = 1, limit = 20): Promise<PageResult<ForumCommunity>> {
    const response = await apiGet<PageResult<ForumCommunity>>('/admin/forum/communities', {
      params: { page, limit },
    })
    return response
  },

  async deletePost(id: string): Promise<void> {
    await apiDelete(`/admin/forum/posts/${id}`)
  },

  async pinPost(id: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/pin`)
  },

  async unpinPost(id: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/unpin`)
  },

  async lockPost(id: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/lock`)
  },

  async unlockPost(id: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/unlock`)
  },

  async bulkAction(data: BulkForumActionDto): Promise<{ results: BulkActionResult[] }> {
    const response = await apiPost<{ results: BulkActionResult[] }>('/admin/forum/bulk', data)
    return response
  },

  // Detail view methods
  async getPostDetail(id: string): Promise<ForumPostDetail> {
    const post = await apiGet<
      ForumPostDetail & {
        username?: string
        avatar?: string
        communityName?: string
        communitySlug?: string
      }
    >(`/admin/forum/posts/${id}`)
    return {
      ...post,
      author: {
        id: post.userId,
        username: post.username ?? post.userId,
        avatar: post.avatar,
      },
      community: {
        id: post.communityId,
        name: post.communityName ?? 'Unknown',
        slug: post.communitySlug ?? '',
      },
    }
  },

  async getPostAuditHistory(id: string): Promise<AuditLog[]> {
    return apiGet<AuditLog[]>(`/admin/forum/posts/${id}/audit`)
  },

  async flagPost(id: string, reason: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/flag`, { reason })
  },

  async unflagPost(id: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/unflag`)
  },
}
