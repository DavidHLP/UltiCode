import apiClient from '../client'

export enum Difficulty {
  EASY = 'EASY',
  MEDIUM = 'MEDIUM',
  HARD = 'HARD',
}

export enum ProblemStatus {
  SOLVED = 'solved',
  ATTEMPTED = 'attempted',
  TODO = 'todo',
}

export interface ProblemTag {
  id: string
  label: string
  category: string
}

export interface ProblemDetail {
  id: string
  summary: string
  content?: string
  difficulty_rating: number
  likes: number
  dislikes: number
  constraints_json?: string[]
  hints?: string[]
}

export interface Problem {
  id: string
  slug: string
  title: string
  difficulty: Difficulty
  status: ProblemStatus
  is_premium: boolean
  has_solution: boolean
  is_published: boolean
  published_at?: Date
  published_by?: string
  is_deleted: boolean
  deleted_at?: Date
  created_at: Date
  updated_at: Date
  detail?: ProblemDetail
  tags: ProblemTag[]
  submission_count?: number
  solution_count?: number
  examples?: ProblemExample[]
  languages?: ProblemLanguage[]
}

export interface ProblemExample {
  id: string
  input: string
  output: string
  explanation?: string
  order: number
}

export interface ProblemLanguage {
  id: string
  language: string
  value: string
  style?: string
  starter_code: string
}

export interface ProblemQueryParams {
  search?: string
  difficulty?: Difficulty
  status?: ProblemStatus
  is_published?: boolean
  is_deleted?: boolean
  tag?: string
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface ProblemsResponse {
  data: Problem[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface CreateProblemDto {
  slug: string
  title: string
  difficulty: Difficulty
  status?: ProblemStatus
  is_premium?: boolean
  is_published?: boolean
  published_by?: string
  summary?: string
  content?: string
  examples?: ProblemExample[]
  constraints?: string[]
  hints?: string[]
  languages?: string[]
  tags?: string[]
}

export interface UpdateProblemDto {
  slug?: string
  title?: string
  difficulty?: Difficulty
  status?: ProblemStatus
  is_premium?: boolean
  has_solution?: boolean
  summary?: string
  content?: string
  examples?: ProblemExample[]
  constraints?: string[]
  hints?: string[]
  languages?: string[]
  tags?: string[]
}

export interface BulkProblemActionDto {
  ids: string[]
  action: 'publish' | 'unpublish' | 'delete' | 'restore'
}

export const problemsApi = {
  async getProblems(params: ProblemQueryParams): Promise<ProblemsResponse> {
    const response = await apiClient.get<ProblemsResponse>('/admin/problems', { params })
    return response.data
  },

  async getProblem(id: string): Promise<Problem> {
    const response = await apiClient.get<Problem>(`/admin/problems/${id}`)
    return response.data
  },

  async createProblem(data: CreateProblemDto): Promise<Problem> {
    const response = await apiClient.post<Problem>('/admin/problems', data)
    return response.data
  },

  async updateProblem(id: string, data: UpdateProblemDto): Promise<Problem> {
    const response = await apiClient.patch<Problem>(`/admin/problems/${id}`, data)
    return response.data
  },

  async deleteProblem(id: string): Promise<void> {
    await apiClient.delete(`/admin/problems/${id}`)
  },

  async publishProblem(id: string): Promise<Problem> {
    const response = await apiClient.post<Problem>(`/admin/problems/${id}/publish`)
    return response.data
  },

  async unpublishProblem(id: string): Promise<Problem> {
    const response = await apiClient.post<Problem>(`/admin/problems/${id}/unpublish`)
    return response.data
  },

  async getProblemSubmissions(
    id: string,
    params: { page?: number; limit?: number } = {},
  ): Promise<unknown> {
    const response = await apiClient.get(`/admin/problems/${id}/submissions`, { params })
    return response.data
  },

  async bulkAction(data: BulkProblemActionDto): Promise<void> {
    await apiClient.post('/admin/problems/bulk', data)
  },
}
