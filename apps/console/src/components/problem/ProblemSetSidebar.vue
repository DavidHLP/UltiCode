<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";

import { useAuthStore } from "@/stores/auth";
import { Calendar } from "@ulticode/design-system/ui/calendar";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import {
  type DateValue,
  getLocalTimeZone,
  today,
} from "@internationalized/date";
import { Badge } from "@ulticode/design-system/ui/badge";
import { type Ref, ref, onMounted, computed } from "vue";
import { Trophy, ChevronDown, ChevronUp } from "@lucide/vue";
import { fetchDailyActivity } from "@/api/submission";
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";
import { fetchRandomProblem } from "@/api/problem";
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

    const dayName = new Intl.DateTimeFormat(locale.value, {
      weekday: "short",
    }).format(d);

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

const { t, locale } = useI18n();
onMounted(async () => {
  try {
    dailyProblem.value = await fetchRandomProblem();
  } catch {
    dailyProblem.value = null;
  }

  if (useAuthStore().isAuthenticated) {
    try {
      const year = new Date().getFullYear();
      completedDates.value = await fetchDailyActivity(year);
    } catch {
      // Ignore background activity fetch failure on initial load
    }
  }
});
</script>

<template>
  <div class="space-y-6">
    <!-- Calendar Widget -->
    <Card>
      <CardHeader
        class="pb-3 cursor-pointer @3xl/problemset:cursor-default select-none @3xl/problemset:border-b @3xl/problemset:border-border/40"
        :class="{ 'border-b border-border/40': !isCollapsed }"
        @click="toggleCollapse"
      >
        <CardTitle class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2 text-foreground">
            <Trophy class="w-4 h-4 text-status-warning-mark animate-pulse" />
            <span class="font-sans text-sm font-semibold text-foreground">{{
              t("problem.sidebar.dailyChallenge")
            }}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <Badge variant="outline" class="px-2 py-0.5">
              <span
                class="mr-1.5 h-1.5 w-1.5 rounded-full bg-status-warning-mark"
              ></span>
              <span>{{ t("problem.sidebar.completed") }}</span>
              <span
                class="tabular-nums font-black text-foreground text-xs px-0.5"
                >{{ completedDates.length }}</span
              >
              <span>{{
                t("problem.sidebar.daysUnit", completedDates.length)
              }}</span>
            </Badge>
            <Button
              variant="ghost"
              class="@3xl/problemset:hidden p-1"
              :aria-label="t('problem.sidebar.toggleCalendar')"
              :aria-expanded="!isCollapsed"
            >
              <ChevronDown v-if="isCollapsed" class="w-4 h-4" />
              <ChevronUp v-else class="w-4 h-4" />
            </Button>
          </div>
        </CardTitle>
        <!-- Today's Problem Subtitle on narrow screens -->
        <div
          v-if="dailyProblem"
          class="@3xl/problemset:hidden mt-2 text-xs font-mono text-muted-foreground/90 border-t border-border/20 pt-2 flex items-center gap-1 flex-wrap"
          @click.stop
        >
          <span class="text-foreground font-bold">></span>
          <span>{{
            t("problem.sidebar.todayProblem", { id: dailyProblem.id })
          }}</span>
          <RouterLink
            :to="`/problems/${dailyProblem.slug || dailyProblem.id}`"
            class="text-primary hover:underline font-bold"
          >
            {{ dailyProblem.title }}
          </RouterLink>
          <span
            class="ml-1 rounded-sm px-1 text-xs font-bold uppercase tracking-tighter"
            :class="
              dailyProblem.difficulty === 'EASY'
                ? 'text-foreground bg-status-success-mark/10'
                : dailyProblem.difficulty === 'MEDIUM'
                  ? 'text-foreground bg-status-warning-mark/10'
                  : 'text-foreground bg-status-error-mark/10'
            "
          >
            ({{
              t(`problem.difficulty.${dailyProblem.difficulty.toLowerCase()}`)
            }})
          </span>
        </div>
      </CardHeader>
      <CardContent
        class="ease-in-out @3xl/problemset:max-h-none @3xl/problemset:opacity-100 @3xl/problemset:overflow-visible @3xl/problemset:p-4 flex flex-col items-center"
        :class="[
          isCollapsed
            ? 'max-h-0 opacity-0 overflow-hidden p-0'
            : 'max-h-[180px] opacity-100 overflow-hidden p-3',
        ]"
      >
        <!-- Mobile Week View (visible only on mobile/narrow viewports) -->
        <div
          class="@3xl/problemset:hidden w-full flex items-center justify-between gap-1.5 py-1"
        >
          <div
            v-for="day in weeklyDays"
            :key="day.dateStr"
            class="flex flex-1 flex-col items-center rounded-md border px-0.5 py-1"
            :class="
              day.isToday
                ? 'border-primary bg-primary/10 text-primary'
                : day.isCompleted
                  ? 'border-status-warning-mark/45 bg-status-warning-surface text-foreground'
                  : 'border-border/30 bg-muted/10 text-muted-foreground/70'
            "
          >
            <span class="mb-1 rounded-sm text-xs text-foreground font-mono">
              {{ day.dayName }}
            </span>
            <span
              class="flex h-6 w-6 items-center justify-center rounded-md text-xs font-mono font-bold"
              :class="day.isToday ? 'text-primary font-extrabold' : ''"
            >
              {{ day.dayNum }}
            </span>
            <!-- Completed indicator dot -->
            <div
              class="mt-1 h-1.5 w-1.5 rounded-full"
              :class="
                day.isCompleted
                  ? 'bg-status-warning-mark shadow-[0_0_4px_var(--status-warning-mark)]'
                  : 'bg-transparent'
              "
            ></div>
          </div>
        </div>

        <!-- Desktop Calendar (visible only on desktop/tablets) -->
        <Calendar
          v-model="date"
          :locale="locale"
          class="hidden @3xl/problemset:block w-full p-0 [&_[data-slot=calendar-grid]]:w-full [&_[data-slot=calendar-grid]]:table-fixed [&_[data-slot=calendar-grid-row]]:w-full [&_[data-slot=calendar-head-cell]]:min-w-0 [&_[data-slot=calendar-head-cell]]:flex-1 [&_[data-slot=calendar-cell]]:min-w-0 [&_[data-slot=calendar-cell]]:flex-1 [&_[data-slot=calendar-cell-trigger]]:!h-auto [&_[data-slot=calendar-cell-trigger]]:!w-full [&_[data-slot=calendar-cell-trigger]]:aspect-square"
          :completed-dates="completedDates"
        />
      </CardContent>
    </Card>

    <!-- Session/Progress Widget Placeholder -->
  </div>
</template>
