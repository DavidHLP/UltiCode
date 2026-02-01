import type { BadgeVariant } from '@/lib/entities/user'

/**
 * Returns the badge variant for a user role
 */
export function getRoleBadgeVariant(role: string): BadgeVariant {
  switch (role) {
    case 'SUPER_ADMIN':
      return 'destructive'
    case 'ADMIN':
      return 'default'
    case 'MODERATOR':
      return 'secondary'
    default:
      return 'outline'
  }
}
