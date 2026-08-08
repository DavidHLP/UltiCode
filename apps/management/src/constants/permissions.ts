/**
 * Permission Constants (Management Frontend)
 *
 * Structured `{ action, resource }` form derived from the single source of
 * truth in `@/shared/auth-core/src/permission.ts` (`Permissions`). This file
 * exists only because management's route meta / composable APIs consume the
 * object shape; the canonical string keys live in the shared package so the
 * two frontends cannot drift.
 *
 * Architecture review Candidate 3 — eliminate the dual source of truth.
 *
 * Usage (unchanged for existing callers):
 *   import { PERM } from '@/constants/permissions'
 *   meta: { permission: PERM.USER_READ }
 *   PERM.USER_READ.action   // 'READ'
 *   PERM.USER_READ.resource // 'USER'
 */

import { Permissions, type PermissionKey } from '@/shared/auth-core/src/permission'

/**
 * Split a canonical `'ACTION:RESOURCE'` string into the structured form.
 * Internal helper — not exported. Used only to build the static `PERM` map.
 */
function splitPerm(value: string): { action: string; resource: string } {
  const idx = value.indexOf(':')
  if (idx === -1) {
    // Defensive: shared `Permissions` is the only caller, and its values are
    // validated by tests in shared/auth-core; this branch should never run.
    throw new Error(`Invalid permission string: ${value}`)
  }
  return { action: value.slice(0, idx), resource: value.slice(idx + 1) }
}

/**
 * Build the structured `PERM` map lazily over all keys of `Permissions`.
 *
 * We use a `reduce` over `Object.entries` rather than `Object.fromEntries`
 * so TypeScript can preserve the `as const` literal types of each
 * `action` / `resource` field per key (the consumer-visible API is
 * unchanged from the previous hand-written declaration).
 */
const PERM_ENTRIES = Object.entries(Permissions).map(([key, value]) => {
  const split = splitPerm(value)
  // Re-wrap with literal types so consumers retain 'READ' instead of string.
  return [
    key,
    {
      action: split.action,
      resource: split.resource,
    },
  ] as const
})

/**
 * Structured permission map derived from the shared source of truth.
 *
 * Each entry mirrors the previous hand-written `{ action: 'X' as const, resource: 'Y' as const }`
 * shape so consumers (router meta, composables, views) need no changes.
 */
export const PERM = Object.fromEntries(PERM_ENTRIES) as {
  [K in PermissionKey]: {
    readonly action: (typeof Permissions)[K] extends `${infer A}:${string}` ? A : never
    readonly resource: (typeof Permissions)[K] extends `${string}:${infer R}` ? R : never
  }
}

/**
 * Type for a single structured permission object (union over all PERM values).
 */
export type Permission = (typeof PERM)[PermissionKey]

/**
 * Type guard preserved from the previous declaration for runtime checks
 * at trust boundaries (e.g. parsing user-supplied meta).
 */
export function isPermission(value: unknown): value is Permission {
  if (typeof value !== 'object' || value === null) return false
  const v = value as Record<string, unknown>
  return typeof v.action === 'string' && typeof v.resource === 'string'
}
