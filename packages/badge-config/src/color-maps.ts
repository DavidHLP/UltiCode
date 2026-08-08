import type { SemanticColor } from './semantic-colors'

/**
 * Domain → semantic color maps for badges.
 *
 * Convention: every key here is the canonical value emitted by the backend
 * (UPPERCASE_UNDERSCORE for enums, lowercase for `ContentFlag`). Do not add
 * aliased keys for "defensive lookup" — that hides the real source of the
 * inconsistency. Callers that receive a non-canonical value (e.g. a UI-only
 * state like 'ongoing' or 'freezing' that does not exist in the backend enum)
 * must normalize at the call site, not via a second copy of the key.
 *
 * The previous `CONTEST_STATUS_COLOR_MAP` carried both lowercase and
 * uppercase variants of the same status ('draft' / 'DRAFT' etc.) plus a set
 * of management-only UI states ('published', 'registering', 'ongoing',
 * 'freezing', 'archived') that do not exist in the backend ContestStatus
 * enum. None of the lowercase keys had a current consumer; the UI-only keys
 * are now owned by the management frontend (see
 * `management/src/views/contest/components/ContestStatusBadge.vue`'s local
 * `statusConfig`). See arch review 2026-07-10, candidate #4.
 */

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
// Keys mirror the backend `ContestStatus` enum (UPPERCASE_UNDERSCORE).
// Management's UI-only states ('published', 'registering', 'ongoing',
// 'freezing', 'archived') are NOT mapped here — they live in management's
// local ContestStatusBadge so this map stays aligned with the backend truth.

export const CONTEST_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  DRAFT: 'neutral',
  UPCOMING: 'warning',
  RUNNING: 'error',
  FINISHED: 'neutral',
  CANCELLED: 'neutral',
}

/**
 * Normalize an arbitrary status string to the canonical UPPERCASE_UNDERSCORE
 * key used by `CONTEST_STATUS_COLOR_MAP`. Returns the input unchanged if it
 * is already a known map key; otherwise upper-cases it so callers can fall
 * back gracefully to `'neutral'` for unknown values.
 */
export function normalizeContestStatusKey(status: string): string {
  if (status in CONTEST_STATUS_COLOR_MAP) return status
  return status.toUpperCase().replace(/\s+/g, '_')
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
