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
    bg: 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
    border: 'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
    text: 'text-[var(--silver-500)]',
    label: 'Draft',
  },
  published: {
    bg: 'bg-[color-mix(in_oklch,_var(--accent-electric)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--accent-electric)_40%,_transparent)]',
    text: 'text-[var(--accent-electric)]',
    label: 'Published',
  },
  registering: {
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--terminal-green)_40%,_transparent)]',
    text: 'text-[var(--terminal-green)]',
    label: 'Registering',
  },
  upcoming: {
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)]',
    text: 'text-[var(--terminal-amber)]',
    label: 'Upcoming',
  },
  ongoing: {
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--terminal-red)_40%,_transparent)]',
    text: 'text-[var(--terminal-red)]',
    label: 'Ongoing',
  },
  freezing: {
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-purple)_15%,_transparent)]',
    border: 'border-[color-mix(in_oklch,_var(--terminal-purple)_40%,_transparent)]',
    text: 'text-[var(--terminal-purple)]',
    label: 'Freezing',
  },
  finished: {
    bg: 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
    border: 'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
    text: 'text-[var(--silver-500)]',
    label: 'Finished',
  },
  archived: {
    bg: 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
    border: 'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
    text: 'text-[var(--silver-500)]',
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
      :class="cn('rounded-full bg-[var(--terminal-red)] animate-pulse', dotSizeClasses[props.size])"
    />
    {{ config.label }}
  </span>
</template>
