<script setup lang="ts">
/**
 * SemanticBadge — Unified status/type badge with Solarized terminal colors.
 *
 * Shared between console and management frontends.
 * Uses `.terminal-badge-{color}` CSS classes for consistent styling.
 */
import type { SemanticColor } from './semantic-colors'
import { joinClasses } from './utils/cn'

withDefaults(
  defineProps<{
    color: SemanticColor
    label?: string
    dot?: boolean
    pulse?: boolean
    size?: 'xs' | 'sm' | 'md'
  }>(),
  {
    label: '',
    dot: false,
    pulse: false,
    size: 'md',
  },
)

const COLOR_CLASS_MAP: Record<SemanticColor, string> = {
  success: 'terminal-badge-success',
  warning: 'terminal-badge-warning',
  error: 'terminal-badge-error',
  info: 'terminal-badge-info',
  purple: 'terminal-badge-purple',
  electric: 'terminal-badge-electric',
  neutral: 'terminal-badge-neutral',
}

const DOT_COLOR_MAP: Record<SemanticColor, string> = {
  success: 'bg-[var(--status-success-mark)]',
  warning: 'bg-[var(--status-warning-mark)]',
  error: 'bg-[var(--status-error-mark)]',
  info: 'bg-[var(--status-info-mark)]',
  purple: 'bg-[var(--status-special-mark)]',
  electric: 'bg-[var(--accent-primary)]',
  neutral: 'bg-[var(--foreground-muted)]',
}

const SIZE_CLASSES = {
  xs: 'text-[10px] px-1.5 py-0.5',
  sm: 'text-[10px] px-2 py-0.5',
  md: 'text-[11px] px-2 py-0.5',
} as const
</script>

<template>
  <span
    :class="
      joinClasses(
        'terminal-badge inline-flex items-center gap-1.5',
        COLOR_CLASS_MAP[color],
        SIZE_CLASSES[size],
        pulse && !dot && 'animate-pulse-subtle',
      )
    "
  >
    <span
      v-if="dot"
      :class="
        joinClasses(
          'w-1.5 h-1.5 rounded-full shrink-0',
          DOT_COLOR_MAP[color],
          pulse && 'animate-pulse-subtle',
        )
      "
    />
    <slot>{{ label }}</slot>
  </span>
</template>
