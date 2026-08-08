import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

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

export interface ProblemTagResponse {
  id: string
  label: string
}

export const tagsApi = {
  getTags(query: TagQuery) {
    return apiGet<TagListResponse>('/admin/tags', { params: query })
  },

  /**
   * Get all tags for a given type, returns ProblemTag shape { id, label }
   */
  async getAllTags(type: TagType = TagType.PROBLEM): Promise<ProblemTagResponse[]> {
    const response = await apiGet<TagListResponse>('/admin/tags', {
      params: { type, limit: 1000, sortBy: 'name', sortOrder: 'asc' },
    })
    return response.data.map((tag) => ({ id: tag.id, label: tag.name }))
  },

  getTag(id: string, type: TagType) {
    return apiGet<Tag>(`/admin/tags/${id}`, { params: { type } })
  },

  createTag(data: CreateTagDto) {
    return apiPost<Tag>('/admin/tags', data)
  },

  updateTag(id: string, data: UpdateTagDto) {
    return apiPatch<Tag>(`/admin/tags/${id}`, data)
  },

  deleteTag(id: string, type: TagType) {
    return apiDelete(`/admin/tags/${id}`, { params: { type } })
  },

  mergeTag(data: { sourceId: string; targetTagId: string; type: TagType }) {
    return apiPost('/admin/tags/merge', data)
  },
}
