import type { SemanticColor } from './semantic-colors'

// ─── Difficulty ────────────────────────────────────────────

export const DIFFICULTY_COLOR_MAP: Record<string, SemanticColor> = {
  EASY: 'success',
  MEDIUM: 'warning',
  HARD: 'error',
  // lowercase fallbacks for defensive lookup
  easy: 'success',
  medium: 'warning',
  hard: 'error',
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
// Includes both lowercase (management) and uppercase (console) keys
// for backward compatibility with existing code.

export const CONTEST_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  // lowercase keys (management convention)
  draft: 'neutral',
  published: 'electric',
  registering: 'success',
  upcoming: 'warning',
  ongoing: 'error',
  freezing: 'purple',
  finished: 'neutral',
  archived: 'neutral',
  // uppercase keys (console convention)
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

// ─── Moderation Status ─────────────────────────────────────

export const MODERATION_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  RESOLVED: 'success',
  DISMISSED: 'error',
  APPEAL_PENDING: 'purple',
}

// ─── Notification Type ─────────────────────────────────────

export const NOTIFICATION_TYPE_COLOR_MAP: Record<string, SemanticColor> = {
  SYSTEM: 'info',
  CONTEST: 'success',
  SUBMISSION: 'warning',
  COMMENT: 'info',
  REPLY: 'info',
  MENTION: 'info',
}

// ─── Content Flag ──────────────────────────────────────────

export const CONTENT_FLAG_COLOR_MAP: Record<string, SemanticColor> = {
  flagged: 'warning',
  deleted: 'error',
  published: 'success',
  normal: 'neutral',
}

// ─── Problem List Visibility ──────────────────────────────

export const PROBLEM_LIST_VISIBILITY_COLOR_MAP: Record<string, SemanticColor> = {
  PUBLIC: 'success',
  PRIVATE: 'warning',
  UNLISTED: 'neutral',
}

// ─── Audit Action ──────────────────────────────────────────

export function getAuditActionColor(action: string): SemanticColor {
  const upper = action.toUpperCase()
  if (upper.includes('CREATE') || upper.includes('GRANT') || upper.includes('PUBLISH'))
    return 'success'
  if (upper.includes('UPDATE') || upper.includes('UNBAN')) return 'info'
  if (upper.includes('DELETE') || upper.includes('BAN') || upper.includes('REVOKE')) return 'error'
  return 'info'
}
