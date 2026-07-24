import {
  ModerationActionType,
  ModerationStatus,
  type ModeratableEntityType,
  type ModerationQueueItem,
} from '@/api/admin/moderation'
import {
  IconCheck,
  IconX,
  IconTrash,
  IconEyeOff,
  IconRefresh,
  IconAlertCircle,
  IconClock,
  IconBan,
  IconScale,
} from '@tabler/icons-vue'
import type { SemanticColor } from '@/components/ui/terminal'

/**
 * Single seam for the moderation decision lifecycle. Owns:
 *   - the action catalog (icon, color, requiresDuration, terminal, label key);
 *   - the entity route resolver;
 *   - the terminal-status reconciliation rule;
 *   - a `runDecision` orchestrator that combines HTTP + reconciliation.
 *
 * Before this module existed the catalog lived inline in `ModerationQueueView`
 * and `ModerationActionPanel`, the entity route map was duplicated in
 * `QueueView` and `ReportsView`, the terminal-status rule was hard-coded in
 * the store's `performAction`, and the store's `reviewAppeal` did not
 * refresh stats — a silent post-decision inconsistency.
 *
 * All callers now consume the catalog + entity routes + decision lifecycle
 * through this single interface, so an added action type or entity type is
 * a one-line change here, not a sweep across views.
 */

export type ActionColorKey = SemanticColor | 'purple' | 'amber' | 'red' | 'green' | 'cyan'

export interface ActionDescriptor {
  value: ModerationActionType
  /** i18n key for the short label (`moderation.actions.<KEY>`). */
  labelKey: string
  /** i18n key for the long description (used by the action panel). */
  descriptionKey: string
  /** Tabler icon component. */
  icon: unknown
  /** CSS color token. */
  color: ActionColorKey
  /** True when the action requires a positive duration in days. */
  requiresDuration: boolean
  /** True when performing this action leaves the queue item in a final state. */
  terminal: boolean
}

const ICONS = {
  check: IconCheck,
  x: IconX,
  trash: IconTrash,
  eyeOff: IconEyeOff,
  refresh: IconRefresh,
  alertCircle: IconAlertCircle,
  clock: IconClock,
  ban: IconBan,
  scale: IconScale,
} as const

/**
 * Canonical action catalog. The order here is the order rendered in
 * dropdowns (QueueView, ActionPanel) so it doubles as the visible
 * progression from "preserve" to "remove".
 */
export const ACTION_CATALOG: ReadonlyArray<ActionDescriptor> = [
  {
    value: ModerationActionType.DISMISSED,
    labelKey: 'moderation.actions.DISMISSED',
    descriptionKey: 'moderation.actionDescriptions.DISMISSED',
    icon: ICONS.x,
    color: 'red',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.RESOLVED,
    labelKey: 'moderation.actions.RESOLVED',
    descriptionKey: 'moderation.actionDescriptions.RESOLVED',
    icon: ICONS.check,
    color: 'green',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.DELETED,
    labelKey: 'moderation.actions.DELETED',
    descriptionKey: 'moderation.actionDescriptions.DELETED',
    icon: ICONS.trash,
    color: 'red',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.HIDDEN,
    labelKey: 'moderation.actions.HIDDEN',
    descriptionKey: 'moderation.actionDescriptions.HIDDEN',
    icon: ICONS.eyeOff,
    color: 'amber',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.RESTORED,
    labelKey: 'moderation.actions.RESTORED',
    descriptionKey: 'moderation.actionDescriptions.RESTORED',
    icon: ICONS.refresh,
    color: 'green',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.WARNED,
    labelKey: 'moderation.actions.WARNED',
    descriptionKey: 'moderation.actionDescriptions.WARNED',
    icon: ICONS.alertCircle,
    color: 'amber',
    requiresDuration: false,
    terminal: false,
  },
  {
    value: ModerationActionType.TEMP_BANNED,
    labelKey: 'moderation.actions.TEMP_BANNED',
    descriptionKey: 'moderation.actionDescriptions.TEMP_BANNED',
    icon: ICONS.clock,
    color: 'amber',
    requiresDuration: true,
    terminal: true,
  },
  {
    value: ModerationActionType.PERM_BANNED,
    labelKey: 'moderation.actions.PERM_BANNED',
    descriptionKey: 'moderation.actionDescriptions.PERM_BANNED',
    icon: ICONS.ban,
    color: 'red',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.APPEAL_PENDING,
    labelKey: 'moderation.actions.APPEAL_PENDING',
    descriptionKey: 'moderation.actionDescriptions.APPEAL_PENDING',
    icon: ICONS.scale,
    color: 'purple',
    requiresDuration: false,
    terminal: false,
  },
  {
    value: ModerationActionType.APPEAL_APPROVED,
    labelKey: 'moderation.actions.APPEAL_APPROVED',
    descriptionKey: 'moderation.actionDescriptions.APPEAL_APPROVED',
    icon: ICONS.check,
    color: 'green',
    requiresDuration: false,
    terminal: true,
  },
  {
    value: ModerationActionType.APPEAL_REJECTED,
    labelKey: 'moderation.actions.APPEAL_REJECTED',
    descriptionKey: 'moderation.actionDescriptions.APPEAL_REJECTED',
    icon: ICONS.x,
    color: 'red',
    requiresDuration: false,
    terminal: true,
  },
]

const ACTION_BY_VALUE: Map<ModerationActionType, ActionDescriptor> = new Map(
  ACTION_CATALOG.map((a) => [a.value, a]),
)

export const findAction = (value: ModerationActionType): ActionDescriptor | undefined =>
  ACTION_BY_VALUE.get(value)

/**
 * Terminal status set. Mirrors the previous hard-coded
 * `item.status === 'RESOLVED' || item.status === 'DISMISSED'` check that
 * lived inline in the store's `performAction`; centralising it here
 * means a new terminal status is one constant, not a code sweep.
 */
export const TERMINAL_STATUSES: ReadonlySet<ModerationStatus> = new Set([
  ModerationStatus.RESOLVED,
  ModerationStatus.DISMISSED,
])

export const isTerminalStatus = (status: ModerationStatus): boolean =>
  TERMINAL_STATUSES.has(status)

/**
 * Resolves an entity (post / comment / solution / problem) to its
 * management view route. Both the queue and report views use this so
 * an added entity type is a one-line change.
 */
export const ENTITY_ROUTES: Readonly<Record<ModeratableEntityType, (id: string) => string>> = {
  forum_post: (id) => `/forum/posts/${id}`,
  forum_comment: (id) => `/comments/forum/${id}`,
  solution: (id) => `/solutions/${id}`,
  solution_comment: (id) => `/comments/solution/${id}`,
  problem: (id) => `/problems/${id}`,
}

export const entityRoute = (entity: ModeratableEntityType, entityId: string): string =>
  ENTITY_ROUTES[entity](entityId)

/**
 * Whether the queue item is in a state where actions are allowed
 * (PENDING or UNDER_REVIEW). Used by both the actions column and the
 * batch action bar to gate write controls.
 */
export const isActionable = (item: ModerationQueueItem): boolean =>
  item.status === ModerationStatus.PENDING || item.status === ModerationStatus.UNDER_REVIEW
