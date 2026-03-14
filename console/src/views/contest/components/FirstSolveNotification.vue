<script setup lang="ts">
/**
 * FirstSolveNotification - Toast notification for first solve achievements
 *
 * Shows a celebratory notification when a participant achieves first solve
 * on a problem. Auto-dismisses after 5 seconds.
 */
import { ref, watch, onMounted, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import { Trophy, X } from "lucide-vue-next";
import type { FirstSolvePayload } from "@/composables/contest";

const props = defineProps<{
  /** First solve data */
  notification: FirstSolvePayload | null;
  /** Auto dismiss timeout in milliseconds */
  autoDismissTimeout?: number;
}>();

const emit = defineEmits<{
  (e: "dismiss"): void;
}>();

const { t } = useI18n();
const isVisible = ref(false);
const progress = ref(100);
let dismissTimer: number | null = null;
let progressTimer: number | null = null;

const timeout = props.autoDismissTimeout ?? 5000;

// Show notification when data changes
watch(
  () => props.notification,
  (newNotification) => {
    if (newNotification) {
      showNotification();
    }
  }
);

function showNotification() {
  isVisible.value = true;
  progress.value = 100;
  startDismissTimer();
}

function dismiss() {
  isVisible.value = false;
  clearTimers();
  emit("dismiss");
}

function startDismissTimer() {
  clearTimers();

  // Progress animation
  const startTime = Date.now();
  progressTimer = window.setInterval(() => {
    const elapsed = Date.now() - startTime;
    progress.value = Math.max(0, 100 - (elapsed / timeout) * 100);

    if (progress.value <= 0) {
      clearTimers();
    }
  }, 50);

  // Auto dismiss
  dismissTimer = window.setTimeout(() => {
    dismiss();
  }, timeout);
}

function clearTimers() {
  if (dismissTimer !== null) {
    clearTimeout(dismissTimer);
    dismissTimer = null;
  }
  if (progressTimer !== null) {
    clearInterval(progressTimer);
    progressTimer = null;
  }
}

// Cleanup on unmount
onUnmounted(() => {
  clearTimers();
});
</script>

<template>
  <Transition
    enter-active-class="transition ease-out duration-300"
    enter-from-class="translate-y-4 opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transition ease-in duration-200"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="translate-y-4 opacity-0"
  >
    <div
      v-if="isVisible && notification"
      class="fixed bottom-4 right-4 z-50 max-w-sm w-full"
    >
      <div
        class="bg-gradient-to-r from-yellow-400 via-amber-400 to-yellow-500 rounded-lg shadow-lg overflow-hidden"
      >
        <!-- Content -->
        <div class="p-4 flex items-start gap-3">
          <!-- Trophy icon -->
          <div class="shrink-0">
            <div class="h-10 w-10 bg-white/20 rounded-full flex items-center justify-center">
              <Trophy class="h-5 w-5 text-white" />
            </div>
          </div>

          <!-- Text content -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="text-white font-bold text-sm uppercase tracking-wide">
                {{ t("contest.firstSolve.title", "First Solve!") }}
              </span>
              <span class="text-white/80 text-xs font-medium">
                {{ notification.problemTitle || notification.problemId }}
              </span>
            </div>
            <p class="text-white/90 text-sm mt-1 truncate">
              <span class="font-semibold">{{ notification.username }}</span>
              {{ t("contest.firstSolve.solved", "solved the problem first!") }}
            </p>
          </div>

          <!-- Close button -->
          <button
            class="shrink-0 text-white/70 hover:text-white transition-colors"
            @click="dismiss"
          >
            <X class="h-4 w-4" />
          </button>
        </div>

        <!-- Progress bar -->
        <div class="h-1 bg-white/20">
          <div
            class="h-full bg-white/50 transition-all ease-linear"
            :style="{ width: `${progress}%` }"
          />
        </div>
      </div>
    </div>
  </Transition>
</template>
