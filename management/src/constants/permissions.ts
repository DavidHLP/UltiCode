/**
 * Permission Constants
 *
 * Centralized permission definitions for route guards and authorization checks.
 * Uses const assertion to ensure type safety and prevent mutation.
 *
 * Usage:
 *   import { PERM } from '@/constants/permissions'
 *   meta: { permission: PERM.USER_READ }
 */

export const PERM = {
  USER_READ: { action: 'READ' as const, resource: 'USER' as const },
  USER_CREATE: { action: 'CREATE' as const, resource: 'USER' as const },
  USER_UPDATE: { action: 'UPDATE' as const, resource: 'USER' as const },
  USER_DELETE: { action: 'DELETE' as const, resource: 'USER' as const },
  PROBLEM_READ: { action: 'READ' as const, resource: 'PROBLEM' as const },
  PROBLEM_CREATE: { action: 'CREATE' as const, resource: 'PROBLEM' as const },
  PROBLEM_UPDATE: { action: 'UPDATE' as const, resource: 'PROBLEM' as const },
  PROBLEM_DELETE: { action: 'DELETE' as const, resource: 'PROBLEM' as const },
  SOLUTION_READ: { action: 'READ' as const, resource: 'SOLUTION' as const },
  MODERATE_PROBLEM: { action: 'MODERATE' as const, resource: 'PROBLEM' as const },
  MODERATE_FORUM_POST: { action: 'MODERATE' as const, resource: 'FORUM_POST' as const },
  MODERATE_FORUM_COMMENT: { action: 'MODERATE' as const, resource: 'FORUM_COMMENT' as const },
  MODERATE_SOLUTION_COMMENT: { action: 'MODERATE' as const, resource: 'SOLUTION_COMMENT' as const },
  PROBLEM_LIST_READ: { action: 'READ' as const, resource: 'PROBLEM_LIST' as const },
  PROBLEM_LIST_CREATE: { action: 'CREATE' as const, resource: 'PROBLEM_LIST' as const },
  PROBLEM_LIST_UPDATE: { action: 'UPDATE' as const, resource: 'PROBLEM_LIST' as const },
  CONTEST_READ: { action: 'READ' as const, resource: 'CONTEST' as const },
  TAG_READ: { action: 'READ' as const, resource: 'TAG' as const },
  TAG_UPDATE: { action: 'UPDATE' as const, resource: 'TAG' as const },
  SYSTEM_READ: { action: 'READ' as const, resource: 'SYSTEM' as const },
  SYSTEM_UPDATE: { action: 'UPDATE' as const, resource: 'SYSTEM' as const },
} as const

/**
 * Type for a permission object
 */
export type Permission = typeof PERM[keyof typeof PERM]

/**
 * Type guard to check if a value is a valid permission
 */
export function isPermission(value: unknown): value is Permission {
  if (typeof value !== 'object' || value === null) return false
  const v = value as Record<string, unknown>
  return typeof v.action === 'string' && typeof v.resource === 'string'
}
