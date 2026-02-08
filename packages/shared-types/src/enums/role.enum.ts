/**
 * User roles
 */
export type UserRole = 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN'

export const UserRole = {
  USER: 'USER' as UserRole,
  MODERATOR: 'MODERATOR' as UserRole,
  ADMIN: 'ADMIN' as UserRole,
  SUPER_ADMIN: 'SUPER_ADMIN' as UserRole,
} as const

export function isUserRole(value: string): value is UserRole {
  return Object.values(UserRole).includes(value as UserRole)
}

/**
 * Role hierarchy for permission checks
 * Higher index = higher privilege
 */
export const RoleHierarchy: readonly UserRole[] = [
  'USER',
  'MODERATOR',
  'ADMIN',
  'SUPER_ADMIN',
] as const

export function hasRoleOrHigher(userRole: UserRole, requiredRole: UserRole): boolean {
  const userLevel = RoleHierarchy.indexOf(userRole)
  const requiredLevel = RoleHierarchy.indexOf(requiredRole)
  return userLevel >= requiredLevel
}
