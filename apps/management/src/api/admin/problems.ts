import {
  normalizeAdminProblem,
  type PageResult,
  type ProblemAdmin,
  type ProblemTag as DomainProblemTag,
} from '@ulticode/domain-types'
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

export type ProblemTag = DomainProblemTag

export interface ProblemDetail {
  id: string
  summary: string
  content?: string
  difficultyRating: number
  likes: number
  dislikes: number
  constraintsJson?: string[]
  hints?: string[]
}

export type Problem = ProblemAdmin

export interface ProblemExample {
  id: string
  input: string
  output: string
  explanation?: string
  inputs?: ProblemExampleParam[]
  order: number
}

export interface ProblemExampleParam {
  name: string
  value?: unknown
  label?: string
  fieldName?: string
}

export interface ProblemLanguage {
  id: string
  language: string
  value: string
  style?: string
  starterCode: string
}

// ========== Tab-specific Types ==========

export interface HeaderData {
  id: string
  title: string
  slug: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  status: ProblemStatus
  isPremium: boolean
  isPublished: boolean
  publishedAt?: Date
}

export interface DescriptionData {
  id: string
  title: string
  slug: string
  difficulty: string
  status: string
  isPremium: boolean
  isPublished: boolean
  detail?: {
    summary?: string
    content?: string
    constraintsJson?: string[]
    hints?: string[]
  }
  tags: Array<{ id: string; label: string }>
  examples?: ProblemExample[]
  createdAt: Date
  updatedAt: Date
  publishedAt?: Date
}

export interface CodeData {
  id: string
  languages?: Array<{
    id: string
    language: string
    value: string
    style?: string
    starterCode: string
  }>
}

export interface CasesData {
  id: string
  examples?: Array<{
    id: string
    input: string
    output: string
    explanation?: string
    inputs?: ProblemExampleParam[]
    order: number
  }>
  detail?: {
    constraintsJson?: string[]
    hints?: string[]
  }
  tags?: Array<{ id: string; label: string }>
}

