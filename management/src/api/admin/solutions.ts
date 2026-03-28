import { apiGet, apiPost, apiDelete } from '@/utils/request'

export interface Solution {
  id: string
  problemId: string
  userId: string
  title: string
  content: string
  summary?: string
  language: string
  tags?: string[]
  views: number
  isPublished: boolean
  publishedAt?: Date
  publishedBy?: string
  isFlagged: boolean
  flaggedReason?: string
  flaggedAt?: Date
  isDeleted: boolean
  deletedAt?: Date
  deletedBy?: string
  createdAt: Date
  updatedAt: Date
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
  commentCount?: number
  comments?: SolutionComment[]
}

export interface SolutionComment {
  id: string
  content: string
  createdAt: Date
  author: {
    id: string
    username: string
    name: string
  }
}

export interface SolutionQueryParams {
  search?: string
  problemId?: string
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
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface BulkSolutionActionDto {
  ids: string[]
  action: 'publish' | 'unpublish' | 'delete' | 'unflag'
}

export interface FlagSolutionDto {
  reason: string
}

export const solutionsApi = {
  async getSolutions(params: SolutionQueryParams): Promise<PageResult<Solution>> {
    return apiGet<PageResult<Solution>>('/admin/solutions', { params })
  },

  async getFlaggedSolutions(params: SolutionQueryParams): Promise<PageResult<Solution>> {
    return apiGet<PageResult<Solution>>('/admin/solutions/flagged', { params })
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
  ): Promise<{ results: { id: string; success: boolean; error?: string }[] }> {
    const response = await apiPost<{
      results: { id: string; success: boolean; error?: string }[]
    }>('/admin/solutions/bulk', data)
    return response
  },
}
