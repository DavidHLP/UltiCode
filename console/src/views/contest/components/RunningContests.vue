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
  const endTime = contest.end_time ?? contest.endTime;
  if (endTime) {
    const endMs = new Date(endTime).getTime();
    return Number.isNaN(endMs) ? null : endMs;
  }
  const startMs = new Date(contest.start_time).getTime();
  if (Number.isNaN(startMs)) return null;
  const durationMinutes = Number(
    contest.duration_minutes ?? contest.durationMinutes ?? 0,
  );
  if (!durationMinutes) return null;
  return startMs + durationMinutes * 60 * 1000;
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
  return t(`contest.types.${type}`);
}

function updateTimers() {
  const now = Date.now();

  props.contests.forEach((contest) => {
    const endMs = getContestEndTimeMs(contest);
    const startMs = new Date(contest.start_time).getTime();

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
        class="flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-[10px] font-bold uppercase tracking-widest text-rose-600"
      >
        <span class="h-2 w-2 rounded-full bg-rose-500 animate-pulse"></span>
        {{ t("contest.list.liveBadge") }}
      </div>
    </div>

    <div class="grid gap-6 lg:grid-cols-2">
      <Card
        v-for="contest in contests.slice(0, 2)"
        :key="contest.id"
        class="relative overflow-hidden border-0 text-white shadow-xl transition-transform hover:-translate-y-1"
      >
        <div
          class="absolute inset-0 bg-gradient-to-br from-[#1f2937] via-[#1f2937] to-[#f97316]"
        ></div>
        <div
          class="absolute -right-12 -top-12 h-36 w-36 rounded-full bg-white/10 blur-3xl"
        ></div>
        <CardContent class="relative z-10 p-6">
          <div class="space-y-5">
            <div class="flex items-start justify-between gap-4">
              <div class="space-y-1">
                <p
                  class="text-xs font-semibold uppercase tracking-widest text-white/70"
                >
                  {{
                    getContestTypeLabel(
                      contest.type || contest.contest_type || "weekly",
                    )
                  }}
                </p>
                <h3 class="text-2xl font-black leading-tight">
                  {{ contest.title }}
                </h3>
              </div>
              <div
                class="rounded-full bg-white/10 px-3 py-1 text-[10px] font-bold uppercase tracking-widest"
              >
                {{ t("contest.list.liveNow") }}
              </div>
            </div>

            <div class="grid gap-3 text-xs text-white/80 md:grid-cols-2">
              <div class="flex items-center gap-2">
                <Calendar class="h-4 w-4" />
                <span>{{ formatDateTime(contest.start_time) }}</span>
              </div>
              <div class="flex items-center gap-2">
                <Clock class="h-4 w-4" />
                <span
                  >{{ t("contest.list.remaining") }}
                  {{ getCountdown(contest.id) }}</span
                >
              </div>
              <div class="flex items-center gap-2">
                <Users class="h-4 w-4" />
                <span
                  >{{
                    contest.participant_count || contest.participantCount || 0
                  }}
                  {{ t("contest.detail.participants") }}</span
                >
              </div>
              <div class="flex items-center gap-2">
                <Clock class="h-4 w-4" />
                <span
                  >{{
                    contest.duration_minutes || contest.durationMinutes || 0
                  }}
                  {{ t("contest.time.min_short") }}</span
                >
              </div>
            </div>

            <div class="space-y-2">
              <div class="h-2 rounded-full bg-white/20 overflow-hidden">
                <div
                  class="h-full bg-white/80 transition-all duration-500"
                  :style="{ width: `${getProgress(contest.id)}%` }"
                ></div>
              </div>
              <div
                class="flex items-center justify-between text-[10px] uppercase tracking-widest text-white/70"
              >
                <span>{{ t("contest.list.liveProgress") }}</span>
                <span>{{ Math.round(getProgress(contest.id)) }}%</span>
              </div>
            </div>

            <div class="flex items-center justify-between">
              <div class="text-xs text-white/70">
                {{ t("contest.list.rated") }}
                {{
                  (contest.isRated ?? contest.is_rated)
                    ? t("common.labels.yes")
                    : t("common.labels.no")
                }}
              </div>
              <Button
                size="sm"
                class="h-9 rounded-full bg-white/20 text-white hover:bg-white/30"
                @click="
                  router.push({
                    name: 'contest-detail',
                    params: { contestId: contest.id },
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
