<script setup lang="ts">
/**
 * ContestTimer Component
 *
 * Shows countdown to contest end or start time.
 * Displays formatted time like "2d 5h 30m 45s"
 * Emits: finished, started
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { IconClock, IconHourglass, IconCheck } from '@tabler/icons-vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    endTime: string | Date
    startTime?: string | Date
    showIcon?: boolean
    variant?: 'default' | 'compact'
  }>(),
  {
    showIcon: true,
    variant: 'default',
  },
)

const emit = defineEmits<{
  finished: []
  started: []
}>()

const now = ref(new Date())
let interval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  interval = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onUnmounted(() => {
  if (interval) {
    clearInterval(interval)
  }
})

// Parse date helper
function parseDate(date: string | Date): Date {
  return typeof date === 'string' ? new Date(date) : date
}

// Computed time values
const endTimeMs = computed(() => parseDate(props.endTime).getTime())
const startTimeMs = computed(() => (props.startTime ? parseDate(props.startTime).getTime() : null))

const isStarted = computed(() => {
  if (!startTimeMs.value) return true
  return now.value.getTime() >= startTimeMs.value
})

const isFinished = computed(() => {
  return now.value.getTime() >= endTimeMs.value
})

const timeUntilStart = computed(() => {
  if (!startTimeMs.value) return 0
  return Math.max(0, startTimeMs.value - now.value.getTime())
})

const timeRemaining = computed(() => {
  return Math.max(0, endTimeMs.value - now.value.getTime())
})

// Format time helper
interface TimeComponents {
  days: number
  hours: number
  minutes: number
  seconds: number
}

function msToTimeComponents(ms: number): TimeComponents {
  const totalSeconds = Math.floor(ms / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return { days, hours, minutes, seconds }
}

const formattedTime = computed(() => {
  const ms = isStarted.value ? timeRemaining.value : timeUntilStart.value
  const { days, hours, minutes, seconds } = msToTimeComponents(ms)

  if (props.variant === 'compact') {
    if (days > 0) {
      return `${days}d ${hours}h`
    }
    if (hours > 0) {
      return `${hours}h ${minutes}m`
    }
    return `${minutes}m ${seconds}s`
  }

  // Default variant - full format
  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0 || days > 0) parts.push(`${hours}h`)
  parts.push(`${minutes}m`)
  parts.push(`${seconds}s`)

  return parts.join(' ')
})

// Label based on state
const label = computed(() => {
  if (isFinished.value) return 'Ended'
  if (!isStarted.value) return 'Starts in'
  return 'Time remaining'
})

// Icon based on state
const iconComponent = computed(() => {
  if (isFinished.value) return IconCheck
  if (!isStarted.value) return IconClock
  return IconHourglass
})

// Watch for state changes to emit events
watch(isStarted, (started, wasStarted) => {
  if (started && !wasStarted) {
    emit('started')
  }
})

watch(isFinished, (finished, wasFinished) => {
  if (finished && !wasFinished) {
    emit('finished')
  }
})
</script>

<template>
  <div
    :class="
      cn('inline-flex items-center gap-2', variant === 'default' ? 'flex-wrap' : 'flex-nowrap')
    "
  >
    <!-- Icon -->
    <component
      :is="iconComponent"
      v-if="showIcon"
      :class="
        cn(
          'h-4 w-4',
          isFinished
            ? 'text-[var(--silver-400)]'
            : !isStarted
              ? 'text-[var(--terminal-amber)]'
              : 'text-[var(--terminal-green)]',
        )
      "
    />

    <!-- Label and Time -->
    <div class="flex flex-col gap-0.5">
      <span
        v-if="variant === 'default'"
        class="font-data text-2xs uppercase tracking-wide text-[var(--silver-400)]"
      >
        {{ label }}
      </span>
      <span
        :class="
          cn(
            'font-data tabular-nums font-medium',
            variant === 'default' ? 'text-sm' : 'text-xs',
            isFinished
              ? 'text-[var(--silver-400)]'
              : !isStarted
                ? 'text-[var(--terminal-amber)]'
                : 'text-[var(--terminal-green)]',
          )
        "
      >
        {{ isFinished ? 'Contest ended' : formattedTime }}
      </span>
    </div>
  </div>
</template>
