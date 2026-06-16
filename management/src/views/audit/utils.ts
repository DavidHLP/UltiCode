import type { VNode } from 'vue'
import {
  IconCircleCheckFilled,
  IconDatabase,
  IconFileText,
  IconFlag,
  IconInfoCircle,
  IconLock,
  IconMessage,
  IconPin,
  IconRefresh,
  IconShield,
  IconTrash,
  IconUser,
  IconX,
} from '@tabler/icons-vue'
import { badge, getAuditActionColor, type SemanticColor } from '@/components/ui/terminal'

// --- Audit action/entityType constants (mirrors AuditActionUtil.java) ---

export const AUDIT_ENTITY_TYPES = [
  'USER',
  'PROBLEM',
  'CONTEST',
  'CONTEST_ANNOUNCEMENT',
  'SOLUTION',
  'SUBMISSION',
  'FORUM_POST',
  'FORUM_COMMENT',
  'COMMENT',
  'TAG',
  'PROBLEM_LIST',
  'SETTINGS',
  'PERMISSION',
  'NOTIFICATION',
] as const

export const AUDIT_ACTIONS_BY_ENTITY: Record<string, string[]> = {
  USER: ['CREATE_USER', 'UPDATE_USER', 'DELETE_USER', 'RESET_PASSWORD', 'BAN_USER', 'UNBAN_USER'],
  PROBLEM: ['CREATE_PROBLEM', 'UPDATE_PROBLEM', 'DELETE_PROBLEM'],
  CONTEST: ['CREATE_CONTEST', 'UPDATE_CONTEST', 'DELETE_CONTEST'],
  CONTEST_ANNOUNCEMENT: [
    'CREATE_CONTEST_ANNOUNCEMENT',
    'UPDATE_CONTEST_ANNOUNCEMENT',
    'DELETE_CONTEST_ANNOUNCEMENT',
  ],
  SOLUTION: [
    'CREATE_SOLUTION',
    'UPDATE_SOLUTION',
    'DELETE_SOLUTION',
    'FLAG_SOLUTION',
    'UNFLAG_SOLUTION',
  ],
  SUBMISSION: ['REQUEUE_SUBMISSION', 'DELETE_SUBMISSION'],
  FORUM_POST: [
    'CREATE_FORUM_POST',
    'UPDATE_FORUM_POST',
    'DELETE_FORUM_POST',
    'PIN_POST',
    'UNPIN_POST',
    'LOCK_POST',
    'UNLOCK_POST',
    'FLAG_POST',
    'UNFLAG_POST',
  ],
  COMMENT: ['FLAG_COMMENT', 'UNFLAG_COMMENT', 'DELETE_COMMENT'],
  TAG: ['CREATE_TAG', 'UPDATE_TAG', 'DELETE_TAG'],
  PROBLEM_LIST: ['UPDATE_PROBLEM_LIST', 'DELETE_PROBLEM_LIST'],
  SETTINGS: ['UPDATE_SETTINGS'],
  PERMISSION: ['GRANT_PERMISSION', 'REVOKE_PERMISSION'],
  NOTIFICATION: ['CREATE_NOTIFICATION', 'UPDATE_NOTIFICATION', 'DELETE_NOTIFICATION'],
  OTHER: ['MODERATE_CONTENT'],
}

export const AUDIT_ACTION_GROUPS = Object.keys(AUDIT_ACTIONS_BY_ENTITY)

export const ALL_AUDIT_ACTIONS = Object.values(AUDIT_ACTIONS_BY_ENTITY).flat()

export function actionToI18nKey(action: string): string {
  return `audit.actionTypes.${action}`
}

export function entityTypeToI18nKey(type: string): string {
  return `audit.entityTypes.${type}`
}

const KNOWN_ACTION_TYPE_GROUPS = new Set([
  'CREATE',
  'UPDATE',
  'DELETE',
  'FLAG',
  'UNFLAG',
  'BAN',
  'UNBAN',
  'GRANT',
  'REVOKE',
  'RESET',
  'PIN',
  'UNPIN',
  'LOCK',
  'UNLOCK',
  'REQUEUE',
  'MODERATE',
  'OTHER',
])

export function actionTypeGroupToI18nKey(actionType: string): string {
  if (KNOWN_ACTION_TYPE_GROUPS.has(actionType)) {
    return `audit.actionTypeGroups.${actionType}`
  }
  return `audit.actionTypeGroups.OTHER`
}

export function formatJson(value: unknown): string {
  if (!value) return 'N/A'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

/**
 * Returns a semantic badge VNode for an audit action.
 * Replaces the old getActionBadgeVariant (shadcn) and getActionBadgeClass (CSS string).
 *
 * `label` is rendered verbatim — callers that need a translated badge
 * should pre-translate (e.g. via `t(actionToI18nKey(action))`) before
 * passing it in. We do not call `useI18n()` here because this helper
 * is invoked from `h()` render functions where the i18n instance is
 * not in scope.
 */
export function getActionBadge(action: string, label?: string): VNode {
  return badge({
    color: getAuditActionColor(action),
    label: label ?? action,
    icon: getActionIcon(action),
  })
}

export function getActionIcon(action: string) {
  const a = action.toUpperCase()
  if (a.includes('CREATE') || a.includes('GRANT')) return IconCircleCheckFilled
  if (a.includes('UPDATE') || a.includes('PUBLISH')) return IconFileText
  if (a.includes('DELETE') || a.includes('REVOKE')) return IconTrash
  if (a.includes('BAN')) return IconX
  if (a.includes('UNBAN')) return IconShield
  if (a.includes('PIN')) return IconPin
  if (a.includes('LOCK')) return IconLock
  if (a.includes('FLAG')) return IconFlag
  if (a.includes('MODERATE')) return IconShield
  if (a.includes('REQUEUE')) return IconRefresh
  if (a.includes('RESET')) return IconRefresh
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
  if (upper === 'USER') return IconUser
  if (upper === 'PROBLEM' || upper === 'PROBLEM_LIST') return IconFileText
  if (upper.includes('CONTEST')) return IconDatabase
  if (upper === 'SOLUTION') return IconFileText
  if (upper === 'SUBMISSION') return IconRefresh
  if (upper === 'FORUM_POST') return IconMessage
  if (upper === 'COMMENT') return IconMessage
  if (upper === 'FORUM_COMMENT') return IconMessage
  if (upper === 'TAG') return IconFlag
  if (upper === 'PERMISSION') return IconShield
  if (upper === 'NOTIFICATION') return IconMessage
  if (upper === 'SETTINGS') return IconDatabase
  return IconInfoCircle
}
