import type { Component } from 'vue'

/**
 * Semantic color tokens mapped to terminal-badge-* CSS classes.
 *
 * Each token corresponds to a `.terminal-badge-{color}` class in style.css
 * that provides consistent 15% bg / 30% border opacity using color-mix().
 */
export type SemanticColor =
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'purple'
  | 'electric'
  | 'neutral'

export interface BadgeOptions {
  /** Semantic color determining the badge's appearance */
  color: SemanticColor
  /** Display label text */
  label: string
  /** Optional leading icon component (rendered at h-3.5 w-3.5) */
  icon?: Component
  /** Show a colored dot indicator before the label */
  dot?: boolean
  /** Apply pulse animation to the entire badge */
  pulse?: boolean
  /** Size variant */
  size?: 'xs' | 'sm' | 'md'
  /** Additional CSS classes */
  class?: string
}
