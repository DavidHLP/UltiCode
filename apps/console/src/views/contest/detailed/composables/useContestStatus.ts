import { ref, computed, watch, onUnmounted, type Ref } from "vue";
import { useI18n } from "vue-i18n";
import type { ContestDetail } from "@/types/contest";

export function useContestStatus(
  contest: Ref<ContestDetail | null>,
  isRegistered: Ref<boolean>,
) {
  const { t } = useI18n();

  const statusCountdown = ref("00:00:00");
  const statusLabel = ref(t("contest.time.startsIn"));
  const statusHint = ref("");
  const statusProgress = ref(0);
  let statusIntervalId: number | null = null;

  const statusCardClass = computed(() => {
    const status = contest.value?.status;
    if (status === "RUNNING")
      return "border-l-4 border-l-[var(--status-error-mark)]";
    if (status === "UPCOMING")
      return "border-l-4 border-l-[var(--status-success-mark)]";
    return "border-l-4 border-l-muted-foreground";
  });

  const contestEndTime = computed(() => {
    const value = contest.value;
    if (!value) return "";
    const endTime = value.endTime;
    if (endTime) return endTime;
    const startMs = new Date(value.startTime).getTime();
    const duration = Number(value.duration ?? 0);
    if (Number.isNaN(startMs) || !duration) return "";
    return new Date(startMs + duration * 60 * 1000).toISOString();
  });

  function formatCountdown(totalSeconds: number): string {
    const seconds = Math.max(0, totalSeconds);
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (days > 0) {
      return `${days}${t("contest.time.days")} ${hours}${t("contest.time.hours")} ${minutes}${t("contest.time.minutes")}`;
    }
    return `${hours.toString().padStart(2, "0")}:${minutes
      .toString()
      .padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  }

  function getContestEndTimeMs(): number | null {
    const value = contest.value;
    if (!value) return null;
    const endTime = value.endTime;
    if (endTime) {
      const endMs = new Date(endTime).getTime();
      return Number.isNaN(endMs) ? null : endMs;
    }
    const startMs = new Date(value.startTime).getTime();
    if (Number.isNaN(startMs)) return null;
    const duration = Number(value.duration ?? 0);
    if (!duration) return null;
    return startMs + duration * 60 * 1000;
  }

  function updateStatusTimer() {
    const value = contest.value;
    if (!value) return;
    const startMs = new Date(value.startTime).getTime();
    const endMs = getContestEndTimeMs();
    const now = Date.now();

    if (value.status === "UPCOMING") {
      const remaining = Math.max(0, Math.floor((startMs - now) / 1000));
      statusLabel.value = t("contest.detail.notStarted");
      statusCountdown.value = formatCountdown(remaining);
      statusHint.value = isRegistered.value
        ? t("contest.detail.youAreRegistered")
        : t("contest.detail.registrationOpen");
      statusProgress.value = 0;
      return;
    }

    if (value.status === "RUNNING") {
      const remaining = Math.max(0, Math.floor(((endMs ?? now) - now) / 1000));
      const total = Math.max(1, Math.floor(((endMs ?? now) - startMs) / 1000));
      const elapsed = Math.min(total, Math.max(0, total - remaining));
      statusLabel.value = t("contest.detail.inProgress");
      statusCountdown.value = formatCountdown(remaining);
      statusHint.value = t("contest.detail.submissionsLive");
      statusProgress.value = Math.min(
        100,
        Math.max(0, (elapsed / total) * 100),
      );
      return;
    }

    statusLabel.value = t("contest.detail.ended");
    statusCountdown.value = t("contest.detail.resultsPublished");
    statusHint.value = t("contest.detail.replayHint");
    statusProgress.value = 100;
  }

  function formatDateTime(isoString: string): string {
    const date = new Date(isoString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hours = String(date.getHours()).padStart(2, "0");
    const minutes = String(date.getMinutes()).padStart(2, "0");
    return `${year}-${month}-${day} ${hours}:${minutes}`;
  }

  function getDifficultyColor(difficulty: string): string {
    const colors: Record<string, string> = {
      Easy: "text-foreground-strong",
      Medium: "text-foreground-strong",
      Hard: "text-foreground-strong",
    };
    return colors[difficulty] || "text-foreground-muted";
  }

  // Start timer when contest loads, clean up on unmount
  watch(
    contest,
    (value) => {
      if (!value) return;
      updateStatusTimer();
      if (statusIntervalId !== null) {
        clearInterval(statusIntervalId);
      }
      statusIntervalId = window.setInterval(updateStatusTimer, 1000);
    },
    { immediate: true },
  );

  onUnmounted(() => {
    if (statusIntervalId !== null) {
      clearInterval(statusIntervalId);
    }
  });

  return {
    statusCountdown,
    statusLabel,
    statusHint,
    statusProgress,
    statusCardClass,
    contestEndTime,
    formatDateTime,
    getDifficultyColor,
  };
}
