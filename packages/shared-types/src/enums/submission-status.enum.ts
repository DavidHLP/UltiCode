/**
 * Submission status codes
 */
export type SubmissionStatus =
  | 'PENDING'
  | 'JUDGING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'

export const SubmissionStatus = {
  PENDING: 'PENDING' as SubmissionStatus,
  JUDGING: 'JUDGING' as SubmissionStatus,
  ACCEPTED: 'ACCEPTED' as SubmissionStatus,
  WRONG_ANSWER: 'WRONG_ANSWER' as SubmissionStatus,
  TIME_LIMIT_EXCEEDED: 'TIME_LIMIT_EXCEEDED' as SubmissionStatus,
  MEMORY_LIMIT_EXCEEDED: 'MEMORY_LIMIT_EXCEEDED' as SubmissionStatus,
  RUNTIME_ERROR: 'RUNTIME_ERROR' as SubmissionStatus,
  COMPILATION_ERROR: 'COMPILATION_ERROR' as SubmissionStatus,
} as const

export function isSubmissionStatus(value: string): value is SubmissionStatus {
  return Object.values(SubmissionStatus).includes(value as SubmissionStatus)
}
