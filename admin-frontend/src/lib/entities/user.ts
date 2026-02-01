import type { Component } from 'vue'
import { h } from 'vue'
import {
  IconUser,
  IconShield,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconLoader,
} from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import type { User } from '@/api/admin/users'

export type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline'

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

/**
 * Returns the icon component for a user's status
 */
export function getStatusIcon(user: User): Component {
  if (user.is_banned) {
    return h(IconCircleXFilled, { class: 'h-4 w-4 text-destructive' })
  }
  if (user.is_active) {
    return h(IconCircleCheckFilled, { class: 'h-4 w-4 text-emerald-500' })
  }
  return h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' })
}

/**
 * Returns the badge component for a user's status
 * @param t - i18n translation function
 */
export function getStatusBadge(user: User, t: (key: string) => string): Component {
  if (user.is_banned) {
    return h(Badge, { variant: 'destructive' }, () => t('users.status.banned'))
  }
  if (user.is_active) {
    return h(Badge, { variant: 'default' }, () => t('users.status.active'))
  }
  return h(Badge, { variant: 'secondary' }, () => t('users.status.inactive'))
}

/**
 * Returns the icon component for a user's role
 */
export function getRoleIcon(role: string): Component {
  const icon = role === 'USER' ? IconUser : IconShield
  return h(icon, { class: 'h-4 w-4 text-muted-foreground' })
}
