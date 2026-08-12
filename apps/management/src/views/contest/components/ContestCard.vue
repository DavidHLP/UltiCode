<script setup lang="ts">
/**
 * ContestCard Component
 *
 * Card component showing contest info including:
 * - Title, type, status badge, timer
 * - Participant count
 * - Register button (for upcoming contests)
 */
import { computed } from 'vue'
import { IconUsers, IconTrophy, IconCalendar } from '@tabler/icons-vue'
import { cn } from '@/lib/utils'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import ContestStatusBadge, { type ContestUiStatus } from './ContestStatusBadge.vue'
import ContestTimer from './ContestTimer.vue'
import { formatDateTime } from '@/lib/format/date'

export interface ContestCardData {
  id: string
  slug: string
  title: string
  description?: string
  type: 'PUBLIC' | 'PRIVATE' | 'VIRTUAL'
  startTime: string | Date
  endTime: string | Date
  status: ContestUiStatus
  participantCount: number
  isRegistered?: boolean
}

const props = defineProps<{
  contest: ContestCardData
  showRegisterButton?: boolean
}>()

const emit = defineEmits<{
  click: [contest: ContestCardData]
  register: [contest: ContestCardData]
}>()

// Map API type to display label
const typeLabels: Record<string, string> = {
  PUBLIC: 'Public',
  PRIVATE: 'Private',
  VIRTUAL: 'Virtual',
}

// Determine if registration is available
const canRegister = computed(() => {
  return (
    props.showRegisterButton &&
    (props.contest.status === 'upcoming' || props.contest.status === 'registering') &&
    !props.contest.isRegistered
  )
})

// Format start time for display
const formattedStartTime = computed(() => formatDateTime(props.contest.startTime))

function handleClick() {
  emit('click', props.contest)
}

function handleRegister(event: Event) {
  event.stopPropagation()
  emit('register', props.contest)
}
</script>

<template>
  <Card
    :class="
      cn(
        'group cursor-pointer transition-all duration-200',
        'hover:border-[var(--primary)] hover:shadow-md',
        'border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]',
        'bg-card',
      )
    "
    @click="handleClick"
  >
    <div class="p-4 space-y-3">
      <!-- Header: Title and Type Badge -->
      <div class="flex items-start justify-between gap-3">
        <div class="flex-1 min-w-0">
          <h3
            class="font-medium text-sm text-foreground truncate group-hover:text-[var(--primary)] transition-colors"
          >
            {{ contest.title }}
          </h3>
          <p class="font-data text-xs text-[var(--foreground-muted)] truncate mt-0.5">
            {{ contest.slug }}
          </p>
        </div>
        <div class="flex items-center gap-2 shrink-0">
          <!-- Type badge -->
          <span
            :class="
              cn(
                'font-data text-2xs font-medium uppercase tracking-label',
                'px-2 py-0.5 border rounded-none',
                contest.type === 'PUBLIC'
                  ? 'bg-[color-mix(in_oklch,_var(--status-info-mark)_15%,_transparent)] border-[color-mix(in_oklch,_var(--status-info-mark)_40%,_transparent)] text-foreground-strong'
                  : contest.type === 'PRIVATE'
                    ? 'bg-[color-mix(in_oklch,_var(--primary)_15%,_transparent)] border-[color-mix(in_oklch,_var(--primary)_40%,_transparent)] text-[var(--primary)]'
                    : 'bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)] text-foreground-strong',
              )
            "
          >
            {{ typeLabels[contest.type] }}
          </span>
        </div>
      </div>

      <!-- Status Badge and Timer -->
      <div class="flex items-center justify-between gap-3">
        <ContestStatusBadge :status="contest.status" show-icon size="sm" />
        <ContestTimer
          v-if="contest.status !== 'finished' && contest.status !== 'archived'"
          :end-time="contest.endTime"
          :start-time="contest.startTime"
          :show-icon="false"
          variant="compact"
        />
      </div>

      <!-- Description (truncated) -->
      <p
        v-if="contest.description"
        class="text-xs text-[var(--foreground-muted)] line-clamp-2 min-h-[2rem]"
      >
        {{ contest.description }}
      </p>

      <!-- Footer: Schedule, Participants, Actions -->
      <div
        class="flex items-center justify-between pt-2 border-t border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]"
      >
        <!-- Schedule and Participants -->
        <div class="flex items-center gap-4">
          <!-- Start time -->
          <div class="flex items-center gap-1.5 text-[var(--foreground-muted)]">
            <IconCalendar class="h-3.5 w-3.5" />
            <span class="font-data text-xs tabular-nums">{{ formattedStartTime }}</span>
          </div>

          <!-- Participant count -->
          <div class="flex items-center gap-1.5 text-[var(--foreground-muted)]">
            <IconUsers class="h-3.5 w-3.5" />
            <span class="font-data text-xs tabular-nums">{{ contest.participantCount }}</span>
          </div>
        </div>

        <!-- Register button -->
        <Button
          v-if="canRegister"
          variant="terminal"
          size="sm"
          class="h-7 text-xs"
          @click="handleRegister"
        >
          <IconTrophy class="h-3.5 w-3.5" />
          Register
        </Button>
        <span
          v-else-if="contest.isRegistered"
          class="font-data text-xs text-foreground-strong"
        >
          Registered
        </span>
      </div>
    </div>
  </Card>
</template>
