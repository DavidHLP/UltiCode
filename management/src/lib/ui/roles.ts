import type { BadgeVariant } from '@/lib/entities/user'
import { UserRole, type UserRole as UserRoleType } from '@/constants/roles'

/**
 * Returns the badge variant for a user role
 */
export function getRoleBadgeVariant(role: UserRoleType): BadgeVariant {
  switch (role) {
    case UserRole.SUPER_ADMIN:
      return 'destructive'
    case UserRole.ADMIN:
      return 'default'
    case UserRole.MODERATOR:
      return 'secondary'
    case UserRole.USER:
    default:
      return 'outline'
  }
}
