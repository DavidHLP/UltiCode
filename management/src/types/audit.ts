/**
 * Audit log change value types
 * Represents the structure of old_values and new_values in audit logs
 */

// Import shared types
import type { UserRole } from '@ulticode/shared-types'

/**
 * Base interface for audit values
 * All entity-specific audit values extend this
 */
export interface AuditValueBase {
  id?: string
  updated_at?: string
}

/**
 * User entity audit values
 * Uses shared UserRole type for consistency
 */
export interface UserAuditValues extends AuditValueBase {
  username?: string
  email?: string
  name?: string
  role?: UserRole // Using shared UserRole
  is_active?: boolean
  is_banned?: boolean
}

/**
 * Problem entity audit values
 * Uses shared Difficulty type for consistency
 */
export interface ProblemAuditValues extends AuditValueBase {
  title?: string
  difficulty?: 'Easy' | 'Medium' | 'Hard' // Using shared Difficulty values
  is_published?: boolean
  is_premium?: boolean
}

/**
 * Contest entity audit values
 * Uses shared ContestType and ContestStatus for consistency
 */
export interface ContestAuditValues extends AuditValueBase {
  title?: string
  start_time?: string
  end_time?: string
  is_published?: boolean
  contest_type?: 'PUBLIC' | 'PRIVATE' | 'CONTEST' // Using shared ContestType values
  status?: 'UPCOMING' | 'ONGOING' | 'FINISHED' // Using shared ContestStatus values
}

/**
 * Forum post audit values
 */
export interface ForumPostAuditValues extends AuditValueBase {
  title?: string
  is_pinned?: boolean
  is_locked?: boolean
}

/**
 * Discriminated union for all audit value types
 */
export type AuditValue =
  | UserAuditValues
  | ProblemAuditValues
  | ContestAuditValues
  | ForumPostAuditValues
  | Record<string, unknown>

/**
 * Type guard for UserAuditValues
 */
export function isUserAuditValues(value: unknown): value is UserAuditValues {
  if (typeof value !== 'object' || value === null) return false
  const v = value as Record<string, unknown>
  return (
    typeof v.id === 'string' ||
    typeof v.username === 'string' ||
    typeof v.email === 'string' ||
    typeof v.role === 'string'
  )
}

/**
 * Get typed audit values based on entity type
 */
export function getAuditValues(values: unknown, entityType: string): AuditValue {
  switch (entityType) {
    case 'User':
    case 'user':
      return isUserAuditValues(values) ? values : (values as AuditValue)
    default:
      return values as AuditValue
  }
}

// Re-export shared types for convenience
export type { UserRole } from '@ulticode/shared-types'
