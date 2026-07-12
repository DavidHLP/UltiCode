import type { PageResult } from '@/shared/domain-types/src'
import { apiGet, apiPost } from '@/utils/request'

export interface SubmissionQueryParams {
  page?: number
  limit?: number
  userId?: string
  problemId?: number
  status?: string
  language?: string
  /** @format ISO-8601 (YYYY-MM-DDTHH:mm:ss) */
  startDate?: string
  /** @format ISO-8601 (YYYY-MM-DDTHH:mm:ss) */
  endDate?: string
  search?: string
  sortBy?: 'createdAt' | 'runtime' | 'memory' | 'status'
  sortOrder?: 'asc' | 'desc'
}

/**
 * Submission list item from AdminSubmissionVO
 * Backend uses camelCase (Jackson serialization)
 */
export interface SubmissionListItem {
  id: string
  problemId: number
  problemTitle: string
  problemSlug: string
  userId: string
  username: string
  language: string
  status: string
  /** Runtime in milliseconds, null if not judged yet */
  runtime: number | null
  /** Memory in MB, null if not judged yet */
  memory: number | null
  createdAt: string
  /** Code length in bytes, can be null */
  codeLength: number | null
}

/**
 * Detailed submission information
 * Extends SubmissionListItem with additional fields
 */
export interface SubmissionDetail extends SubmissionListItem {
  /** Source code, can be null */
  code: string | null
  notes: string | null
  runtimePercentile: number | null
  memoryPercentile: number | null
  testDetails: unknown
  memoryDistBinsMb: unknown
  runtimeDistBinsMs: unknown
}

export interface RejudgeResult {
  submissionId: string
  success: boolean
  oldStatus: string
  newStatus?: string
  error?: string
  /** ISO-8601 UTC timestamp; present on success. */
  rejudgedAt?: string
  /** Retry count after this rejudge; present on success. */
  retryCount?: number
}

export interface BatchRejudgeResponse {
  results: RejudgeResult[]
  total: number
  successful: number
  failed: number
}

export interface SubmissionStatistics {
  total: number
  byStatus: Array<{ status: string; count: number }>
  byLanguage: Array<{ language: string; count: number }>
  last24h: number
  pending: number
}

export interface StatusOption {
  /** DB-stored status key (e.g. "Compile Error"). Use this for filter queries. */
  key: string
  /** Display label, usually equal to {@link key}. */
  label: string
  /** Coarse filter category (pending, accepted, error, system). */
  category: string
  /** Stable enum-style code (e.g. "COMPILE_ERROR"). Use this for i18n lookups. */
  code: string
}

export interface LanguageOption {
  /** DB-stored language code (e.g. "cpp"). */
  key: string
  /** Humanised display label (e.g. "C++"). */
  label: string
}

export const submissionsApi = {
  async getList(params: SubmissionQueryParams): Promise<PageResult<SubmissionListItem>> {
    return apiGet<PageResult<SubmissionListItem>>('/admin/submissions', { params })
  },

  async getById(id: string): Promise<SubmissionDetail | null> {
    return apiGet<SubmissionDetail>(`/admin/submissions/${id}`)
  },

  async getStatistics(): Promise<SubmissionStatistics> {
    return apiGet<SubmissionStatistics>('/admin/submissions/statistics')
  },

  async getStatuses(): Promise<StatusOption[]> {
    return apiGet<StatusOption[]>('/admin/submissions/statuses')
  },

  async getLanguages(): Promise<LanguageOption[]> {
    return apiGet<LanguageOption[]>('/admin/submissions/languages')
  },

  async rejudge(id: string, notifyUser: boolean = false): Promise<RejudgeResult> {
    return apiPost<RejudgeResult>(`/admin/submissions/${id}/rejudge`, {
      notifyUser,
    })
  },

  async batchRejudge(
    submissionIds: string[],
    notifyUsers: boolean = false,
  ): Promise<BatchRejudgeResponse> {
    return apiPost<BatchRejudgeResponse>('/admin/submissions/batch-rejudge', {
      submissionIds,
      notifyUsers,
    })
  },
}
