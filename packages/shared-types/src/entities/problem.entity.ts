/**
 * Core Problem entity type
 */

import type { Difficulty } from '../enums/difficulty.enum'

export interface ProblemEntity {
  id: string
  slug: string
  title: string
  difficulty: Difficulty
  acceptance_rate: number
  is_premium: boolean
  is_published: boolean
  created_at: string
  updated_at: string
}

export { Difficulty } from '../enums/difficulty.enum'
