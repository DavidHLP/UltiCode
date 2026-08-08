import type { PageResult } from '@/shared/domain-types/src'
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export enum ContestType {
  ICPC = 'ICPC',
  IOI = 'IOI',
  CUSTOM = 'CUSTOM',
}

export enum ContestStatus {
  DRAFT = 'DRAFT',
  UPCOMING = 'UPCOMING',
  RUNNING = 'RUNNING',
  FINISHED = 'FINISHED',
  CANCELLED = 'CANCELLED',
}

export interface Contest {
  id: string
  slug: string
  title: string
  description?: string
  contestType: ContestType
  startTime: string
  endTime?: string
  duration: number
  status: ContestStatus
  isVisible: boolean
  isPremium: boolean
  isPublished: boolean
  participantCount: number
  problemCount: number
  maxParticipants?: number
  currentParticipants?: number
  registeredCount?: number
  scoringRuleId?: string
  problemIds?: number[]
  tags?: string[]
  createdAt?: string
  updatedAt?: string
  createdById?: number
  createdByUsername?: string
  isParticipating?: boolean
  userRanking?: number
  userScore?: number
  isRated?: boolean
  scoringMode?: string
  penaltyPerWrong?: number
  coverImage?: string
}

export interface ContestRanking {
  rank: number
  userId: string
  username: string
  name: string | null
  avatar?: string
  score: number
  penalty: number
  problemsSolved: number
  timeBonus?: number
  country?: string
  isCurrentUser?: boolean
}

export interface ContestProblem {
  id: string
  contestId: string
  problemId: number
  problemIndex: string
  score: number
}

export interface ContestQueryParams {
  search?: string
  contestType?: ContestType
  status?: string
  page?: number
  pageSize?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

export interface CreateContestDto {
  title: string
  /**
   * URL-friendly identifier. Validated by the backend
   * (lowercase letters, digits, hyphens) and persisted when provided; falls
   * back to `generateSlug(title)` only when the field is absent or blank.
   */
  slug?: string
  description?: string
  contestType?: ContestType
  startTime: string
  duration: number
  maxParticipants?: number
  isPremium?: boolean
  isPublished?: boolean
  problemIds?: number[]
  /**
   * Scored problem attachments persisted atomically with the contest.
   * Preferred over `problemIds`; when present the backend bulk-inserts each
   * problem with the author's score in the create transaction.
   */
  problems?: ContestProblemAttachment[]
  tags?: string[]
  scoringRuleId?: string
}

export interface UpdateContestDto {
  title?: string
  description?: string
  contestType?: ContestType
  startTime?: string
  duration?: number
  maxParticipants?: number
  isPremium?: boolean
  isPublished?: boolean
  problemIds?: number[]
  /**
   * Scored problem attachments that replace the contest's problem set
   * atomically with the update. Preferred over `problemIds`.
   */
  problems?: ContestProblemAttachment[]
  tags?: string[]
  scoringRuleId?: string
}

export interface AddContestProblemDto {
  problemId: number
  score?: number
}

/**
 * Scored problem attachment used by the atomic contest create/update
 * payloads. One `{ problemId, score }` per drafted problem; the backend
 * persists the contest row and every scored ContestProblem in a single
 * transaction.
 */
export interface ContestProblemAttachment {
  problemId: number
  score?: number
}

export const contestsApi = {
  async getContests(params: ContestQueryParams): Promise<PageResult<Contest>> {
    return apiGet<PageResult<Contest>>('/admin/contest', { params })
  },

  async getContest(id: string): Promise<Contest> {
    return apiGet<Contest>(`/admin/contest/${id}`)
  },

  async createContest(data: CreateContestDto): Promise<Contest> {
    return apiPost<Contest>('/admin/contest', data)
  },

  async updateContest(id: string, data: UpdateContestDto): Promise<Contest> {
    return apiPatch<Contest>(`/admin/contest/${id}`, data)
  },

  async deleteContest(id: string): Promise<void> {
    await apiDelete(`/admin/contest/${id}`)
  },

  async addProblem(id: string, data: AddContestProblemDto): Promise<ContestProblem> {
    return apiPost<ContestProblem>(`/admin/contest/${id}/problems`, data)
  },

  async removeProblem(id: string, problemId: number): Promise<void> {
    await apiDelete(`/admin/contest/${id}/problems/${problemId}`)
  },

  async getRankings(id: string, page = 1, limit = 50): Promise<PageResult<ContestRanking>> {
    return apiGet<PageResult<ContestRanking>>(`/admin/contest/${id}/rankings`, {
      params: { page, limit },
    })
  },

  async startContest(id: string): Promise<Contest> {
    return apiPost<Contest>(`/admin/contest/${id}/start`)
  },

  async endContest(id: string): Promise<Contest> {
    return apiPost<Contest>(`/admin/contest/${id}/end`)
  },
}
