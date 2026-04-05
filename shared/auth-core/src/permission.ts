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
