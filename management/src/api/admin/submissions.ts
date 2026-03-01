import { apiGet, apiPost } from '@/utils/request'

export interface SubmissionQueryParams {
  page?: number
  limit?: number
  userId?: string
  problemId?: number
  status?: string
  language?: string
  startDate?: string
  endDate?: string
  search?: string
  sortBy?: 'created_at' | 'runtime' | 'memory' | 'status'
  sortOrder?: 'asc' | 'desc'
}

export interface SubmissionListItem {
  id: string
  problemId: number
  problemTitle: string
  problemSlug: string
  userId: string
  username: string
  language: string
  status: string
  runtime: number
  memory: number
  createdAt: string
  codeLength: number
}

export interface SubmissionDetail extends SubmissionListItem {
  code: string
  notes: string | null
  runtimePercentile: number | null
  memoryPercentile: number | null
  testDetails: unknown
  memoryDistBinsMb: unknown
  runtimeDistBinsMs: unknown
}

export interface SubmissionListResponse {
  data: SubmissionListItem[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface RejudgeResult {
  submissionId: string
  success: boolean
  oldStatus: string
  newStatus?: string
  error?: string
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
  key: string
  label: string
  category: string
}

export const submissionsApi = {
  async getList(params: SubmissionQueryParams): Promise<SubmissionListResponse> {
    return apiGet<SubmissionListResponse>('/admin/submissions', { params })
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

  async getLanguages(): Promise<string[]> {
    return apiGet<string[]>('/admin/submissions/languages')
  },

  async rejudge(id: string, notifyUser: boolean = false): Promise<RejudgeResult> {
    return apiPost<RejudgeResult>(`/admin/submissions/${id}/rejudge`, {
      notifyUser,
    })
  },

  async batchRejudge(ids: string[], notifyUsers: boolean = false): Promise<BatchRejudgeResponse> {
    return apiPost<BatchRejudgeResponse>('/admin/submissions/batch-rejudge', {
      ids,
      notifyUsers,
    })
  },
}
