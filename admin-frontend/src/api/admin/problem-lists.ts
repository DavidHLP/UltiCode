import apiClient from '../client'

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
    const response = await apiClient.get<ProblemListResponse>('/admin/problem-lists', { params: query })
    return response.data
  },

  async getList(id: string): Promise<ProblemListDetail> {
    const response = await apiClient.get<ProblemListDetail>(`/admin/problem-lists/${id}`)
    return response.data
  },

  async createList(data: CreateProblemListDto): Promise<ProblemList> {
    const response = await apiClient.post<ProblemList>('/admin/problem-lists', data)
    return response.data
  },

  async updateList(id: string, data: UpdateProblemListDto): Promise<ProblemList> {
    const response = await apiClient.patch<ProblemList>(`/admin/problem-lists/${id}`, data)
    return response.data
  },

  async deleteList(id: string): Promise<void> {
    await apiClient.delete(`/admin/problem-lists/${id}`)
  },

  async updateListProblems(id: string, data: UpdateProblemListProblemsDto): Promise<void> {
    await apiClient.post(`/admin/problem-lists/${id}/problems`, data)
  },
}
