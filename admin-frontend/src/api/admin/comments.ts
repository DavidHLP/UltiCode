import apiClient from '../client'

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

export interface CommentsResponse {
  data: Comment[]
  meta: {
    total: number
    page: number
    limit: number
    totalPages: number
  }
}

export interface BulkCommentActionDto {
  ids: string[]
  type: CommentType
  action: 'delete' | 'unflag'
}

export const commentsApi = {
  async getComments(params: CommentQueryParams): Promise<CommentsResponse> {
    const response = await apiClient.get<CommentsResponse>('/admin/comments', { params })
    return response.data
  },

  async flagComment(id: string, type: CommentType, reason: string): Promise<Comment> {
    const response = await apiClient.patch<Comment>(`/admin/comments/${type}/${id}/flag`, {
      reason,
    })
    return response.data
  },

  async unflagComment(id: string, type: CommentType): Promise<Comment> {
    const response = await apiClient.patch<Comment>(`/admin/comments/${type}/${id}/unflag`)
    return response.data
  },

  async deleteComment(id: string, type: CommentType): Promise<void> {
    await apiClient.delete(`/admin/comments/${type}/${id}`)
  },

  async bulkAction(data: BulkCommentActionDto): Promise<void> {
    await apiClient.post('/admin/comments/bulk', data)
  },
}
