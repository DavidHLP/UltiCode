import type { PageResult } from "@/shared/domain-types/src"
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export type CommentType = 'forum' | 'solution'

export interface Comment {
  id: string
  content: string
  createdAt: string
  updatedAt: string
  authorId: string
  parentId?: string
  parentEntityId?: string

  type: CommentType
  parentTitle?: string

  author: {
    id: string
    username: string
    avatar?: string
  }

  isFlagged: boolean
  flaggedReason?: string
  flaggedAt?: string
  isDeleted: boolean
  deletedAt?: string
  deletedBy?: string
}

export interface CommentQueryParams {
  page?: number
  limit?: number
  search?: string
  type?: CommentType
  isFlagged?: boolean
  isDeleted?: boolean
  parentEntityId?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
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

  async getComment(id: string, type: CommentType): Promise<Comment> {
    return apiGet<Comment>(`/admin/comments/${type}/${id}`)
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
