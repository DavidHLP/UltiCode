import type { SemanticColor } from './semantic-types'

// ─── Difficulty ────────────────────────────────────────────

export const DIFFICULTY_COLOR_MAP: Record<string, SemanticColor> = {
  EASY: 'success',
  MEDIUM: 'warning',
  HARD: 'error',
}

// ─── User Status ───────────────────────────────────────────

export const USER_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  BANNED: 'error',
}

// ─── User Role ─────────────────────────────────────────────

export const USER_ROLE_COLOR_MAP: Record<string, SemanticColor> = {
  SUPER_ADMIN: 'purple',
  ADMIN: 'info',
  MODERATOR: 'warning',
  USER: 'neutral',
}

// ─── Contest Status ────────────────────────────────────────

export const CONTEST_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  DRAFT: 'neutral',
  UPCOMING: 'warning',
  RUNNING: 'error',
  FINISHED: 'neutral',
  CANCELLED: 'neutral',
}

// ─── Contest Type ──────────────────────────────────────────

export const CONTEST_TYPE_COLOR_MAP: Record<string, SemanticColor> = {
  IOI: 'info',
  ICPC: 'info',
  PUBLIC: 'success',
  PRIVATE: 'warning',
  VIRTUAL: 'info',
  CUSTOM: 'electric',
}

// ─── Submission Status ─────────────────────────────────────

export const SUBMISSION_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  ACCEPTED: 'success',
  PENDING: 'warning',
  JUDGING: 'warning',
  WRONG_ANSWER: 'error',
  TIME_LIMIT_EXCEEDED: 'error',
  MEMORY_LIMIT_EXCEEDED: 'error',
  RUNTIME_ERROR: 'error',
  COMPILE_ERROR: 'error',
}

// ─── Problem List Visibility ──────────────────────────────

export const PROBLEM_LIST_VISIBILITY_COLOR_MAP: Record<string, SemanticColor> = {
  PUBLIC: 'success',
  PRIVATE: 'warning',
  UNLISTED: 'neutral',
}
