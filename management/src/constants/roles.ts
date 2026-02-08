/**
 * User role constants
 * Centralized role definitions to prevent typos and enable type safety
 */
export const UserRole = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMIN: 'ADMIN',
  MODERATOR: 'MODERATOR',
  USER: 'USER',
} as const

export type UserRole = (typeof UserRole)[keyof typeof UserRole]

/**
 * Type guard for UserRole
 */
export function isUserRole(value: string): value is UserRole {
  return Object.values(UserRole).includes(value as UserRole)
}

/**
 * Array of all valid user roles for iteration/validation
 */
export const USER_ROLES: readonly UserRole[] = Object.values(UserRole) as readonly UserRole[]
