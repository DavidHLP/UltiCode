import type { Component } from 'vue'

/**
 * The 7 semantic terminal badge colors.
 * Maps to .terminal-badge-{color} CSS classes in style.css.
 */
export type SemanticColor =
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'purple'
  | 'electric'
  | 'neutral'

/**
 * Options for the badge() helper function.
 */
export interface BadgeOptions {
  color: SemanticColor
  label: string
  icon?: Component
  dot?: boolean
  pulse?: boolean
  size?: 'xs' | 'sm' | 'md'
  class?: string
}
