<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { HeatmapPoint } from "@/types/userStats";
import { cn } from "@/lib/utils";
import { formatDate } from "@/utils/datetime";

const props = defineProps<{
  data: HeatmapPoint[];
  title?: string;
}>();

const { t, locale } = useI18n();
const days = computed(() => {
  const formatter = new Intl.DateTimeFormat(locale.value, {
    weekday: "short",
    timeZone: "UTC",
  });
  return Array.from({ length: 7 }, (_, day) =>
    formatter.format(new Date(Date.UTC(2024, 0, 7 + day))),
  );
});

// Generate last 365 days of data
const heatmapGrid = computed(() => {
  const today = new Date();
  const grid: { date: string; level: number; dayOfYear: number }[][] = [];

  // Start from 52 weeks ago (364 days)
  const startDate = new Date(today);
  startDate.setDate(startDate.getDate() - 364);

  // Align to the start of the week (Sunday)
  const dayOfWeek = startDate.getDay();
  startDate.setDate(startDate.getDate() - dayOfWeek);

  const dataMap = new Map(props.data.map((d) => [d.date, d.level]));

  for (let week = 0; week < 53; week++) {
    const weekData: { date: string; level: number; dayOfYear: number }[] = [];
    for (let day = 0; day < 7; day++) {
      const date = new Date(startDate);
      date.setDate(date.getDate() + week * 7 + day);
      const dateStr = date.toISOString().split("T")[0] ?? "";
      const dayOfYear = Math.floor(
        (date.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24),
      );

      weekData.push({
        date: dateStr,
        level: dateStr ? (dataMap.get(dateStr) ?? 0) : 0,
        dayOfYear,
      });
    }
    grid.push(weekData);
  }

  return grid;
});

// Activity magnitude uses a single semantic ramp in both color modes.
const levelColors = [
  "bg-[color-mix(in_oklch,var(--card)_85%,var(--foreground))] dark:bg-[color-mix(in_oklch,var(--card)_40%,var(--foreground))]",
  "bg-[color-mix(in_oklch,var(--status-success-mark)_20%,transparent)] dark:bg-[color-mix(in_oklch,var(--status-success-mark)_30%,transparent)]",
  "bg-[color-mix(in_oklch,var(--status-success-mark)_40%,transparent)] dark:bg-[color-mix(in_oklch,var(--status-success-mark)_50%,transparent)]",
  "bg-[color-mix(in_oklch,var(--status-success-mark)_60%,transparent)]",
  "bg-[color-mix(in_oklch,var(--status-success-mark)_80%,transparent)] dark:bg-[color-mix(in_oklch,var(--status-success-mark)_100%,transparent)]",
];

const levelLabels = computed(() => [
  t("personal.dashboard.heatmap.noActivity"),
  t("personal.dashboard.heatmap.levelOne"),
  t("personal.dashboard.heatmap.levelTwo"),
  t("personal.dashboard.heatmap.levelThree"),
  t("personal.dashboard.heatmap.levelFour"),
]);

function getActivityCount(level: number): string {
  return levelLabels.value[level] || t("personal.dashboard.heatmap.noActivity");
}
</script>

<template>
  <div class="space-y-2">
    <h3 v-if="title" class="text-sm font-medium">{{ title }}</h3>

    <div class="overflow-x-auto">
      <div class="flex w-max gap-0.5">
        <!-- Day labels -->
        <div class="mr-1 flex shrink-0 flex-col gap-0.5">
          <span
            v-for="(day, i) in days"
            :key="day"
            class="flex h-3 items-center justify-end whitespace-nowrap text-xs leading-none text-muted-foreground"
            :class="{ invisible: i % 2 === 1 }"
          >
            {{ day }}
          </span>
        </div>

        <!-- Heatmap grid -->
        <div class="flex gap-0.5">
          <div
            v-for="(week, weekIndex) in heatmapGrid"
            :key="weekIndex"
            class="flex flex-col gap-0.5"
          >
            <div
              v-for="(cell, dayIndex) in week"
              :key="`${weekIndex}-${dayIndex}`"
              :class="
                cn(
                  'h-3 w-3 rounded-lg transition-colors',
                  levelColors[cell.level],
                )
              "
              :title="`${formatDate(cell.date)}: ${getActivityCount(cell.level)}`"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Legend -->
    <div
      class="flex items-center justify-end gap-1 text-xs text-muted-foreground"
    >
      <span>{{ t("common.labels.less") }}</span>
      <div
        v-for="(color, i) in levelColors"
        :key="i"
        :class="cn('h-3 w-3 rounded-lg', color)"
      />
      <span>{{ t("common.labels.more") }}</span>
    </div>
  </div>
</template>
