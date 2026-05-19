import { apiGet, apiPost, apiPut, apiDelete } from '@/utils/request'

export interface ScoringRule {
  id: string
  name: string
  description?: string
  baseScorePerProblem: number
  timeBonusPerMinute: number
  wrongAnswerPenalty: number
  timeLimitPenalty?: number
  firstSolveBonus: number
  fullScoreBonus?: number
  isDefault: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
  contestCount?: number
}

export interface CreateScoringRuleDto {
  name: string
  description?: string
  baseScorePerProblem: number
  timeBonusPerMinute: number
  wrongAnswerPenalty: number
  timeLimitPenalty?: number
  firstSolveBonus: number
  fullScoreBonus?: number
  isDefault?: boolean
}

export interface UpdateScoringRuleDto {
  name?: string
  description?: string
  baseScorePerProblem?: number
  timeBonusPerMinute?: number
  wrongAnswerPenalty?: number
  timeLimitPenalty?: number
  firstSolveBonus?: number
  fullScoreBonus?: number
  isDefault?: boolean
}

export const scoringRulesApi = {
  async getAll(includeInactive = false): Promise<ScoringRule[]> {
    return apiGet<ScoringRule[]>('/admin/scoring-rules', {
      params: { includeInactive: String(includeInactive) },
    })
  },

  async getById(id: string): Promise<ScoringRule> {
    return apiGet<ScoringRule>(`/admin/scoring-rules/${id}`)
  },

  async create(dto: CreateScoringRuleDto): Promise<ScoringRule> {
    return apiPost<ScoringRule>('/admin/scoring-rules', dto)
  },

  async update(id: string, dto: UpdateScoringRuleDto): Promise<ScoringRule> {
    return apiPut<ScoringRule>(`/admin/scoring-rules/${id}`, dto)
  },

  async delete(id: string): Promise<void> {
    await apiDelete(`/admin/scoring-rules/${id}`)
  },
}
