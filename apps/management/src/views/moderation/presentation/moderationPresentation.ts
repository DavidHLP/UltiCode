import { ModerationActionType, type ModeratableEntityType } from '@/api/admin/moderation'
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

/**
 * Presentation catalog for moderation actions and entity routes.
 *
 * Decision reconciliation belongs to the moderation store; this module only
 * supplies labels, icons, colors, duration hints, routes, and CSS tokens.
 */

export type ActionColorKey = 'purple' | 'amber' | 'red' | 'green'

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
  },
  {
    value: ModerationActionType.RESOLVED,
    labelKey: 'moderation.actions.RESOLVED',
    descriptionKey: 'moderation.actionDescriptions.RESOLVED',
    icon: ICONS.check,
    color: 'green',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.DELETED,
    labelKey: 'moderation.actions.DELETED',
    descriptionKey: 'moderation.actionDescriptions.DELETED',
    icon: ICONS.trash,
    color: 'red',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.HIDDEN,
    labelKey: 'moderation.actions.HIDDEN',
    descriptionKey: 'moderation.actionDescriptions.HIDDEN',
    icon: ICONS.eyeOff,
    color: 'amber',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.RESTORED,
    labelKey: 'moderation.actions.RESTORED',
    descriptionKey: 'moderation.actionDescriptions.RESTORED',
    icon: ICONS.refresh,
    color: 'green',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.WARNED,
    labelKey: 'moderation.actions.WARNED',
    descriptionKey: 'moderation.actionDescriptions.WARNED',
    icon: ICONS.alertCircle,
    color: 'amber',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.TEMP_BANNED,
    labelKey: 'moderation.actions.TEMP_BANNED',
    descriptionKey: 'moderation.actionDescriptions.TEMP_BANNED',
    icon: ICONS.clock,
    color: 'amber',
    requiresDuration: true,
  },
  {
    value: ModerationActionType.PERM_BANNED,
    labelKey: 'moderation.actions.PERM_BANNED',
    descriptionKey: 'moderation.actionDescriptions.PERM_BANNED',
    icon: ICONS.ban,
    color: 'red',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.APPEAL_PENDING,
    labelKey: 'moderation.actions.APPEAL_PENDING',
    descriptionKey: 'moderation.actionDescriptions.APPEAL_PENDING',
    icon: ICONS.scale,
    color: 'purple',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.APPEAL_APPROVED,
    labelKey: 'moderation.actions.APPEAL_APPROVED',
    descriptionKey: 'moderation.actionDescriptions.APPEAL_APPROVED',
    icon: ICONS.check,
    color: 'green',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.APPEAL_REJECTED,
    labelKey: 'moderation.actions.APPEAL_REJECTED',
    descriptionKey: 'moderation.actionDescriptions.APPEAL_REJECTED',
    icon: ICONS.x,
    color: 'red',
    requiresDuration: false,
  },
]



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
 * Centralised CSS variable mapping for the moderation chrome.
 * The views (QueueView drawer, ActionPanel cards) consume these so
 * the same ActionColorKey produces the same visual treatment in every
 * call site. Adding a new color is a one-line change here.
 */
interface ActionStyle {
  color: string
  background: string
  border: string
}

const ACTION_STYLES: Readonly<Record<ActionColorKey, ActionStyle>> = {
  red: {
    color: 'text-foreground-strong',
    background: 'bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-error-mark)_40%,_transparent)]',
  },
  amber: {
    color: 'text-foreground-strong',
    background: 'bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)]',
  },
  green: {
    color: 'text-foreground-strong',
    background: 'bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-success-mark)_40%,_transparent)]',
  },
  purple: {
    color: 'text-foreground-strong',
    background: 'bg-[color-mix(in_oklch,_var(--status-special-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-special-mark)_40%,_transparent)]',
  },
}

export const actionColorVar = (key: ActionColorKey): string => ACTION_STYLES[key].color
export const actionBgVar = (key: ActionColorKey): string => ACTION_STYLES[key].background
export const actionBorderVar = (key: ActionColorKey): string => ACTION_STYLES[key].border
