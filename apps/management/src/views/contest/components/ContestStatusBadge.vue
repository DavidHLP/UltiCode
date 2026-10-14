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
    bg: 'bg-accent',
    border: 'border-input',
    text: 'text-foreground',
    label: 'Draft',
  },
  published: {
    bg: 'bg-[color-mix(in_oklch,_var(--primary)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--primary)_40%,_transparent)]',
    text: 'text-primary',
    label: 'Published',
  },
  registering: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-success-mark)_40%,_transparent)]',
    text: 'text-foreground',
    label: 'Registering',
  },
  upcoming: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)]',
    text: 'text-foreground',
    label: 'Upcoming',
  },
  ongoing: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-error-mark)_40%,_transparent)]',
    text: 'text-foreground',
    label: 'Ongoing',
  },
  freezing: {
    bg: 'bg-[color-mix(in_oklch,_var(--status-special-mark)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--status-special-mark)_40%,_transparent)]',
    text: 'text-foreground',
    label: 'Freezing',
  },
  finished: {
    bg: 'bg-accent dark:bg-foreground',
    border: 'border-border dark:border-foreground',
    text: 'text-muted-foreground',
    label: 'Finished',
  },
  archived: {
    bg: 'bg-accent dark:bg-foreground',
    border: 'border-border dark:border-foreground',
    text: 'text-muted-foreground',
    label: 'Archived',
  },
}

const config = computed(() => statusConfig[props.status] || statusConfig.draft)

const sizeClasses = {
  sm: 'text-xs px-1.5 py-0.5',
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
        'inline-flex items-center gap-1.5 font-mono font-medium uppercase tracking-label border rounded-lg',
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
      :class="cn('rounded-full bg-status-error-mark animate-pulse', dotSizeClasses[props.size])"
    />
    {{ config.label }}
  </span>
</template>
