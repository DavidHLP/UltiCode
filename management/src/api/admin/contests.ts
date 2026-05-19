import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export type ContestFormat = 'ICPC' | 'IOI' | 'CUSTOM'

export type ContestStatus = 'DRAFT' | 'UPCOMING' | 'RUNNING' | 'FINISHED' | 'CANCELLED'

export interface Contest {
  id: string
  slug: string
  title: string
  description?: string
  contestType: ContestFormat
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
  scoringRuleId?: string
  problemIds?: string[]
  tags?: string[]
  createdAt: string
  updatedAt: string
  createdById?: number
  createdByUsername?: string
  isParticipating?: boolean
  userRanking?: number
  userScore?: number
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
  contestType?: ContestFormat
  status?: string
  page?: number
  pageSize?: number
  sortBy?: string
  direction?: 'asc' | 'desc'
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface CreateContestDto {
  slug?: string
  title: string
  description?: string
  contestType?: ContestFormat
  startTime: string
  duration: number
  maxParticipants?: number
  isPremium?: boolean
  isPublished?: boolean
  problemIds?: number[]
  tags?: string[]
  scoringRuleId?: string
}

export interface UpdateContestDto {
  slug?: string
  title?: string
  description?: string
  contestType?: ContestFormat
  startTime?: string
  duration?: number
  maxParticipants?: number
  isPremium?: boolean
  isPublished?: boolean
  problemIds?: number[]
  tags?: string[]
  scoringRuleId?: string
}

export interface AddContestProblemDto {
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
