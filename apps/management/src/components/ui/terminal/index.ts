/**
 * Terminal UI barrel — re-exports from the shared `shared/badge-config` module.
 * The previous local SemanticBadge / useSemanticBadge / semantic-types copies
 * have been removed; this barrel provides a single shared source for both
 * console and management frontends. Management-only components (TerminalCard,
 * TerminalInput, TerminalBadge, DataBlock) remain local.
 */
export { default as TerminalCard } from './TerminalCard.vue'
export { default as TerminalInput } from './TerminalInput.vue'
export { default as TerminalBadge } from './TerminalBadge.vue'
export { default as DataBlock } from './DataBlock.vue'

export { default as SemanticBadge } from '@ulticode/badge-config/SemanticBadge.vue'
export { badge } from '@ulticode/badge-config/useSemanticBadge'
export type { SemanticColor, BadgeOptions } from '@ulticode/badge-config/semantic-colors'
export {
  DIFFICULTY_COLOR_MAP,
  USER_STATUS_COLOR_MAP,
  USER_ROLE_COLOR_MAP,
  CONTEST_STATUS_COLOR_MAP,
  CONTEST_TYPE_COLOR_MAP,
  MODERATION_STATUS_COLOR_MAP,
  NOTIFICATION_TYPE_COLOR_MAP,
  CONTENT_FLAG_COLOR_MAP,
  PROBLEM_LIST_VISIBILITY_COLOR_MAP,
  getAuditActionColor,
} from '@ulticode/badge-config/color-maps'
