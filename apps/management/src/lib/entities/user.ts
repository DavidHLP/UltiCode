import { h, type VNode } from 'vue'
import {
  User as IconUser,
  Shield as IconShield,
  CircleCheck as IconCircleCheckFilled,
  CircleX as IconCircleXFilled,
  Loader as IconLoader,
} from '@lucide/vue'

import { badge } from '@ulticode/badge-config'
import type { User } from '@/api/admin/users'

/**
 * Returns the icon component for a user's status
 */
export function getStatusIcon(user: User): VNode {
  if (user.isBanned) {
    return h(IconCircleXFilled, { class: 'h-4 w-4 text-destructive' })
  }
  if (user.isActive) {
    return h(IconCircleCheckFilled, { class: 'h-4 w-4 text-foreground' })
  }
  return h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' })
}

/**
 * Returns the badge component for a user's status
 * @param t - i18n translation function
 */
export function getStatusBadge(user: User, t: (key: string) => string): VNode {
  if (user.isBanned) return badge({ color: 'error', label: t('users.status.banned'), pulse: true })
  if (!user.isActive) return badge({ color: 'neutral', label: t('users.status.inactive') })
  return badge({ color: 'success', label: t('users.status.active'), dot: true, pulse: true })
}

/**
 * Returns the icon component for a user's role
 */
export function getRoleIcon(role: string): VNode {
  const icon = role === 'USER' ? IconUser : IconShield
  return h(icon, { class: 'h-4 w-4 text-muted-foreground' })
}
