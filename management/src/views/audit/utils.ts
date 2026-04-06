import {
  IconCircleCheckFilled,
  IconDatabase,
  IconFileText,
  IconInfoCircle,
  IconShield,
  IconTrash,
  IconUser,
  IconX,
} from '@tabler/icons-vue'
import { getAuditActionColor, type SemanticColor } from '@/components/ui/terminal'

const COLOR_TO_CLASS: Record<SemanticColor, string> = {
  success: 'terminal-badge-success',
  warning: 'terminal-badge-warning',
  error: 'terminal-badge-error',
  info: 'terminal-badge-info',
  purple: 'terminal-badge-purple',
  electric: 'terminal-badge-electric',
  neutral: 'terminal-badge-neutral',
}

export function formatJson(value: unknown): string {
  if (!value) return 'N/A'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

export function getActionBadgeVariant(
  action: string,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  const actionUpper = action.toUpperCase()
  if (
    actionUpper.includes('CREATE') ||
    actionUpper.includes('GRANT') ||
    actionUpper.includes('PUBLISH')
  ) {
    return 'default'
  }
  if (actionUpper.includes('UPDATE') || actionUpper.includes('UNBAN')) {
    return 'secondary'
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'destructive'
  }
  return 'outline'
}

/**
 * Get terminal badge class based on action type
 * @param action - The action string to determine badge style
 * @returns Terminal badge class name
 */
export function getActionBadgeClass(action: string): string {
  return COLOR_TO_CLASS[getAuditActionColor(action)]
}

export function getActionIcon(action: string) {
  const actionUpper = action.toUpperCase()
  if (actionUpper.includes('CREATE') || actionUpper.includes('GRANT')) {
    return IconCircleCheckFilled
  }
  if (actionUpper.includes('UPDATE') || actionUpper.includes('PUBLISH')) {
    return IconFileText
  }
  if (actionUpper.includes('DELETE') || actionUpper.includes('REVOKE')) {
    return IconTrash
  }
  if (actionUpper.includes('BAN')) {
    return IconX
  }
  if (actionUpper.includes('UNBAN')) {
    return IconShield
  }
  return IconInfoCircle
}

const COLOR_TO_TEXT_CLASS: Record<SemanticColor, string> = {
  success: 'text-[var(--terminal-green)]',
  warning: 'text-[var(--terminal-amber)]',
  error: 'text-[var(--terminal-red)]',
  info: 'text-[var(--terminal-cyan)]',
  purple: 'text-[var(--terminal-purple)]',
  electric: 'text-[var(--accent-electric)]',
  neutral: 'text-[var(--silver-500)]',
}

/**
 * Get terminal color class based on action type
 * @param action - The action string to determine color style
 * @returns Terminal color class name
 */
export function getActionIconColor(action: string): string {
  return COLOR_TO_TEXT_CLASS[getAuditActionColor(action)]
}

export function getEntityTypeIcon(entityType: string | undefined) {
  if (!entityType) return IconInfoCircle
  const upper = entityType.toUpperCase()
  if (upper.includes('USER')) return IconUser
  if (upper.includes('PROBLEM')) return IconFileText
  if (upper.includes('CONTEST')) return IconDatabase
  return IconInfoCircle
}
