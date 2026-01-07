import { apiGet, apiPost, apiDelete } from '../client'

export interface Solution {
  id: string
  problem_id: string
  user_id: string
  title: string
  content: string
  summary?: string
  language: string
  tags?: string[]
  views: number
  is_published: boolean
  published_at?: Date
  published_by?: string
  is_flagged: boolean
  flagged_reason?: string
  flagged_at?: Date
  is_deleted: boolean
  deleted_at?: Date
  deleted_by?: string
  created_at: Date
  updated_at: Date
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
  comment_count?: number
  comments?: SolutionComment[]
}

export interface SolutionComment {
  id: string
  content: string
  created_at: Date
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
  is_flagged?: boolean
  is_published?: boolean
  is_deleted?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface SolutionsResponse {
  data: Solution[]
  total: number
  page: number
  limit: number
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
  async getSolutions(params: SolutionQueryParams): Promise<SolutionsResponse> {
    const response = await apiGet<SolutionsResponse>('/admin/solutions', { params })
    return response
  },

  async getFlaggedSolutions(params: SolutionQueryParams): Promise<SolutionsResponse> {
    const response = await apiGet<SolutionsResponse>('/admin/solutions/flagged', { params })
    return response
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