export interface ProblemQueryParams {
  search?: string
  difficulty?: Difficulty
  status?: Problem['status']
  publishStatus?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  isPublished?: boolean
  isDeleted?: boolean
  tag?: string
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// Backend PageResult structure (from Result<PageResult>)

// ========== API Transport DTOs (match backend Java DTOs exactly) ==========

export interface CreateProblemDto {
  slug: string
  title: string
  difficulty: Difficulty
  status?: ProblemStatus
  isPremium?: boolean
  isPublished?: boolean
  publishedBy?: string
  summary?: string
  content?: string
  examples?: string // JSON string — backend expects String
  constraints?: string // JSON string — backend expects String
  hints?: string // JSON string — backend expects String
  languages?: string[]
  tags?: string[]
}

export interface LanguageConfig {
  language: string
  starterCode: string
}

export interface UpdateProblemDto {
  slug?: string
  title?: string
  difficulty?: Difficulty
  status?: ProblemStatus
  isPremium?: boolean
  hasSolution?: boolean
  summary?: string
  content?: string
  constraintsJson?: string // JSON string — backend expects String
  hints?: string // JSON string — backend expects String
  examples?: string // JSON string — backend expects String
  tags?: string[]
  languages?: LanguageConfig[]
}

// ========== Component-layer Input Types (structured data) ==========

export interface ProblemExampleInput {
  id?: string
  input: string
  output: string
  explanation?: string
  inputs?: ProblemExampleParam[]
  order?: number
}

export interface ProblemCreateInput {
  slug: string
  title: string
  difficulty: Difficulty
  status?: ProblemStatus
  isPremium?: boolean
  isPublished?: boolean
  publishedBy?: string
  summary?: string
  content?: string
  examples?: ProblemExampleInput[] // structured array
  constraints?: string[] // string array
  hints?: string[] // string array
  languages?: string[]
  tags?: string[]
}

export interface ProblemUpdateInput {
  slug?: string
  title?: string
  difficulty?: Difficulty
  status?: ProblemStatus
  isPremium?: boolean
  hasSolution?: boolean
  summary?: string
  content?: string
  constraintsJson?: string[] // string array (component layer)
  hints?: string[] // string array (component layer)
  examples?: ProblemExampleInput[] // structured array (component layer)
  tags?: string[]
  languages?: LanguageConfig[]
}

// ========== Serialization Helpers ==========

export function serializeCreateInput(input: ProblemCreateInput): CreateProblemDto {
  return {
    ...input,
    examples: input.examples
      ? JSON.stringify(input.examples.map((ex, idx) => ({ ...ex, order: ex.order ?? idx })))
      : undefined,
    constraints: input.constraints ? JSON.stringify(input.constraints) : undefined,
    hints: input.hints ? JSON.stringify(input.hints) : undefined,
  }
}

export function serializeUpdateInput(input: ProblemUpdateInput): UpdateProblemDto {
  return {
    ...input,
    examples: input.examples
      ? JSON.stringify(input.examples.map((ex, idx) => ({ ...ex, order: ex.order ?? idx })))
      : undefined,
    constraintsJson: input.constraintsJson ? JSON.stringify(input.constraintsJson) : undefined,
    hints: input.hints ? JSON.stringify(input.hints) : undefined,
  }
}

export interface BulkProblemActionDto {
  ids: string[]
  action: 'publish' | 'unpublish' | 'delete' | 'restore'
}

export interface BulkEditProblemDto {
  ids: string[]
  difficulty?: Difficulty
  isPremium?: boolean
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
    inputs?: ProblemExampleParam[]
    order?: number
  }> | null
  languages: Array<{
    label: string
    value: string
    starterCode: string
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
  isPremium?: boolean
  hasSolution?: boolean
  isPublished?: boolean
  summary?: string
  examples?: Array<{
    input: string
    output: string
    explanation?: string
    inputs?: ProblemExampleParam[]
  }>
  constraints?: string[]
  hints?: string[]
  languages?: Array<{
    label: string
    value: string
    starterCode: string
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
  async getProblems(params: ProblemQueryParams): Promise<PageResult<Problem>> {
    // apiGet already unwraps response.data automatically
    const response = await apiGet<PageResult<unknown>>('/admin/problems', { params })
    return {
      ...response,
      items: response.items.map(normalizeAdminProblem),
    }
  },

  async getProblem(id: string): Promise<Problem> {
    const response = await apiGet<unknown>(`/admin/problems/${id}`)
    return normalizeAdminProblem(response)
  },

  async createProblem(input: ProblemCreateInput): Promise<Problem> {
    const data = serializeCreateInput(input)
    const response = await apiPost<unknown>('/admin/problems', data)
    return normalizeAdminProblem(response)
  },

  async updateProblem(id: string, input: ProblemUpdateInput): Promise<Problem> {
    const data = serializeUpdateInput(input)
    const response = await apiPatch<unknown>(`/admin/problems/${id}`, data)
    return normalizeAdminProblem(response)
  },

  async deleteProblem(id: string): Promise<void> {
    await apiDelete(`/admin/problems/${id}`)
  },

  async publishProblem(id: string): Promise<Problem> {
    const response = await apiPost<unknown>(`/admin/problems/${id}/publish`)
    return normalizeAdminProblem(response)
  },

  async unpublishProblem(id: string): Promise<Problem> {
    const response = await apiPost<unknown>(`/admin/problems/${id}/unpublish`)
    return normalizeAdminProblem(response)
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
    }>('/admin/problems/bulk', data)
    return response
  },

  async bulkEdit(data: BulkEditProblemDto): Promise<{
    results: Array<{ id: string; success: boolean; error?: string }>
  }> {
    const response = await apiPost<{
      results: Array<{ id: string; success: boolean; error?: string }>
    }>('/admin/problems/bulk', { ...data, action: 'edit' })
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

  async createInitialVersion(id: string): Promise<{ success: boolean; message: string }> {
    const response = await apiPost<{ success: boolean; message: string }>(
      `/admin/problems/${id}/versions/create-initial`,
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
    const response = await apiPost<unknown>(`/admin/problems/${id}/flag`, { reason })
    return normalizeAdminProblem(response)
  },

  async moderateProblem(
    id: string,
    data: {
      status: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
      notes?: string
    },
  ): Promise<Problem> {
    const response = await apiPost<unknown>(`/admin/problems/${id}/moderate`, data)
    return normalizeAdminProblem(response)
  },

  async getFlaggedProblems(params: {
    page?: number
    limit?: number
    status?: 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'
  }): Promise<PageResult<Problem>> {
    // apiGet already unwraps response.data automatically
    const response = await apiGet<PageResult<unknown>>('/admin/problems/flagged', { params })
    return {
      ...response,
      items: response.items.map(normalizeAdminProblem),
    }
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

  // ========== Tab-specific APIs ==========

  async getHeader(id: string, signal?: AbortSignal): Promise<HeaderData> {
    return apiGet<HeaderData>(`/admin/problems/${id}/header`, { signal })
  },

  async getDescription(id: string, signal?: AbortSignal): Promise<DescriptionData> {
    return apiGet<DescriptionData>(`/admin/problems/${id}/description`, { signal })
  },

  async getCode(id: string, signal?: AbortSignal): Promise<CodeData> {
    return apiGet<CodeData>(`/admin/problems/${id}/code`, { signal })
  },

  async getCases(id: string, signal?: AbortSignal): Promise<CasesData> {
    return apiGet<CasesData>(`/admin/problems/${id}/cases`, { signal })
  },
}
