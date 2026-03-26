import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export type CommentType = 'forum' | 'solution'

export interface Comment {
  id: string
  content: string
  created_at: string
  updated_at: string
  author_id: string
  parent_id?: string

  // Type identification
  type: CommentType
  parentId: string // unified parent ID (post_id or solution_id)
  parentTitle?: string // unified parent title

  // Moderation
  is_flagged: boolean
  flagged_reason?: string
  flagged_at?: string
  is_deleted: boolean
  deleted_at?: string
  deleted_by?: string

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
  is_flagged?: boolean
  is_deleted?: boolean
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
