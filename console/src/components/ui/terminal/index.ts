/**
 * Terminal UI barrel — re-exports from the shared `shared/badge-config` module.
 * The previous local SemanticBadge / useSemanticBadge / semantic-types copies
 * have been removed; this barrel provides a single shared source for both
 * console and management frontends.
 */
export { default as SemanticBadge } from "@/shared/badge-config/src/SemanticBadge.vue";
export { badge } from "@/shared/badge-config/src/useSemanticBadge";
export type {
  SemanticColor,
  BadgeOptions,
} from "@/shared/badge-config/src/semantic-colors";
export {
  DIFFICULTY_COLOR_MAP,
  USER_STATUS_COLOR_MAP,
  USER_ROLE_COLOR_MAP,
  CONTEST_STATUS_COLOR_MAP,
  CONTEST_TYPE_COLOR_MAP,
  PROBLEM_LIST_VISIBILITY_COLOR_MAP,
} from "@/shared/badge-config/src/color-maps";
