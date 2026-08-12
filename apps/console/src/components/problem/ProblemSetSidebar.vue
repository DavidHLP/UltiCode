<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  type DateValue,
  getLocalTimeZone,
  today,
} from "@internationalized/date";
import { Badge } from "@/components/ui/badge";
import { type Ref, ref, onMounted, computed } from "vue";
import { Trophy, ChevronDown, ChevronUp } from "lucide-vue-next";
import { fetchDailyActivity } from "@/api/submission";
import { useI18n } from "vue-i18n";
import { fetchProblemById } from "@/api/problem";
import type { Problem } from "@/types/problem";

const date = ref(today(getLocalTimeZone())) as Ref<DateValue>;
const completedDates = ref<string[]>([]);
const isCollapsed = ref(true);
const dailyProblem = ref<Problem | null>(null);

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value;
};

const getLocalDateString = (d: Date) => {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const weeklyDays = computed(() => {
  const list: {
    dateStr: string;
    dayName: string;
    dayNum: number;
    isCompleted: boolean;
    isToday: boolean;
  }[] = [];
  const tz = getLocalTimeZone();
  const todayVal = today(tz);

  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = getLocalDateString(d);

    const dayNames = ["日", "一", "二", "三", "四", "五", "六"];
    const dayName = dayNames[d.getDay()];

    list.push({
      dateStr,
      dayName,
      dayNum: d.getDate(),
      isCompleted: completedDates.value.includes(dateStr),
      isToday: dateStr === todayVal.toString(),
    });
  }
  return list;
});

const { t } = useI18n();
onMounted(async () => {
  try {
    dailyProblem.value = await fetchProblemById(7);
  } catch (e) {
    console.error("Failed to fetch daily problem", e);
    dailyProblem.value = {
      id: 7,
      title: "合并K个升序链表",
      difficulty: "HARD",
      slug: "merge-k-sorted-lists",
      acceptance_rate: 28.4,
      tags: [],
    } as unknown as Problem;
  }

  if (useAuthStore().isAuthenticated) {
    try {
      const year = new Date().getFullYear();
      completedDates.value = await fetchDailyActivity(year);
    } catch (e) {
      console.error("Failed to fetch daily activity", e);
    }
  }
});
</script>

