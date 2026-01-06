import apiClient from '../client'

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
  contest_id: string
  user_id: string
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
  contest_id: string
  problem_id: string
  problem_index: string
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
  contest_id: string
  user_id: string
  total_score: number
  total_penalty: number
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
  contest_type: ContestType
  start_time: string
  end_time?: string
  duration_minutes: number
  status: ContestStatus
  is_visible: boolean
  participant_count?: number
  problem_count?: number
  created_at: string
  updated_at: string
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

export interface ContestsResponse {
  data: Contest[]
  total: number
  page: number
  limit: number
  totalPages: number
}

export interface CreateContestDto {
  slug: string
  title: string
  description?: string
  type: ContestType
  start_time: string
  duration: number
  is_published?: boolean
  problem_ids?: string[]
}

export interface UpdateContestDto {
  slug?: string
  title?: string
  description?: string
  type?: ContestType
  start_time?: string
  duration?: number
  is_published?: boolean
}

export interface AddContestProblemDto {
  problem_id: string
  score?: number
}

export const contestsApi = {
  async getContests(params: ContestQueryParams): Promise<ContestsResponse> {
    const response = await apiClient.get<ContestsResponse>('/admin/contests', { params })
    return response.data
  },

  async getContest(id: string): Promise<Contest> {
    const response = await apiClient.get<Contest>(`/admin/contests/${id}`)
    return response.data
  },

  async createContest(data: CreateContestDto): Promise<Contest> {
    const response = await apiClient.post<Contest>('/admin/contests', data)
    return response.data
  },

  async updateContest(id: string, data: UpdateContestDto): Promise<Contest> {
    const response = await apiClient.patch<Contest>(`/admin/contests/${id}`, data)
    return response.data
  },

  async deleteContest(id: string): Promise<void> {
    await apiClient.delete(`/admin/contests/${id}`)
  },

  async addProblem(id: string, data: AddContestProblemDto): Promise<ContestProblem> {
    const response = await apiClient.post<ContestProblem>(`/admin/contests/${id}/problems`, data)
    return response.data
  },

  async removeProblem(id: string, problemId: string): Promise<void> {
    await apiClient.delete(`/admin/contests/${id}/problems/${problemId}`)
  },

  async getRankings(id: string): Promise<{ data: ContestRanking[] }> {
    const response = await apiClient.get<{ data: ContestRanking[] }>(
      `/admin/contests/${id}/rankings`,
    )
    return response.data
  },

  async startContest(id: string): Promise<Contest> {
    const response = await apiClient.post<Contest>(`/admin/contests/${id}/start`)
    return response.data
  },

  async endContest(id: string): Promise<Contest> {
    const response = await apiClient.post<Contest>(`/admin/contests/${id}/end`)
    return response.data
  },
}
