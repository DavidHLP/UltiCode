import { h, type VNode } from 'vue'
import { joinClasses } from './utils/cn'
import type { BadgeOptions, SemanticColor } from './semantic-colors'

const COLOR_CLASS_MAP: Record<SemanticColor, string> = {
  success: 'terminal-badge-success',
  warning: 'terminal-badge-warning',
  error: 'terminal-badge-error',
  info: 'terminal-badge-info',
  purple: 'terminal-badge-purple',
  electric: 'terminal-badge-electric',
  neutral: 'terminal-badge-neutral',
}

const SIZE_CLASSES = {
  xs: 'text-[10px] px-1.5 py-0.5',
  sm: 'text-[10px] px-2 py-0.5',
  md: 'text-[11px] px-2 py-0.5',
} as const

const DOT_COLOR_MAP: Record<SemanticColor, string> = {
  success: 'bg-[var(--status-success-mark)]',
  warning: 'bg-[var(--status-warning-mark)]',
  error: 'bg-[var(--status-error-mark)]',
  info: 'bg-[var(--status-info-mark)]',
  purple: 'bg-[var(--status-special-mark)]',
  electric: 'bg-[var(--accent-primary)]',
  neutral: 'bg-[var(--foreground-muted)]',
}

/**
 * Creates a semantic badge VNode for use in column render functions.
 *
 * Drop-in replacement for all local renderStatusBadge/renderDifficultyBadge functions.
 * Uses terminal-badge-* CSS classes from style.css — no hardcoded oklch values.
 */
export function badge(options: BadgeOptions): VNode {
  const { color, label, icon, dot, pulse, size = 'md' } = options
  const children: (VNode | string)[] = []

  if (dot) {
    children.push(
      h('span', {
        class: joinClasses(
          'w-1.5 h-1.5 rounded-full shrink-0',
          DOT_COLOR_MAP[color],
          pulse && 'animate-pulse-subtle',
        ),
      }),
    )
  }

  if (icon) {
    children.push(h(icon, { class: 'h-3.5 w-3.5 shrink-0' }))
  }

  children.push(label)

  return h(
    'span',
    {
      class: joinClasses(
        'terminal-badge inline-flex items-center gap-1.5',
        COLOR_CLASS_MAP[color],
        SIZE_CLASSES[size],
        pulse && !dot && 'animate-pulse-subtle',
        options.class,
      ),
    },
    children,
  )
}
