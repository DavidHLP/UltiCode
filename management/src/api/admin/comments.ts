import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export type CommentType = 'forum' | 'solution'

export interface Comment {
  id: string
  content: string
  createdAt: string
  updatedAt: string
  authorId: string
  parentId?: string

  // Type identification
  type: CommentType
  parentTitle?: string // unified parent title (post title or solution title)

  // Moderation
  isFlagged: boolean
  flaggedReason?: string
  flaggedAt?: string
  isDeleted: boolean
  deletedAt?: string
  deletedBy?: string

  author: {
    id: string
    username: string
    avatar?: string
  }
}

export interface CommentQueryParams {
  page?: number
  limit?: number
  search?: string
  type?: CommentType
  isFlagged?: boolean
  isDeleted?: boolean
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface BulkCommentActionDto {
  ids: string[]
  type: CommentType
  action: 'delete' | 'unflag'
}

export const commentsApi = {
  async getComments(params: CommentQueryParams): Promise<PageResult<Comment>> {
    return apiGet<PageResult<Comment>>('/admin/comments', { params })
  },

  async flagComment(id: string, type: CommentType, reason: string): Promise<Comment> {
    return apiPatch<Comment>(`/admin/comments/${type}/${id}/flag`, { reason })
  },

  async unflagComment(id: string, type: CommentType): Promise<Comment> {
    return apiPatch<Comment>(`/admin/comments/${type}/${id}/unflag`)
  },

  async deleteComment(id: string, type: CommentType): Promise<void> {
    await apiDelete(`/admin/comments/${type}/${id}`)
  },

  async bulkAction(data: BulkCommentActionDto): Promise<void> {
    await apiPost('/admin/comments/bulk', data)
  },
}
