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

export function getActionIconColor(action: string) {
  const actionUpper = action.toUpperCase()
  if (
    actionUpper.includes('CREATE') ||
    actionUpper.includes('GRANT') ||
    actionUpper.includes('PUBLISH')
  ) {
    return 'text-emerald-500'
  }
  if (actionUpper.includes('UPDATE')) {
    return 'text-blue-500'
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'text-red-500'
  }
  return 'text-muted-foreground'
}

export function getEntityTypeIcon(entityType: string | undefined) {
  if (!entityType) return IconInfoCircle
  const upper = entityType.toUpperCase()
  if (upper.includes('USER')) return IconUser
  if (upper.includes('PROBLEM')) return IconFileText
  if (upper.includes('CONTEST')) return IconDatabase
  return IconInfoCircle
}
