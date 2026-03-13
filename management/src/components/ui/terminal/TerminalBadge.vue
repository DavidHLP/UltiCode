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
    'bg-[var(--silver-100)] text-[var(--silver-700)] border border-[var(--silver-200)] dark:bg-[var(--silver-800)] dark:text-[var(--silver-300)] dark:border-[var(--silver-600)]',
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
          variant === 'success' && 'bg-[var(--terminal-green)]',
          variant === 'warning' && 'bg-[var(--terminal-amber)]',
          variant === 'error' && 'bg-[var(--terminal-red)]',
          variant === 'info' && 'bg-[var(--terminal-cyan)]',
          variant === 'default' && 'bg-[var(--silver-500)]',
        )
      "
    />
    {{ label }}
  </span>
</template>
