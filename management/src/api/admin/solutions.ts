import type { PageResult } from '@/shared/domain-types/src'
import { apiGet, apiPost, apiDelete } from '@/utils/request'

export interface SolutionListItem {
  id: string
  title: string
  language: string
  views: number
  isPublished: boolean
  isFlagged: boolean
  isDeleted: boolean
  createdAt: string
  author: {
    id: string
    username: string
    name: string
    email?: string
  }
  problem: {
    id: string
    slug: string
    title: string
    difficulty: string
  }
}

export interface Solution {
  id: string
  problemId: number
  userId: string
  title: string
  content: string
  summary?: string
  language: string
  tags?: string
  views: number
  isPublished: boolean
  publishedAt?: string
  publishedBy?: string
  isFlagged: boolean
  flaggedReason?: string
  flaggedAt?: string
  isDeleted: boolean
  deletedAt?: string
  deletedBy?: string
  createdAt: string
  updatedAt: string
  author: {
    id: string
    username: string
    name: string
    email?: string
  }
  problem: {
    id: string
    slug: string
    title: string
    difficulty: string
  }
}

export interface SolutionQueryParams {
  search?: string
  problemId?: number
  userId?: string
  isFlagged?: boolean
  isPublished?: boolean
  isDeleted?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// PageResult type for paginated responses (matches backend PageResult<T>)

export interface BulkSolutionActionDto {
  ids: string[]
  action: 'publish' | 'unpublish' | 'delete' | 'unflag'
}

export interface FlagSolutionDto {
  reason: string
}

export const solutionsApi = {
  async getSolutions(params: SolutionQueryParams): Promise<PageResult<SolutionListItem>> {
    return apiGet<PageResult<SolutionListItem>>('/admin/solutions', { params })
  },

  async getFlaggedSolutions(params: SolutionQueryParams): Promise<PageResult<SolutionListItem>> {
    return apiGet<PageResult<SolutionListItem>>('/admin/solutions/flagged', { params })
  },

  async getSolution(id: string): Promise<Solution> {
    const response = await apiGet<Solution>(`/admin/solutions/${id}`)
    return response
  },

  async flagSolution(id: string, data: FlagSolutionDto): Promise<Solution> {
    const response = await apiPost<Solution>(`/admin/solutions/${id}/flag`, data)
    return response
  },

  async unflagSolution(id: string): Promise<Solution> {
    const response = await apiPost<Solution>(`/admin/solutions/${id}/unflag`)
    return response
  },

  async deleteSolution(id: string): Promise<void> {
    await apiDelete(`/admin/solutions/${id}`)
  },

  async bulkAction(
    data: BulkSolutionActionDto,
  ): Promise<{ id: string; success: boolean; error?: string }[]> {
    return apiPost<{ id: string; success: boolean; error?: string }[]>(
      '/admin/solutions/bulk',
      data,
    )
  },
}
