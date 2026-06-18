<script setup lang="ts">
/**
 * FirstSolveNotification Component
 *
 * Toast notification for first solve achievements.
 * Shows: username, problemIndex, timeSpent
 * Auto-dismiss after 5 seconds.
 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { IconTrophy, IconX } from '@tabler/icons-vue'
import { cn } from '@/lib/utils'

export interface FirstSolveData {
  username: string
  problemIndex: string
  timeSpent: number
  contestId?: string
}

const props = withDefaults(
  defineProps<{
    visible: boolean
    data: FirstSolveData | null
    autoDismiss?: number
  }>(),
  {
    autoDismiss: 5000,
  },
)

const emit = defineEmits<{
  close: []
}>()

const isAnimating = ref(false)
const isLeaving = ref(false)
let dismissTimer: ReturnType<typeof setTimeout> | null = null

// Format time spent
function formatTimeSpent(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (mins > 0) {
    return `${mins}m ${secs}s`
  }
  return `${secs}s`
}

// Start auto-dismiss timer
function startDismissTimer() {
  if (props.autoDismiss > 0) {
    dismissTimer = setTimeout(() => {
      close()
    }, props.autoDismiss)
  }
}

// Clear timer
function clearDismissTimer() {
  if (dismissTimer) {
    clearTimeout(dismissTimer)
    dismissTimer = null
  }
}

// Close with animation
function close() {
  clearDismissTimer()
  isLeaving.value = true
  setTimeout(() => {
    emit('close')
    isLeaving.value = false
  }, 300)
}

// Watch for visibility changes
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      isAnimating.value = true
      startDismissTimer()
    } else {
      clearDismissTimer()
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (props.visible) {
    isAnimating.value = true
    startDismissTimer()
  }
})

onUnmounted(() => {
  clearDismissTimer()
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="translate-x-full opacity-0"
      enter-to-class="translate-x-0 opacity-100"
      leave-active-class="transition-all duration-300 ease-in"
      leave-from-class="translate-x-0 opacity-100"
      leave-to-class="translate-x-full opacity-0"
    >
      <div
        v-if="visible && data"
        :class="
          cn(
            'fixed top-4 right-4 z-50 max-w-sm',
            'border border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)]',
            'bg-gradient-to-r from-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] to-[color-mix(in_oklch,_var(--terminal-cyan)_15%,_transparent)]',
            'backdrop-blur-sm',
            'rounded-none shadow-lg',
            'p-4',
            isLeaving && 'opacity-0 translate-x-full',
          )
        "
      >
        <div class="flex items-start gap-3">
          <!-- Trophy icon with animation -->
          <div
            class="flex-shrink-0 w-10 h-10 rounded-full bg-[color-mix(in_oklch,_var(--terminal-amber)_30%,_transparent)] flex items-center justify-center"
          >
            <IconTrophy class="h-5 w-5 text-[var(--terminal-amber)] animate-bounce-subtle" />
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span
                class="font-data text-2xs uppercase tracking-wide text-[var(--terminal-amber)]"
              >
                First Solve
              </span>
            </div>
            <p class="mt-1 text-sm font-medium text-foreground">
              <span class="text-[var(--accent-electric)]">{{ data.username }}</span>
              solved problem
              <span class="font-data font-bold text-[var(--terminal-amber)]">
                {{ data.problemIndex }}
              </span>
            </p>
            <p class="mt-0.5 text-xs text-[var(--silver-400)]">
              Solved in {{ formatTimeSpent(data.timeSpent) }}
            </p>
          </div>

          <!-- Close button -->
          <button
            class="flex-shrink-0 p-1 rounded-none hover:bg-[var(--silver-200)] dark:hover:bg-[var(--silver-700)] transition-colors"
            @click="close"
          >
            <IconX class="h-4 w-4 text-[var(--silver-400)]" />
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
@keyframes bounce-subtle {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
}

.animate-bounce-subtle {
  animation: bounce-subtle 1s ease-in-out infinite;
}
</style>
