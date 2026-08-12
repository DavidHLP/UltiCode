<script setup lang="ts">
/**
 * TerminalCard - Sharp corners card with comment-style header
 *
 * A bold tech-minimalist card component following the Terminal Precision design.
 * Features zero border-radius, monospace headers, and industrial aesthetic.
 */
import { cn } from '@/lib/utils'

interface Props {
  title?: string
  variant?: 'default' | 'elevated' | 'sunken'
  showSeparator?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'default',
  showSeparator: false,
})
</script>

<template>
  <div
    :class="
      cn(
        'terminal-card overflow-hidden',
        variant === 'elevated' && 'shadow-float',
        variant === 'sunken' && 'bg-[var(--surface-sunken)]',
      )
    "
  >
    <div v-if="title" class="terminal-card-header flex items-center gap-2">
      <span class="text-[var(--foreground-muted)]">//</span>
      <span>{{ title }}</span>
    </div>
    <div class="p-4">
      <slot />
    </div>
    <div v-if="showSeparator" class="terminal-separator mx-4" />
    <div
      v-if="$slots.footer"
      class="border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] p-4 bg-[var(--surface-sunken)]"
    >
      <slot name="footer" />
    </div>
  </div>
</template>