<template>
  <div class="space-y-6">
    <!-- Calendar Widget -->
    <Card class="terminal-card">
      <CardHeader
        class="pb-3 cursor-pointer md:cursor-default select-none md:border-b md:border-border/40"
        :class="{ 'border-b border-border/40': !isCollapsed }"
        @click="toggleCollapse"
      >
        <CardTitle
          class="text-sm font-semibold flex items-center justify-between"
        >
          <div class="flex items-center gap-2 text-foreground">
            <Trophy
              class="w-4 h-4 text-[var(--status-warning-mark)] animate-pulse"
            />
            <span class="font-sans text-sm font-semibold text-foreground">{{
              t("problem.sidebar.dailyChallenge")
            }}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <Badge
              variant="outline"
              class="rounded-md border border-status-warning-mark bg-status-warning-surface px-2 py-0.5 text-xs font-mono text-foreground-strong"
            >
              <span
                class="mr-1.5 h-1.5 w-1.5 rounded-full bg-status-warning-mark"
              ></span>
              <span>{{ t("problem.sidebar.completed") }}</span>
              <span class="tabular-nums font-black text-foreground-strong text-xs px-0.5">{{
                completedDates.length
              }}</span>
              <span>{{ t("problem.sidebar.daysUnit") }}</span>
            </Badge>
            <button
              class="md:hidden p-1 text-muted-foreground hover:text-foreground transition-colors"
              aria-label="Toggle calendar"
            >
              <ChevronDown v-if="isCollapsed" class="w-4 h-4" />
              <ChevronUp v-else class="w-4 h-4" />
            </button>
          </div>
        </CardTitle>
        <!-- Today's Problem Subtitle on narrow screens -->
        <div
          v-if="dailyProblem"
          class="md:hidden mt-2 text-xxs font-mono text-muted-foreground/90 border-t border-border/20 pt-2 flex items-center gap-1 flex-wrap"
          @click.stop
        >
          <span class="text-[var(--foreground-strong)] font-bold">></span>
          <span>今日：{{ dailyProblem.id }}.</span>
          <RouterLink
            :to="`/problems/${dailyProblem.slug || 'merge-k-sorted-lists'}`"
            class="text-primary hover:underline font-bold"
          >
            {{ dailyProblem.title }}
          </RouterLink>
          <span
            class="ml-1 rounded-sm px-1 text-2xs font-bold uppercase tracking-tighter"
            :class="
              dailyProblem.difficulty === 'EASY'
                ? 'text-foreground-strong bg-[var(--status-success-mark)]/10'
                : dailyProblem.difficulty === 'MEDIUM'
                  ? 'text-foreground-strong bg-[var(--status-warning-mark)]/10'
                  : 'text-foreground-strong bg-[var(--status-error-mark)]/10'
            "
          >
            ({{
              dailyProblem.difficulty === "EASY"
                ? "简单"
                : dailyProblem.difficulty === "MEDIUM"
                  ? "中等"
                  : "困难"
            }})
          </span>
        </div>
      </CardHeader>
      <CardContent
        class="bg-card transition-all duration-300 ease-in-out md:max-h-none md:opacity-100 md:overflow-visible md:p-4 flex flex-col items-center"
        :class="[
          isCollapsed
            ? 'max-h-0 opacity-0 overflow-hidden p-0'
            : 'max-h-[180px] opacity-100 overflow-hidden p-3',
        ]"
      >
        <!-- Mobile Week View (visible only on mobile/narrow viewports) -->
        <div
          class="md:hidden w-full flex items-center justify-between gap-1.5 py-1"
        >
          <div
            v-for="day in weeklyDays"
            :key="day.dateStr"
            class="flex flex-1 flex-col items-center rounded-md border px-0.5 py-1"
            :class="
              day.isToday
                ? 'border-[var(--primary)] bg-[var(--primary)]/10 text-[var(--primary)]'
                : day.isCompleted
                  ? 'border-[var(--status-warning-mark)]/45 bg-status-warning-surface text-foreground-strong'
                  : 'border-border/30 bg-muted/10 text-muted-foreground/70'
            "
          >
            <span
              class="mb-1 rounded-sm text-2xs text-foreground font-mono"
            >
              {{ day.dayName }}
            </span>
            <span
              class="flex h-6 w-6 items-center justify-center rounded-md text-xs font-mono font-bold"
              :class="
                day.isToday
                  ? 'text-[var(--primary)] font-extrabold'
                  : ''
              "
            >
              {{ day.dayNum }}
            </span>
            <!-- Completed indicator dot -->
            <div
              class="mt-1 h-1.5 w-1.5 rounded-full"
              :class="
                day.isCompleted
                  ? 'bg-[var(--status-warning-mark)] shadow-[0_0_4px_var(--status-warning-mark)]'
                  : 'bg-transparent'
              "
            ></div>
          </div>
        </div>

        <!-- Desktop Calendar (visible only on desktop/tablets) -->
        <Calendar
          v-model="date"
          class="hidden md:block w-full rounded-lg border-0 p-0 [&_[data-slot=calendar-grid]]:w-full [&_[data-slot=calendar-grid]]:table-fixed [&_[data-slot=calendar-grid-row]]:w-full [&_[data-slot=calendar-head-cell]]:min-w-0 [&_[data-slot=calendar-head-cell]]:flex-1 [&_[data-slot=calendar-cell]]:min-w-0 [&_[data-slot=calendar-cell]]:flex-1 [&_[data-slot=calendar-cell-trigger]]:!h-auto [&_[data-slot=calendar-cell-trigger]]:!w-full [&_[data-slot=calendar-cell-trigger]]:aspect-square"
          :completed-dates="completedDates"
        />
      </CardContent>
    </Card>

    <!-- Session/Progress Widget Placeholder -->
  </div>
</template>
