import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export enum ContestType {
  PUBLIC = 'PUBLIC',
  PRIVATE = 'PRIVATE',
  VIRTUAL = 'VIRTUAL',
}

export enum ContestStatus {
  UPCOMING = 'UPCOMING',
  RUNNING = 'RUNNING',
  FINISHED = 'FINISHED',
}

export interface ContestParticipant {
  id: string
  contestId: string
  userId: string
  score: number
  rank?: number
  user: {
    id: string
    username: string
    name: string | null
  }
}

export interface ContestProblem {
  id: string
  contestId: string
  problemId: string
  problemIndex: string
  score: number
  problem: {
    id: string
    slug: string
    title: string
    difficulty: string
  }
}

export interface ContestRanking {
  id: string
  contestId: string
  userId: string
  totalScore: number
  totalPenalty: number
  rank: number
  user: {
    id: string
    username: string
    name: string | null
  }
}

export interface Contest {
  id: string
  slug: string
  title: string
  description?: string
  contestType: ContestType
  startTime: string
  endTime?: string
  durationMinutes: number
  status: ContestStatus
  isVisible: boolean
  participantCount?: number
  problemCount?: number
  createdAt: string
  updatedAt: string
  participants?: ContestParticipant[]
  problems?: ContestProblem[]
}

export interface ContestQueryParams {
  search?: string
  type?: ContestType
  status?: string // 'upcoming' | 'running' | 'finished'
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface CreateContestDto {
  slug: string
  title: string
  description?: string
  type: ContestType
  startTime: string
  duration: number
  isPublished?: boolean
  problemIds?: string[]
  scoringRuleId?: string
}

export interface UpdateContestDto {
  slug?: string
  title?: string
  description?: string
  type?: ContestType
  startTime?: string
  duration?: number
  isPublished?: boolean
}

export interface AddContestProblemDto {
  problemId: string
  score?: number
}

export const contestsApi = {
  async getContests(params: ContestQueryParams): Promise<PageResult<Contest>> {
    const response = await apiGet<PageResult<Contest>>('/admin/contests', { params })
    return response
  },

  async getContest(id: string): Promise<Contest> {
    const response = await apiGet<Contest>(`/admin/contests/${id}`)
    return response
  },

  async createContest(data: CreateContestDto): Promise<Contest> {
    const response = await apiPost<Contest>('/admin/contests', data)
    return response
  },

  async updateContest(id: string, data: UpdateContestDto): Promise<Contest> {
    const response = await apiPatch<Contest>(`/admin/contests/${id}`, data)
    return response
  },

  async deleteContest(id: string): Promise<void> {
    await apiDelete(`/admin/contests/${id}`)
  },

  async addProblem(id: string, data: AddContestProblemDto): Promise<ContestProblem> {
    const response = await apiPost<ContestProblem>(`/admin/contests/${id}/problems`, data)
    return response
  },

  async removeProblem(id: string, problemId: string): Promise<void> {
    await apiDelete(`/admin/contests/${id}/problems/${problemId}`)
  },

  async getRankings(id: string): Promise<{ data: ContestRanking[] }> {
    const response = await apiGet<{ data: ContestRanking[] }>(`/admin/contests/${id}/rankings`)
    return response
  },

  async startContest(id: string): Promise<Contest> {
    const response = await apiPost<Contest>(`/admin/contests/${id}/start`)
    return response
  },

  async endContest(id: string): Promise<Contest> {
    const response = await apiPost<Contest>(`/admin/contests/${id}/end`)
    return response
  },
}
