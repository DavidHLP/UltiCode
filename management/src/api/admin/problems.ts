import { apiGet, apiPost, apiPatch, apiDelete, apiDownload } from '@/utils/request'

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
  is_flagged?: boolean
  flag_reason?: string
  flag_reported_by?: string
  flag_reported_at?: Date
  flag_status?: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
  flag_reviewed_by?: string
  flag_reviewed_at?: Date
  flag_notes?: string
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

export interface BulkEditProblemDto {
  ids: string[]
  difficulty?: Difficulty
  is_premium?: boolean
}

export interface ProblemVersion {
  id: string
  versionNumber: number
  changeSummary: string | null
  changeType: string
  createdAt: Date
  createdBy: string | null
}

export interface ProblemVersionDetail {
  id: string
  versionNumber: number
  title: string
  slug: string
  difficulty: Difficulty
  isPremium: boolean
  isPublished: boolean
  summary: string | null
  content: string | null
  constraints: string[] | null
  hints: string[] | null
  examples: Array<{
    input: string
    output: string
    explanation?: string
    order?: number
  }> | null
  languages: Array<{
    label: string
    value: string
    starter_code: string
  }> | null
  tags: string[] | null
  changeSummary: string | null
  changeType: string
  createdAt: Date
  createdBy: string | null
}

export interface VersionDiff {
  field: string
  oldValue: unknown
  newValue: unknown
}

export interface VersionWithDiff {
  id: string
  versionNumber: number
  changeSummary: string | null
  changeType: string
  createdAt: Date
  createdBy: string | null
  diffs: VersionDiff[]
}

export interface VersionsResponse {
  versions: ProblemVersion[]
  pagination: {
    total: number
    page: number
    limit: number
    totalPages: number
  }
}

export interface ImportProblemDto {
  slug: string
  title: string
  difficulty: Difficulty
  status?: ProblemStatus
  is_premium?: boolean
  has_solution?: boolean
  is_published?: boolean
  summary?: string
  examples?: Array<{
    input: string
    output: string
    explanation?: string
  }>
  constraints?: string[]
  hints?: string[]
  languages?: Array<{
    label: string
    value: string
    starter_code: string
  }>
  tags?: string[]
}

export interface ImportProblemsResponse {
  total: number
  created: number
  updated: number
  skipped: number
  failed: number
  results: Array<{
    slug: string
    success: boolean
    error?: string
    action?: 'created' | 'updated' | 'skipped'
  }>
}

export const problemsApi = {
  async getProblems(params: ProblemQueryParams): Promise<ProblemsResponse> {
    const response = await apiGet<ProblemsResponse>('/admin/problems', { params })
    return response
  },

  async getProblem(id: string): Promise<Problem> {
    const response = await apiGet<Problem>(`/admin/problems/${id}`)
    return response
  },

  async createProblem(data: CreateProblemDto): Promise<Problem> {
    const response = await apiPost<Problem>('/admin/problems', data)
    return response
  },

  async updateProblem(id: string, data: UpdateProblemDto): Promise<Problem> {
    const response = await apiPatch<Problem>(`/admin/problems/${id}`, data)
    return response
  },

  async deleteProblem(id: string): Promise<void> {
    await apiDelete(`/admin/problems/${id}`)
  },

  async publishProblem(id: string): Promise<Problem> {
    const response = await apiPost<Problem>(`/admin/problems/${id}/publish`)
    return response
  },

  async unpublishProblem(id: string): Promise<Problem> {
    const response = await apiPost<Problem>(`/admin/problems/${id}/unpublish`)
    return response
  },

  async getProblemSubmissions(
    id: string,
    params: { page?: number; limit?: number } = {},
  ): Promise<unknown> {
    const response = await apiGet(`/admin/problems/${id}/submissions`, { params })
    return response
  },

  async bulkAction(data: BulkProblemActionDto): Promise<{
    results: Array<{ id: string; success: boolean; error?: string }>
  }> {
    const response = await apiPost<{
      results: Array<{ id: string; success: boolean; error?: string }>
    }>('/admin/bulk/problems/publish', data)
    return response
  },

  async bulkEdit(data: BulkEditProblemDto): Promise<{
    results: Array<{ id: string; success: boolean; error?: string }>
  }> {
    const response = await apiPost<{
      results: Array<{ id: string; success: boolean; error?: string }>
    }>('/admin/bulk/problems/edit', data)
    return response
  },

  async getProblemVersions(
    id: string,
    params: { page?: number; limit?: number } = {},
  ): Promise<VersionsResponse> {
    const response = await apiGet<VersionsResponse>(`/admin/problems/${id}/versions`, { params })
    return response
  },

  async getProblemVersion(id: string, versionId: string): Promise<ProblemVersionDetail> {
    const response = await apiGet<ProblemVersionDetail>(
      `/admin/problems/${id}/versions/${versionId}`,
    )
    return response
  },

  async getVersionDiff(
    id: string,
    fromVersionId: string,
    toVersionId: string,
  ): Promise<VersionWithDiff> {
    const response = await apiGet<VersionWithDiff>(
      `/admin/problems/${id}/versions/${fromVersionId}/diff/${toVersionId}`,
    )
    return response
  },

  async rollbackToVersion(
    id: string,
    versionId: string,
    reason?: string,
  ): Promise<{ success: boolean; message: string }> {
    const response = await apiPost<{ success: boolean; message: string }>(
      `/admin/problems/${id}/versions/${versionId}/rollback`,
      { reason },
    )
    return response
  },

  async exportProblems(params: ProblemQueryParams, format: 'json' | 'csv' = 'json'): Promise<void> {
    const date = new Date().toISOString().split('T')[0]
    const filename =
      format === 'json' ? `problems-export-${date}.json` : `problems-export-${date}.csv`
    await apiDownload(`/admin/problems/export?format=${format}`, filename, { params })
  },

  async importProblems(
    problems: ImportProblemDto[],
    onConflict: 'skip' | 'update' | 'create_new' = 'skip',
  ): Promise<ImportProblemsResponse> {
    const response = await apiPost<ImportProblemsResponse>('/admin/problems/import', {
      problems,
      onConflict,
    })
    return response
  },

  async flagProblem(id: string, reason: string): Promise<Problem> {
    const response = await apiPost<Problem>(`/admin/problems/${id}/flag`, { reason })
    return response
  },

  async moderateProblem(
    id: string,
    data: {
      status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
      notes?: string
    },
  ): Promise<Problem> {
    const response = await apiPost<Problem>(`/admin/problems/${id}/moderate`, data)
    return response
  },

  async getFlaggedProblems(params: {
    page?: number
    limit?: number
    status?: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
  }): Promise<ProblemsResponse> {
    const response = await apiGet<ProblemsResponse>('/admin/problems/flagged', { params })
    return response
  },

  async batchModerateProblems(data: {
    ids: string[]
    status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
    notes?: string
  }): Promise<{
    results: Array<{ id: string; success: boolean; error?: string }>
  }> {
    const response = await apiPost<{
      results: Array<{ id: string; success: boolean; error?: string }>
    }>('/admin/problems/flagged/batch-moderate', data)
    return response
  },
}
