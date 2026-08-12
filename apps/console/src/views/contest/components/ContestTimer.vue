<script setup lang="ts">
/**
 * ContestTimer - Countdown timer for contests
 *
 * Shows time remaining (days, hours, minutes, seconds) and updates every second.
 * Emits events when contest starts/ends.
 *
 * @emits start - Emitted when contest starts (time reaches 0 from positive)
 * @emits end - Emitted when contest ends (time reaches 0 during contest)
 */
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Clock } from "lucide-vue-next";

const props = defineProps<{
  /** Target time (ISO string or Date) */
  targetTime: string | Date;
  /** Whether counting down to start (true) or end (false) */
  isCountdownToStart?: boolean;
  /** Show icon */
  showIcon?: boolean;
  /** Compact mode - only show time without labels */
  compact?: boolean;
  /** Size variant */
  size?: "sm" | "md" | "lg";
}>();

const emit = defineEmits<{
  (e: "start"): void;
  (e: "end"): void;
}>();

const { t } = useI18n();
const timeRemaining = ref(0);
let intervalId: number | null = null;
const previousTimeRemaining = ref(0);

// Computed time components
const days = computed(() => Math.floor(timeRemaining.value / 86400));
const hours = computed(() => Math.floor((timeRemaining.value % 86400) / 3600));
const minutes = computed(() => Math.floor((timeRemaining.value % 3600) / 60));
const seconds = computed(() => timeRemaining.value % 60);

// Check if timer has completed
const isComplete = computed(() => timeRemaining.value <= 0);

// Size classes
const sizeClasses = computed(() => {
  switch (props.size) {
    case "sm":
      return "text-sm";
    case "lg":
      return "text-2xl";
    default:
      return "text-lg";
  }
});

// Formatted time display
const formattedTime = computed(() => {
  if (isComplete.value) {
    return props.isCountdownToStart
      ? t("contest.status.started", "Started")
      : t("contest.status.ended", "Ended");
  }

  const parts: string[] = [];

  if (days.value > 0) {
    if (props.compact) {
      parts.push(`${days.value}d`);
    } else {
      parts.push(
        t("contest.time.countdown_full", {
          d: days.value,
          h: hours.value.toString().padStart(2, "0"),
          m: minutes.value.toString().padStart(2, "0"),
          s: seconds.value.toString().padStart(2, "0"),
        }),
      );
      return parts[0];
    }
  }

  if (props.compact) {
    parts.push(
      `${hours.value.toString().padStart(2, "0")}:${minutes.value.toString().padStart(2, "0")}:${seconds.value.toString().padStart(2, "0")}`,
    );
  } else if (days.value === 0) {
    parts.push(
      t("contest.time.countdown_short", {
        h: hours.value.toString().padStart(2, "0"),
        m: minutes.value.toString().padStart(2, "0"),
        s: seconds.value.toString().padStart(2, "0"),
      }),
    );
  }

  return parts.join(" ");
});

// Color based on remaining time
const timerColor = computed(() => {
  if (isComplete.value) return "text-muted-foreground";
  if (timeRemaining.value < 300) return "text-destructive"; // < 5 minutes
  if (timeRemaining.value < 3600) return "text-[var(--foreground-strong)]"; // < 1 hour
  return "text-primary";
});

function updateTimer() {
  const now = Date.now();
  const target =
    typeof props.targetTime === "string"
      ? new Date(props.targetTime).getTime()
      : props.targetTime.getTime();

  previousTimeRemaining.value = timeRemaining.value;
  timeRemaining.value = Math.max(0, Math.floor((target - now) / 1000));

  // Emit events when timer reaches 0
  if (timeRemaining.value === 0 && previousTimeRemaining.value > 0) {
    if (props.isCountdownToStart) {
      emit("start");
    } else {
      emit("end");
    }
  }
}

// Watch for target time changes
watch(
  () => props.targetTime,
  () => {
    updateTimer();
  },
  { immediate: true },
);

onMounted(() => {
  updateTimer();
  intervalId = window.setInterval(updateTimer, 1000);
});

onUnmounted(() => {
  if (intervalId !== null) {
    clearInterval(intervalId);
  }
});
</script>

<template>
  <div
    class="inline-flex items-center gap-2 font-mono"
    :class="[sizeClasses, timerColor]"
  >
    <Clock v-if="showIcon" class="h-4 w-4" />
    <span class="font-semibold">
      {{ formattedTime }}
    </span>
  </div>
</template>
