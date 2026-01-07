import apiClient from '../client'

export enum TagType {
  PROBLEM = 'PROBLEM',
  FORUM = 'FORUM',
}

export interface Tag {
  id: string
  name: string // Mapped from label for ProblemTag
  slug?: string
  description?: string
  color?: string
  usage_count: number
  type: TagType
  created_at: string
  updated_at: string
}

export interface TagQuery {
  search?: string
  type?: TagType
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface CreateTagDto {
  name: string
  slug?: string
  description?: string
  color?: string
  type: TagType
}

export interface UpdateTagDto {
  name?: string
  slug?: string
  description?: string
  color?: string
  type: TagType // Required for backend to know which table to update
}

export interface TagListResponse {
  data: Tag[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export const tagsApi = {
  getTags(query: TagQuery) {
    return apiClient.get<TagListResponse>('/admin/tags', { params: query })
  },

  getTag(id: string, type: TagType) {
    return apiClient.get<Tag>(`/admin/tags/${id}`, { params: { type } })
  },

  createTag(data: CreateTagDto) {
    return apiClient.post<Tag>('/admin/tags', data)
  },

  updateTag(id: string, data: UpdateTagDto) {
    return apiClient.patch<Tag>(`/admin/tags/${id}`, data)
  },

  deleteTag(id: string, type: TagType) {
    return apiClient.delete(`/admin/tags/${id}`, { params: { type } })
  },

  mergeTag(data: { sourceId: string; targetTagId: string; type: TagType }) {
    return apiClient.post('/admin/tags/merge', data)
  },
}
