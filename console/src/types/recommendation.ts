// console/src/types/recommendation.ts

/**
 * 推荐场景类型（与后端 RecommendScenario 枚举对应）
 */
export type RecommendScenario = 'DAILY' | 'SIMILAR' | 'WEAK_POINT' | 'CHALLENGE'

/**
 * 前端路由使用的推荐类型
 */
export type RecommendType = 'daily' | 'weak-points' | 'challenge' | 'similar'

/**
 * 单个推荐题目项（与后端 RecommendItem DTO 对应）
 */
export interface RecommendItem {
  /** 题目 ID */
  problemId: number
  /** URL 友好的标识 */
  slug: string
  /** 题目标题 */
  title: string
  /** 难度 (Easy/Medium/Hard) */
  difficulty: string
  /** 推荐分数 (0.0 - 1.0) */
  score: number
  /** 标签列表 */
  tags: string[]
  /** 推荐理由 */
  reason: string
}

/**
 * 推荐结果（与后端 RecommendResult DTO 对应）
 */
export interface RecommendResult {
  /** 推荐题目列表 */
  items: RecommendItem[]
  /** 总数 */
  totalCount: number
  /** 使用的推荐场景 */
  scenario: RecommendScenario
  /** 生成时间 */
  generatedAt: string
}

/**
 * API 响应包装（与后端 RecommendResponse DTO 对应）
 */
export interface RecommendResponse {
  success: boolean
  code: number
  message: string
  data: RecommendResult | null
}
