import type { PageResult } from '@/shared/domain-types/src'
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export interface ProblemList {
  id: string
  name: string
  description?: string
  authorId: string
  authorName?: string
  authorUsername?: string
  isPublic: boolean
  isFeatured: boolean
  bannerTag?: string
  bannerIcon?: string
  bannerTheme?: string
  bannerOrder?: number
  createdAt: string
  updatedAt: string
  problemCount?: number
  isSaved?: boolean
}

export interface ProblemListProblem {
  id: number
  slug: string
  title: string
  difficulty: string
  status: string
  sortOrder: number
  addedAt: string
}

export interface ProblemListDetail extends ProblemList {
  problems: ProblemListProblem[]
  isOwner?: boolean
}

export interface ProblemListQuery {
  search?: string
  isFeatured?: boolean
  isPublic?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface CreateProblemListDto {
  name: string
  description?: string
  isPublic?: boolean
  bannerTag?: string
  bannerIcon?: string
  bannerTheme?: string
  bannerOrder?: number
}

export interface UpdateProblemListProblemsDto {
  problems: {
    problemId: number
    sortOrder: number
  }[]
}

export interface UpdateBasicInfoDto {
  name: string
  description?: string
}

export interface UpdateVisibilityDto {
  isPublic?: boolean
  isFeatured?: boolean
}

export interface UpdateBannerDto {
  bannerTag?: string
  bannerIcon?: string
  bannerTheme?: string
  bannerOrder?: number
}

// PageResult type for paginated responses (matches backend PageResult<T>)

export const adminProblemListsApi = {
  async getLists(query: ProblemListQuery): Promise<PageResult<ProblemList>> {
    return apiGet<PageResult<ProblemList>>('/admin/problem-lists', { params: query })
  },

  async getList(id: string): Promise<ProblemListDetail> {
    const response = await apiGet<ProblemListDetail>(`/admin/problem-lists/${id}`)
    return response
  },

  async createList(data: CreateProblemListDto): Promise<ProblemList> {
    const response = await apiPost<ProblemList>('/admin/problem-lists', data)
    return response
  },

  async deleteList(id: string): Promise<void> {
    await apiDelete(`/admin/problem-lists/${id}`)
  },

  async updateListProblems(id: string, data: UpdateProblemListProblemsDto): Promise<void> {
    await apiPost(`/admin/problem-lists/${id}/problems`, data)
  },

  async updateBasicInfo(id: string, data: UpdateBasicInfoDto): Promise<ProblemList> {
    return apiPatch<ProblemList>(`/admin/problem-lists/${id}/basic-info`, data)
  },

  async updateVisibility(id: string, data: UpdateVisibilityDto): Promise<ProblemList> {
    return apiPatch<ProblemList>(`/admin/problem-lists/${id}/visibility`, data)
  },

  async updateBanner(id: string, data: UpdateBannerDto): Promise<ProblemList> {
    return apiPatch<ProblemList>(`/admin/problem-lists/${id}/banner`, data)
  },
}
