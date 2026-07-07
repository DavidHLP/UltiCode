// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

// Permission is defined in ./types; we import it with `import type` so it is
// available for internal use (parsePermissionString return type, etc.) and
// also re-export it so consumers of './permission' still receive the type.
import type { Permission } from './types';
export type { Permission };

export type PermissionMatchMode = 'ANY' | 'ALL';

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------

const PERMISSION_RE = /^([^:*]+):([^:*]+)$|^\*:\*$|^\*:[^:*]+$|^[^:*]+:\*$/;

/**
 * Parse a permission string into a Permission object.
 * Supports wildcards: `*:*`, `action:*`, `*:resource`.
 * Returns `null` for invalid formats.
 */
export function parsePermissionString(permission: string): Permission | null {
  if (!permission || typeof permission !== 'string') return null;

  const trimmed = permission.trim();
  if (trimmed === '*:*') {
    return { action: '*', resource: '*' };
  }

  const colonIdx = trimmed.indexOf(':');
  if (colonIdx === -1) return null;

  const action = trimmed.slice(0, colonIdx);
  const resource = trimmed.slice(colonIdx + 1);

  if (!action || !resource) return null;

  // Validate parts: if not wildcard, must be non-empty.
  if (action !== '*' && !action) return null;
  if (resource !== '*' && !resource) return null;

  return { action, resource };
}

/**
 * Check whether a single user permission matches a single required permission.
 * Handles wildcards.
 */
function matches(userPerm: Permission, required: Permission): boolean {
  const actionOk = userPerm.action === '*' || userPerm.action === required.action;
  const resourceOk = userPerm.resource === '*' || userPerm.resource === required.resource;
  return actionOk && resourceOk;
}

// ---------------------------------------------------------------------------
// hasPermission
// ---------------------------------------------------------------------------

/**
 * Check whether a user's permission set satisfies the required permission(s).
 *
 * @param userPermissions  Set of permission strings the user holds, e.g. `new Set(['problem:read', 'submission:create'])`
 * @param required         A single Permission or an array of Permissions
 * @param mode              `'ANY'` – user needs at least one match (default)
 *                          `'ALL'` – user needs every required permission
 */
export function hasPermission(
  userPermissions: Set<string>,
  required: Permission | Permission[],
  mode: PermissionMatchMode = 'ANY',
): boolean {
  if (userPermissions.size === 0) return false;

  const requiredList = Array.isArray(required) ? required : [required];

  if (mode === 'ALL') {
    return requiredList.every((req) => {
      const userPerms = [...userPermissions];
      return userPerms.some((up) => {
        const parsed = parsePermissionString(up);
        return parsed !== null && matches(parsed, req);
      });
    });
  }

  // ANY mode
  return requiredList.some((req) => {
    const userPerms = [...userPermissions];
    return userPerms.some((up) => {
      const parsed = parsePermissionString(up);
      return parsed !== null && matches(parsed, req);
    });
  });
}

// ---------------------------------------------------------------------------
// Wildcard constants
// ---------------------------------------------------------------------------

/** Convenience constant for the super-admin wildcard permission. */
export const WILDCARD_PERMISSION: Permission = { action: '*', resource: '*' };

// ---------------------------------------------------------------------------
// Pre-defined permission constants
// ---------------------------------------------------------------------------

/**
 * Pre-defined permission constants for common operations.
 *
 * Single source of truth for permission strings consumed by both `console` and
 * `management` frontends. Each value follows the `ACTION:RESOURCE` contract used
 * by the backend `user_permissions` / `role_permissions` tables.
 *
 * Use these constants instead of raw strings to avoid typos and enable refactoring.
 *
 * Example usage in management router guards:
 * ```ts
 * import { Permissions, hasPermission } from '@/shared/auth-core';
 *
 * const userPermissions = new Set(user.permissions);
 * if (!hasPermission(userPermissions, Permissions.USER_READ)) {
 *   return false;
 * }
 * ```
 *
 * When the management frontend needs the structured `{ action, resource }` form
 * (e.g. for `meta: { permission: { action, resource } }` route metadata), it
 * derives that from this constant — see `management/src/constants/permissions.ts`.
 */
export const Permissions = {
  USER_READ: 'READ:USER',
  USER_CREATE: 'CREATE:USER',
  USER_UPDATE: 'UPDATE:USER',
  USER_DELETE: 'DELETE:USER',
  PROBLEM_READ: 'READ:PROBLEM',
  PROBLEM_CREATE: 'CREATE:PROBLEM',
  PROBLEM_UPDATE: 'UPDATE:PROBLEM',
  PROBLEM_DELETE: 'DELETE:PROBLEM',
  SOLUTION_READ: 'READ:SOLUTION',
  MODERATE_PROBLEM: 'MODERATE:PROBLEM',
  MODERATE_FORUM_POST: 'MODERATE:FORUM_POST',
  MODERATE_FORUM_COMMENT: 'MODERATE:FORUM_COMMENT',
  MODERATE_SOLUTION_COMMENT: 'MODERATE:SOLUTION_COMMENT',
  DELETE_FORUM_COMMENT: 'DELETE:FORUM_COMMENT',
  DELETE_SOLUTION_COMMENT: 'DELETE:SOLUTION_COMMENT',
  PROBLEM_LIST_READ: 'READ:PROBLEM_LIST',
  PROBLEM_LIST_CREATE: 'CREATE:PROBLEM_LIST',
  PROBLEM_LIST_UPDATE: 'UPDATE:PROBLEM_LIST',
  PROBLEM_LIST_DELETE: 'DELETE:PROBLEM_LIST',
  PROBLEM_LIST_MANAGE_PROBLEMS: 'MANAGE_PROBLEMS:PROBLEM_LIST',
  CONTEST_READ: 'READ:CONTEST',
  TAG_READ: 'READ:TAG',
  TAG_UPDATE: 'UPDATE:TAG',
  SYSTEM_READ: 'READ:SYSTEM',
  SYSTEM_UPDATE: 'UPDATE:SYSTEM',
} as const;

/**
 * Type union of all permission keys. Useful for typed route-meta / form schemas.
 */
export type PermissionKey = keyof typeof Permissions;

// ---------------------------------------------------------------------------
// Store-level convenience: checkPermission / checkRole / checkAnyRole
// ---------------------------------------------------------------------------

/**
 * Check whether a user's permission set grants a specific (action, resource)
 * pair, supporting `*:*`, `action:*`, `*:resource`, and exact-match wildcards.
 *
 * Single source of truth for the inline `hasPermission(action, resource)`
 * logic previously duplicated in both auth stores (arch review candidate #4).
 */
export function checkPermission(
  userPermissions: Set<string>,
  action: string,
  resource: string,
): boolean {
  if (userPermissions.size === 0) return false;
  if (userPermissions.has('*:*')) return true;
  if (userPermissions.has(`${action}:${resource}`)) return true;
  if (userPermissions.has(`${action}:*`)) return true;
  if (userPermissions.has(`*:${resource}`)) return true;
  return false;
}

/**
 * Case-insensitive role comparison against the user's role string.
 */
export function checkRole(userRole: string | undefined | null, role: string): boolean {
  if (!userRole) return false;
  return userRole.toUpperCase() === role.toUpperCase();
}

/**
 * Case-insensitive check: does the user hold any of the listed roles?
 */
export function checkAnyRole(
  userRole: string | undefined | null,
  roles: string[],
): boolean {
  if (!userRole) return false;
  const upper = userRole.toUpperCase();
  return roles.some((r) => r.toUpperCase() === upper);
}
