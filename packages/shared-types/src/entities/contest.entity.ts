/**
 * Core Contest entity type
 */

import type { ContestType, ContestStatus } from '../enums/contest-type.enum'

export interface ContestEntity {
  id: string
  slug: string
  title: string
  description?: string
  contest_type: ContestType
  status: ContestStatus
  start_time: string
  duration_minutes: number
  created_at: string
  updated_at: string
}

export { ContestType, ContestStatus } from '../enums/contest-type.enum'
