import { apiGet, apiPost, apiPut, apiDelete } from '@/utils/request'

export interface ScoringRule {
  id: string
  name: string
  description?: string
  base_score_per_problem: number
  time_bonus_per_minute: number
  wrong_answer_penalty: number
  time_limit_penalty: number
  first_solve_bonus: number
  full_score_bonus: number
  is_default: boolean
  is_active: boolean
  created_at: string
  updated_at: string
}

export interface CreateScoringRuleDto {
  name: string
  description?: string
  base_score_per_problem: number
  time_bonus_per_minute: number
  wrong_answer_penalty: number
  time_limit_penalty?: number
  first_solve_bonus: number
  full_score_bonus?: number
  is_default?: boolean
}

export interface UpdateScoringRuleDto {
  name?: string
  description?: string
  base_score_per_problem?: number
  time_bonus_per_minute?: number
  wrong_answer_penalty?: number
  time_limit_penalty?: number
  first_solve_bonus?: number
  full_score_bonus?: number
  is_default?: boolean
}

export const scoringRulesApi = {
  async getAll(includeInactive = false): Promise<ScoringRule[]> {
    const response = await apiGet<ScoringRule[]>('/admin/scoring-rules', {
      params: { includeInactive: String(includeInactive) },
    })
    return response
  },

  async getById(id: string): Promise<ScoringRule> {
    const response = await apiGet<ScoringRule>(`/admin/scoring-rules/${id}`)
    return response
  },

  async create(dto: CreateScoringRuleDto): Promise<ScoringRule> {
    const response = await apiPost<ScoringRule>('/admin/scoring-rules', dto)
    return response
  },

  async update(id: string, dto: UpdateScoringRuleDto): Promise<ScoringRule> {
    const response = await apiPut<ScoringRule>(`/admin/scoring-rules/${id}`, dto)
    return response
  },

  async delete(id: string): Promise<void> {
    await apiDelete(`/admin/scoring-rules/${id}`)
  },
}
