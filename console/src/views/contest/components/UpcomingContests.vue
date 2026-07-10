<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from "vue";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Calendar, Clock, Trophy } from "lucide-vue-next";
import { useRouter } from "vue-router";
import type { ContestListItem } from "@/types/contest";
import { formatDateTime, getDurationMinutes } from "@/shared/datetime-utils/src";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  contests: ContestListItem[];
}>();

const router = useRouter();
const { t } = useI18n();
const countdowns = ref<Map<string, string>>(new Map());
let intervalId: number | null = null;

// Calculate countdown for all contests
function updateCountdowns() {
  const now = new Date().getTime();

  props.contests.forEach((contest) => {
    const start = new Date(contest.startTime).getTime();
    const diff = start - now;

    if (diff <= 0) {
      countdowns.value.set(contest.id, t("contest.status.started"));
      return;
    }

    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    const countdown =
      days > 0
        ? t("contest.time.countdown_full", {
            d: days,
            h: hours,
            m: minutes,
            s: seconds,
          })
        : t("contest.time.countdown_short", {
            h: hours,
            m: minutes,
            s: seconds,
          });

    countdowns.value.set(contest.id, countdown);
  });
}

// Get countdown for specific contest
function getCountdown(contestId: string): string {
  return countdowns.value.get(contestId) || t("contest.status.calculating");
}

// Watch for contest changes
watch(
  () => props.contests,
  () => {
    updateCountdowns();
  },
  { immediate: true },
);

onMounted(() => {
  // Initial update
  updateCountdowns();
  // Start interval to update countdowns every second
  intervalId = window.setInterval(updateCountdowns, 1000);
});

onUnmounted(() => {
  if (intervalId !== null) {
    clearInterval(intervalId);
  }
});
</script>

<template>
  <section v-if="contests.length > 0" class="space-y-5" data-contests>
    <div class="flex items-center justify-between">
      <div class="space-y-1">
        <h2 class="text-2xl font-bold tracking-tight">
          {{ t("contest.list.upcoming") }}
        </h2>
        <p class="text-sm text-muted-foreground">
          {{ t("contest.list.subtitle") }}
        </p>
      </div>
    </div>

    <div class="grid gap-6 md:grid-cols-2">
      <Card
        v-for="(contest, index) in contests.slice(0, 2)"
        :key="contest.id"
        class="group relative cursor-pointer overflow-hidden rounded-none border border-border bg-card text-foreground transition-all hover:-translate-x-0.5 hover:-translate-y-0.5 active:translate-x-0 active:translate-y-0 shadow-[3px_3px_0px_0px_var(--border)] hover:shadow-[4px_4px_0px_0px_var(--border)]"
        :class="
          index === 0
            ? 'border-l-4 border-l-[var(--terminal-cyan)]'
            : 'border-l-4 border-l-[var(--terminal-green)]'
        "
        @click="
          router.push({
            name: 'contest-detail',
            params: { slug: contest.slug },
          })
        "
      >
        <CardContent class="p-6 relative z-10">
          <div class="space-y-5">
            <div class="flex justify-between items-start gap-4">
              <div class="space-y-2">
                <span
                  class="inline-flex items-center px-2.5 py-0.5 text-xs font-mono font-bold tracking-wider uppercase border border-border bg-muted text-muted-foreground rounded-none"
                >
                  {{
                    t(
                      `contest.types.${contest.contestType || "weekly"}`,
                      contest.contestType || "weekly",
                    )
                  }}
                </span>
                <h3
                  class="text-xl font-bold leading-tight group-hover:text-primary transition-colors"
                >
                  {{ contest.title }}
                </h3>
              </div>
              <div class="p-2 bg-muted/50 border border-border shrink-0">
                <Trophy class="w-8 h-8 text-[var(--terminal-amber)]" />
              </div>
            </div>

            <div
              class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm text-muted-foreground border-t border-b border-dashed py-3 font-mono"
            >
              <div class="flex items-center gap-2">
                <Calendar class="h-4 w-4 shrink-0 text-muted-foreground/80" />
                <span class="truncate">{{
                  formatDateTime(contest.startTime)
                }}</span>
              </div>
              <div class="flex items-center gap-2">
                <Clock class="h-4 w-4 shrink-0 text-muted-foreground/80" />
                <span>
                  {{ getDurationMinutes(contest.startTime, contest.endTime) }}
                  {{ t("contest.time.min_short") }}
                </span>
              </div>
            </div>

            <div
              class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2"
            >
              <div
                class="text-xs font-mono text-muted-foreground flex items-center gap-1.5"
              >
                <span
                  class="h-2 w-2 bg-[var(--terminal-cyan)] animate-pulse inline-block"
                ></span>
                <span>{{ t("contest.list.startsIn") }}</span>
                <span
                  class="font-bold text-foreground text-sm bg-muted/65 px-1.5 py-0.5 border border-border"
                  >{{ getCountdown(contest.id) }}</span
                >
              </div>
              <Button
                variant="outline"
                size="sm"
                class="rounded-none border-border shadow-[2px_2px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 text-xs self-end sm:self-auto"
                @click.stop
              >
                <Calendar class="mr-2 h-3.5 w-3.5" />
                {{ t("contest.list.addToCalendar") }}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </section>
</template>
