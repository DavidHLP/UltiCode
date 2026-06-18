<script setup lang="ts">
import { computed } from "vue";
import type { HeatmapPoint } from "@/types/userStats";
import { cn } from "@/lib/utils";

const props = defineProps<{
  data: HeatmapPoint[];
  title?: string;
}>();

const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

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

const levelColors = [
  "bg-muted",
  "bg-[oklch(0.6444_0.1508_118.6/0.2)] dark:bg-[oklch(0.6444_0.1508_118.6/0.3)]",
  "bg-[oklch(0.6444_0.1508_118.6/0.4)] dark:bg-[oklch(0.6444_0.1508_118.6/0.5)]",
  "bg-[oklch(0.6444_0.1508_118.6/0.6)] dark:bg-[oklch(0.6444_0.1508_118.6/0.6)]",
  "bg-[oklch(0.6444_0.1508_118.6/0.8)] dark:bg-[oklch(0.6444_0.1508_118.6)]",
];

const levelLabels = [
  "No activity",
  "1-2 submissions",
  "3-5 submissions",
  "6-9 submissions",
  "10+ submissions",
];

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function getActivityCount(level: number): string {
  return levelLabels[level] || "No activity";
}
</script>

<template>
  <div class="space-y-2">
    <h3 v-if="title" class="text-sm font-medium">{{ title }}</h3>

    <div class="overflow-x-auto">
      <div class="inline-flex gap-0.5">
        <!-- Day labels -->
        <div class="mr-1 flex flex-col gap-0.5">
          <span
            v-for="(day, i) in days"
            :key="day"
            class="flex h-3 items-center justify-end text-2xs text-muted-foreground"
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
                  'h-3 w-3 rounded-none transition-colors',
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
      class="flex items-center justify-end gap-1 text-2xs text-muted-foreground"
    >
      <span>Less</span>
      <div
        v-for="(color, i) in levelColors"
        :key="i"
        :class="cn('h-3 w-3 rounded-none', color)"
      />
      <span>More</span>
    </div>
  </div>
</template>
