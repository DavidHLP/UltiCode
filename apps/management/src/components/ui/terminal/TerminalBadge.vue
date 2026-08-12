<script setup lang="ts">
/**
 * TerminalBadge - Status badge with terminal colors and optional pulse animation
 *
 * A badge component for displaying status information with terminal-style
 * monospace typography and color-coded variants.
 */
import { cn } from '@/lib/utils'

type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'default'

interface Props {
  variant?: BadgeVariant
  pulse?: boolean
  label: string
}

withDefaults(defineProps<Props>(), {
  variant: 'default',
  pulse: false,
})

const variantClasses: Record<BadgeVariant, string> = {
  success: 'terminal-badge-success',
  warning: 'terminal-badge-warning',
  error: 'terminal-badge-error',
  info: 'terminal-badge-info',
  default:
    'bg-[var(--surface-highlight)] text-[var(--foreground-strong)] border border-[var(--border-subtle)] dark:bg-[var(--foreground-strong)] dark:text-[var(--border-subtle)] dark:border-[var(--foreground-strong)]',
}
</script>

<template>
  <span
    :class="
      cn(
        'terminal-badge inline-flex items-center gap-1.5',
        variantClasses[variant],
        pulse && 'animate-pulse-subtle',
      )
    "
  >
    <span
      v-if="pulse"
      class="w-1.5 h-1.5"
      :class="
        cn(
          variant === 'success' && 'bg-[var(--status-success-mark)]',
          variant === 'warning' && 'bg-[var(--status-warning-mark)]',
          variant === 'error' && 'bg-[var(--status-error-mark)]',
          variant === 'info' && 'bg-[var(--status-info-mark)]',
          variant === 'default' && 'bg-[var(--foreground-muted)]',
        )
      "
    />
    {{ label }}
  </span>
</template>
