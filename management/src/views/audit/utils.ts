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
  const actionUpper = action.toUpperCase()
  if (
    actionUpper.includes('CREATE') ||
    actionUpper.includes('GRANT') ||
    actionUpper.includes('PUBLISH')
  ) {
    return 'terminal-badge-success' // green
  }
  if (actionUpper.includes('UPDATE') || actionUpper.includes('UNBAN')) {
    return 'terminal-badge-info' // cyan
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'terminal-badge-error' // red
  }
  return 'terminal-badge-info'
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

/**
 * Get terminal color class based on action type
 * @param action - The action string to determine color style
 * @returns Terminal color class name
 */
export function getActionIconColor(action: string): string {
  const actionUpper = action.toUpperCase()
  if (
    actionUpper.includes('CREATE') ||
    actionUpper.includes('GRANT') ||
    actionUpper.includes('PUBLISH')
  ) {
    return 'text-[var(--terminal-green)]'
  }
  if (actionUpper.includes('UPDATE')) {
    return 'text-[var(--terminal-cyan)]'
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'text-[var(--terminal-red)]'
  }
  return 'text-[var(--silver-500)]'
}

export function getEntityTypeIcon(entityType: string | undefined) {
  if (!entityType) return IconInfoCircle
  const upper = entityType.toUpperCase()
  if (upper.includes('USER')) return IconUser
  if (upper.includes('PROBLEM')) return IconFileText
  if (upper.includes('CONTEST')) return IconDatabase
  return IconInfoCircle
}
