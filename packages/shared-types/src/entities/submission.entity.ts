/**
 * Core Submission entity type
 */

import type { SubmissionStatus } from '../enums/submission-status.enum'

export interface SubmissionEntity {
  id: string
  user_id: string
  problem_id: string
  contest_id?: string
  language: string
  code: string
  status: SubmissionStatus
  score?: number
  time_used_ms?: number
  memory_used_kb?: number
  created_at: string
  updated_at: string
}

export { SubmissionStatus } from '../enums/submission-status.enum'
