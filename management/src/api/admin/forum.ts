import { apiGet, apiPost, apiDelete } from '@/utils/request'

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
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

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

// Detail view interfaces
export interface AuditEntry {
  id: string
  action: string
  performer: {
    id: string
    username: string
  }
  entityType: string
  entityId: string
  oldValues?: Record<string, unknown>
  newValues?: Record<string, unknown>
  ipAddress?: string
  userAgent?: string
  createdAt: string
}

export interface ForumPostDetail extends ForumPost {
  fullContent?: string
  moderationHistory?: AuditEntry[]
}

export const forumApi = {
  async getPosts(params: ForumPostQueryParams): Promise<PageResult<ForumPost>> {
    const response = await apiGet<PageResult<ForumPost>>('/admin/forum/posts', { params })
    return response
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
    const response = await apiGet<ForumPostDetail>(`/admin/forum/posts/${id}`)
    return response
  },

  async getPostAuditHistory(id: string): Promise<AuditEntry[]> {
    return apiGet<AuditEntry[]>(`/admin/forum/posts/${id}/audit`)
  },

  async flagPost(id: string, reason: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/flag`, { reason })
  },

  async unflagPost(id: string): Promise<void> {
    await apiPost(`/admin/forum/posts/${id}/unflag`)
  },
}
