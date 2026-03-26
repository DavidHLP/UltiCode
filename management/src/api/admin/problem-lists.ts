import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export interface ProblemList {
  id: string
  name: string
  description: string
  authorId: string
  isPublic: boolean
  isFeatured: boolean
  bannerTag?: string
  bannerIcon?: string
  bannerTheme?: string
  bannerOrder: number
  createdAt: string
  updatedAt: string
  problemCount?: number
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
  slug?: string
  isPublic?: boolean
  isFeatured?: boolean
  bannerTag?: string
  bannerIcon?: string
  bannerTheme?: string
  bannerOrder?: number
  authorId?: string
}

export interface UpdateProblemListDto {
  name?: string
  description?: string
  slug?: string
  isPublic?: boolean
  isFeatured?: boolean
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

// PageResult type for paginated responses (matches backend PageResult<T>)
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

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
