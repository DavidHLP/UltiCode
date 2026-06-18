<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from "vue";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Calendar, Clock, PlayCircle, Users } from "lucide-vue-next";
import { useRouter } from "vue-router";
import type { ContestListItem } from "@/types/contest";
import { formatDateTime } from "@/utils/date";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  contests: ContestListItem[];
}>();

const router = useRouter();
const { t } = useI18n();
const countdowns = ref<Map<string, string>>(new Map());
const progress = ref<Map<string, number>>(new Map());
let intervalId: number | null = null;

function getContestEndTimeMs(contest: ContestListItem): number | null {
  const endTime = contest.endTime;
  if (endTime) {
    const endMs = new Date(endTime).getTime();
    return Number.isNaN(endMs) ? null : endMs;
  }
  const startMs = new Date(contest.startTime).getTime();
  if (Number.isNaN(startMs)) return null;
  const duration = Number(contest.duration ?? 0);
  if (!duration) return null;
  return startMs + duration * 60 * 1000;
}

function formatCountdown(seconds: number): string {
  const remaining = Math.max(0, seconds);
  const hours = Math.floor(remaining / 3600);
  const minutes = Math.floor((remaining % 3600) / 60);
  const secs = remaining % 60;
  return `${hours.toString().padStart(2, "0")}:${minutes
    .toString()
    .padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
}

function getContestTypeLabel(type: string): string {
  return t(`contest.types.${type}`, type);
}

function updateTimers() {
  const now = Date.now();

  props.contests.forEach((contest) => {
    const endMs = getContestEndTimeMs(contest);
    const startMs = new Date(contest.startTime).getTime();

    if (!endMs || Number.isNaN(startMs)) {
      countdowns.value.set(contest.id, t("contest.status.tbd"));
      progress.value.set(contest.id, 0);
      return;
    }

    const remaining = Math.max(0, Math.floor((endMs - now) / 1000));
    const total = Math.max(1, Math.floor((endMs - startMs) / 1000));
    const percent = Math.min(
      100,
      Math.max(0, ((total - remaining) / total) * 100),
    );

    progress.value.set(contest.id, percent);
    countdowns.value.set(
      contest.id,
      remaining === 0 ? t("contest.status.ended") : formatCountdown(remaining),
    );
  });
}

function getCountdown(contestId: string): string {
  return countdowns.value.get(contestId) || t("common.status.loading");
}

function getProgress(contestId: string): number {
  return progress.value.get(contestId) ?? 0;
}

watch(
  () => props.contests,
  () => {
    updateTimers();
  },
  { immediate: true },
);

onMounted(() => {
  updateTimers();
  intervalId = window.setInterval(updateTimers, 1000);
});

onUnmounted(() => {
  if (intervalId !== null) {
    clearInterval(intervalId);
  }
});
</script>

<template>
  <section v-if="contests.length > 0" class="space-y-5">
    <div class="flex items-center justify-between">
      <div class="space-y-1">
        <h2 class="text-2xl font-bold tracking-tight">
          {{ t("contest.list.live") }}
        </h2>
        <p class="text-sm text-muted-foreground">
          {{ t("contest.list.liveSubtitle") }}
        </p>
      </div>
      <div
        class="flex items-center gap-2 rounded-none border border-[var(--terminal-red)] bg-[var(--terminal-red)]/10 px-3 py-1 text-2xs font-bold uppercase tracking-widest text-[var(--terminal-red)]"
      >
        <span
          class="h-2 w-2 rounded-none bg-[var(--terminal-red)] animate-pulse"
        ></span>
        {{ t("contest.list.liveBadge") }}
      </div>
    </div>

    <div class="grid gap-6 lg:grid-cols-2">
      <Card
        v-for="contest in contests.slice(0, 2)"
        :key="contest.id"
        class="group relative overflow-hidden rounded-none border border-border border-l-4 border-l-[var(--terminal-red)] bg-card text-foreground transition-all hover:-translate-x-0.5 hover:-translate-y-0.5 active:translate-x-0 active:translate-y-0 shadow-[3px_3px_0px_0px_var(--border)] hover:shadow-[4px_4px_0px_0px_var(--border)]"
      >
        <CardContent class="relative z-10 p-6">
          <div class="space-y-5">
            <div class="flex items-start justify-between gap-4">
              <div class="space-y-2">
                <span
                  class="inline-flex items-center px-2.5 py-0.5 text-xs font-mono font-bold tracking-wider uppercase border border-border bg-muted text-muted-foreground rounded-none"
                >
                  {{ getContestTypeLabel(contest.contestType || "weekly") }}
                </span>
                <h3
                  class="text-xl font-bold leading-tight group-hover:text-primary transition-colors"
                >
                  {{ contest.title }}
                </h3>
              </div>
              <div
                class="rounded-none border border-[var(--terminal-red)] bg-[var(--terminal-red)]/10 px-2.5 py-1 text-2xs font-bold uppercase tracking-wider text-[var(--terminal-red)] flex items-center gap-1.5 shrink-0"
              >
                <span
                  class="h-1.5 w-1.5 bg-[var(--terminal-red)] animate-pulse inline-block"
                ></span>
                {{ t("contest.list.liveNow") }}
              </div>
            </div>

            <div
              class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm text-muted-foreground border-t border-b border-dashed py-3 font-mono"
            >
              <div class="flex items-center gap-2">
                <Calendar class="h-4 w-4 shrink-0 text-muted-foreground/80" />
                <span>{{ formatDateTime(contest.startTime) }}</span>
              </div>
              <div class="flex items-center gap-2">
                <Clock class="h-4 w-4 shrink-0 text-muted-foreground/80" />
                <span
                  >{{ t("contest.list.remaining") }}
                  <span class="font-bold text-foreground">{{
                    getCountdown(contest.id)
                  }}</span></span
                >
              </div>
              <div class="flex items-center gap-2">
                <Users class="h-4 w-4 shrink-0 text-muted-foreground/80" />
                <span
                  >{{ contest.participantCount || 0 }}
                  {{ t("contest.detail.participants") }}</span
                >
              </div>
              <div class="flex items-center gap-2">
                <Clock class="h-4 w-4 shrink-0 text-muted-foreground/80" />
                <span
                  >{{ contest.duration || 0 }}
                  {{ t("contest.time.min_short") }}</span
                >
              </div>
            </div>

            <div class="space-y-2">
              <div
                class="h-2 rounded-none bg-muted border border-border overflow-hidden"
              >
                <div
                  class="h-full bg-[var(--terminal-red)] transition-all duration-500"
                  :style="{ width: `${getProgress(contest.id)}%` }"
                ></div>
              </div>
              <div
                class="flex items-center justify-between text-2xs font-mono uppercase tracking-widest text-muted-foreground"
              >
                <span>{{ t("contest.list.liveProgress") }}</span>
                <span class="font-bold text-foreground"
                  >{{ Math.round(getProgress(contest.id)) }}%</span
                >
              </div>
            </div>

            <div class="flex items-center justify-between gap-3 pt-2">
              <div class="text-xs font-mono text-muted-foreground">
                {{ t("contest.list.rated") }}
                <span class="font-bold text-foreground">
                  {{
                    contest.isRated
                      ? t("common.labels.yes")
                      : t("common.labels.no")
                  }}
                </span>
              </div>
              <Button
                size="sm"
                class="rounded-none border border-border bg-[var(--terminal-red)] text-white hover:bg-[var(--terminal-red)]/90 shadow-[2px_2px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 text-xs h-9 px-4 font-bold"
                @click="
                  router.push({
                    name: 'contest-detail',
                    params: { slug: contest.slug },
                  })
                "
              >
                <PlayCircle class="mr-2 h-4 w-4" />
                {{ t("contest.detail.enterContest") }}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </section>
</template>
