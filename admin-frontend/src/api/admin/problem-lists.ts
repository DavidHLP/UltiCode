import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export interface ProblemList {
  id: string
  name: string
  description: string
  author_id: string
  is_public: boolean
  is_featured: boolean
  banner_tag?: string
  banner_icon?: string
  banner_theme?: string
  banner_order: number
  created_at: string
  updated_at: string
  problem_count?: number
}

export interface ProblemListProblem {
  id: number
  slug: string
  title: string
  difficulty: string
  status: string
  sort_order: number
  added_at: string
}

export interface ProblemListDetail extends ProblemList {
  problems: ProblemListProblem[]
}

export interface ProblemListQuery {
  search?: string
  is_featured?: boolean
  is_public?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface CreateProblemListDto {
  name: string
  description?: string
  slug?: string
  is_public?: boolean
  is_featured?: boolean
  banner_tag?: string
  banner_icon?: string
  banner_theme?: string
  banner_order?: number
  author_id?: string
}

export interface UpdateProblemListDto {
  name?: string
  description?: string
  slug?: string
  is_public?: boolean
  is_featured?: boolean
  banner_tag?: string
  banner_icon?: string
  banner_theme?: string
  banner_order?: number
}

export interface UpdateProblemListProblemsDto {
  problems: {
    problem_id: number
    sort_order: number
  }[]
}

export interface ProblemListResponse {
  data: ProblemList[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export const adminProblemListsApi = {
  async getLists(query: ProblemListQuery): Promise<ProblemListResponse> {
    const response = await apiGet<ProblemListResponse>('/admin/problem-lists', {
      params: query,
    })
    return response
  },

  async getList(id: string): Promise<ProblemListDetail> {
    const response = await apiGet<ProblemListDetail>(`/admin/problem-lists/${id}`)
    return response
  },

  async createList(data: CreateProblemListDto): Promise<ProblemList> {
    const response = await apiPost<ProblemList>('/admin/problem-lists', data)
    return response
  },

  async updateList(id: string, data: UpdateProblemListDto): Promise<ProblemList> {
    const response = await apiPatch<ProblemList>(`/admin/problem-lists/${id}`, data)
    return response
  },

  async deleteList(id: string): Promise<void> {
    await apiDelete(`/admin/problem-lists/${id}`)
  },

  async updateListProblems(id: string, data: UpdateProblemListProblemsDto): Promise<void> {
    await apiPost(`/admin/problem-lists/${id}/problems`, data)
  },
}
