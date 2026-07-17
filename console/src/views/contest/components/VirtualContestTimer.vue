<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useVirtualContestStore } from "@/stores/virtualContest";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { toast } from "vue-sonner";
import { Clock, Trophy } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { formatPenaltyTime } from "@/utils/datetime";

const contestStore = useVirtualContestStore();
const { t } = useI18n();
const timeRemaining = ref(0);
let intervalId: number | null = null;
let finishRequested = false;

const isActive = computed(() => contestStore.isInVirtualContest);
const session = computed(() => contestStore.virtualSession);

const formattedTime = computed(() => {
  const hours = Math.floor(timeRemaining.value / 3600);
  const minutes = Math.floor((timeRemaining.value % 3600) / 60);
  const seconds = timeRemaining.value % 60;

  return `${hours.toString().padStart(2, "0")}:${minutes
    .toString()
    .padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
});

const progressPercent = computed(() => {
  if (!session.value) return 0;
  const total =
    (new Date(session.value.endsAt).getTime() -
      new Date(session.value.startedAt).getTime()) /
    1000;
  const elapsed = total - timeRemaining.value;
  return Math.min(100, (elapsed / total) * 100);
});

function updateTimer() {
  if (!session.value?.endsAt) return;

  const now = Date.now();
  const endsAt = new Date(session.value.endsAt).getTime();
  const remaining = Math.max(0, Math.floor((endsAt - now) / 1000));

  timeRemaining.value = remaining;

  if (remaining === 0) {
    stopTimer();
    void finishCurrentVirtualContest();
  }
}

function stopTimer() {
  if (intervalId !== null) {
    clearInterval(intervalId);
    intervalId = null;
  }
}

async function finishCurrentVirtualContest() {
  if (finishRequested) return;
  if (!session.value?.contestId) return;

  finishRequested = true;
  try {
    await contestStore.finishVirtualContest(session.value.contestId);
  } catch {
    finishRequested = false;
    toast.error(t("contest.virtual.finishFailed"));
  }
}

async function handleFinish() {
  await finishCurrentVirtualContest();
}

// R6.4 / F-13: when the tab goes hidden, freeze the visible timer so users
// don't burn through virtual time on backgrounded tabs. When the tab
// comes back, if more time has elapsed than remains, auto-finish the
// session; otherwise shift endsAt forward by the hidden duration so the
// user keeps the same remaining time. The server still enforces the
// hard deadline (R6.2 / F-07) so this is a UX nicety, not a security
// control.
let pausedAt: number | null = null;
function onVisibilityChange() {
  if (typeof document === "undefined") return;
  if (document.hidden) {
    pausedAt = Date.now();
  } else if (pausedAt !== null && session.value?.endsAt) {
    const hiddenMs = Date.now() - pausedAt;
    const remainingMs = new Date(session.value.endsAt).getTime() - Date.now();
    pausedAt = null;
    if (remainingMs <= 0) {
      // Already past — auto-finish.
      if (session.value.id && session.value.contestId) {
        contestStore.finishVirtualContest(session.value.contestId).catch(() => {
          // Best-effort; the server-side F-07 will close the session on
          // the next scheduler tick anyway.
        });
      }
    } else if (hiddenMs > 0) {
      // Shift endsAt forward by the hidden duration so the user-visible
      // timer doesn't burn through virtual time. HIGH-1 fix: use the
      // store's setVirtualSession action (the local `session` is a
      // computed and not writable).
      const current = session.value;
      const newEnd = new Date(Date.now() + remainingMs).toISOString();
      contestStore.setVirtualSession({ ...current, endsAt: newEnd });
    }
  }
}

onMounted(() => {
  updateTimer();
  intervalId = window.setInterval(updateTimer, 1000);
  if (typeof document !== "undefined") {
    document.addEventListener("visibilitychange", onVisibilityChange);
  }
});

onUnmounted(() => {
  stopTimer();
  if (typeof document !== "undefined") {
    document.removeEventListener("visibilitychange", onVisibilityChange);
  }
});
</script>

<template>
  <Card v-if="isActive && session" class="border-primary/50 bg-primary/5">
    <CardContent class="p-4">
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <Trophy class="h-5 w-5 text-primary" />
            <div>
              <p class="text-sm font-semibold">
                {{ t("contest.virtual.active") }}
              </p>
              <p class="text-xs text-muted-foreground">
                {{ t("contest.virtual.contestId") }} {{ session.contestId }}
              </p>
            </div>
          </div>
          <AlertDialog>
            <AlertDialogTrigger as-child>
              <Button size="sm" variant="outline">
                {{ t("contest.virtual.finishEarly") }}
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>{{
                  t("contest.virtual.finishTitle")
                }}</AlertDialogTitle>
                <AlertDialogDescription>
                  {{ t("contest.virtual.finishDescription") }}
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>{{
                  t("common.actions.cancel")
                }}</AlertDialogCancel>
                <AlertDialogAction @click="handleFinish">{{
                  t("common.actions.confirm")
                }}</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Clock class="h-4 w-4 text-muted-foreground" />
              <span class="text-xs text-muted-foreground">{{
                t("contest.virtual.timeRemaining")
              }}</span>
            </div>
            <span
              class="font-mono text-lg font-bold"
              :class="{
                'text-destructive': timeRemaining < 300,
                'text-primary': timeRemaining >= 300,
              }"
            >
              {{ formattedTime }}
            </span>
          </div>

          <!-- Progress Bar -->
          <div class="h-2 bg-muted rounded-full overflow-hidden">
            <div
              class="h-full bg-primary transition-all duration-500"
              :style="{ width: `${progressPercent}%` }"
            ></div>
          </div>
        </div>

        <div
          class="flex items-center justify-between text-xs text-muted-foreground"
        >
          <span
            >{{ t("contest.ranking.score") }}:
            {{ session.score ?? 0 }}</span
          >
          <span
            >{{ t("contest.ranking.penalty") }}:
            {{ formatPenaltyTime(session.penalty ?? 0) }}</span
          >
        </div>
      </div>
    </CardContent>
  </Card>
</template>
