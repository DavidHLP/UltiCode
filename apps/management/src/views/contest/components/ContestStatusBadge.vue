<script setup lang="ts">
/**
 * ContestStatusBadge Component
 *
 * Displays contest status with color-coded badges.
 * Variants: draft (gray), published (blue), registering (green),
 * upcoming (yellow), ongoing (red), freezing (purple), finished/archived (gray)
 */
import { computed } from 'vue'
import { cn } from '@/lib/utils'

export type ContestUiStatus =
  | 'draft'
  | 'published'
  | 'registering'
  | 'upcoming'
  | 'ongoing'
  | 'freezing'
  | 'finished'
  | 'archived'

const props = withDefaults(
  defineProps<{
    status: ContestUiStatus
    showIcon?: boolean
    size?: 'sm' | 'md' | 'lg'
  }>(),
  {
    showIcon: false,
    size: 'md',
  },
)

const statusConfig: Record<
  ContestUiStatus,
  { bg: string; border: string; text: string; label: string }
> = {
  draft: {
    bg: 'bg-surface-highlight',
    border: 'border-border-control',
    text: 'text-foreground-strong',
    label: 'Draft',
  },
  published: {
    bg: 'bg-[color-mix(in_oklch,_var(--primary)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--primary)_40%,_transparent)]',
    text: 'text-[var(--primary)]',
    label: 'Published',
  },
  registering: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-success-mark)_40%,_transparent)]',
    text: 'text-foreground-strong',
    label: 'Registering',
  },
  upcoming: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)]',
    text: 'text-foreground-strong',
    label: 'Upcoming',
  },
  ongoing: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-error-mark)_40%,_transparent)]',
    text: 'text-foreground-strong',
    label: 'Ongoing',
  },
  freezing: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-special-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-special-mark)_40%,_transparent)]',
    text: 'text-foreground-strong',
    label: 'Freezing',
  },
  finished: {
    bg: 'bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)]',
    border: 'border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]',
    text: 'text-[var(--foreground-muted)]',
    label: 'Finished',
  },
  archived: {
    bg: 'bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)]',
    border: 'border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]',
    text: 'text-[var(--foreground-muted)]',
    label: 'Archived',
  },
}

const config = computed(() => statusConfig[props.status] || statusConfig.draft)

const sizeClasses = {
  sm: 'text-2xs px-1.5 py-0.5',
  md: 'text-xs px-2 py-0.5',
  lg: 'text-sm px-2.5 py-1',
}

const dotSizeClasses = {
  sm: 'h-1.5 w-1.5',
  md: 'h-2 w-2',
  lg: 'h-2.5 w-2.5',
}

const showAnimatedDot = computed(() => props.showIcon && props.status === 'ongoing')
</script>

<template>
  <span
    :class="
      cn(
        'inline-flex items-center gap-1.5 font-data font-medium uppercase tracking-label border rounded-none',
        config.bg,
        config.border,
        config.text,
        sizeClasses[props.size],
      )
    "
  >
    <!-- Animated red dot for ongoing contests -->
    <span
      v-if="showAnimatedDot"
      :class="cn('rounded-full bg-[var(--status-error-mark)] animate-pulse', dotSizeClasses[props.size])"
    />
    {{ config.label }}
  </span>
</template>
