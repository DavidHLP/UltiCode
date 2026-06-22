<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

// Generate mock data for the last 365 days
const props = defineProps<{
  data?: { date: string; level: number }[];
}>();

const { t } = useI18n();

const activityData = computed(() => {
  if (props.data && props.data.length > 0) {
    // Fill in missing days for the last year with 0
    const fullData: { date: string; level: number }[] = [];
    const today = new Date();
    // Start from 365 days ago, fill forward in chronological order
    // (oldest first → today last) so grid-flow-col renders left=old, right=new.
    for (let i = 364; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const dateStr = d.toISOString().split("T")[0];
      if (dateStr) {
        const existing = props.data.find((item) => item.date === dateStr);
        fullData.push({
          date: dateStr,
          level: existing ? existing.level : 0,
        });
      }
    }
    return fullData;
  }

  // Fallback if no data provided (e.g. loading or empty) - empty grid in
  // chronological order (oldest first). Without reverse(), the previous
  // implementation pushed today→i=364 then back, which grid-flow-col would
  // render in a confusing reversed date order.
  const emptyData: { date: string; level: number }[] = [];
  const today = new Date();
  for (let i = 364; i >= 0; i--) {
    const date = new Date(today);
    date.setDate(date.getDate() - i);
    const dateStr = date.toISOString().split("T")[0];
    if (dateStr) {
      emptyData.push({
        date: dateStr,
        level: 0,
      });
    }
  }

  return emptyData;
});

const getColorClass = (level: number) => {
  switch (level) {
    case 1:
      return "bg-[oklch(0.6444_0.1508_118.6/0.2)] dark:bg-[oklch(0.6444_0.1508_118.6/0.3)]";
    case 2:
      return "bg-[oklch(0.6444_0.1508_118.6/0.4)] dark:bg-[oklch(0.6444_0.1508_118.6/0.5)]";
    case 3:
      return "bg-[oklch(0.6444_0.1508_118.6/0.6)] dark:bg-[oklch(0.6444_0.1508_118.6/0.6)]";
    case 4:
      return "bg-[oklch(0.6444_0.1508_118.6/0.8)] dark:bg-[oklch(0.6444_0.1508_118.6)]";
    default:
      // Empty cell: use `bg-muted` instead of `bg-secondary/60` so the
      // placeholder square is visible in both light and dark themes.
      // `bg-secondary/60` collapses to near-background in the dark
      // theme (where --secondary is close to the page background),
      // making the heatmap appear to have no grid at all.
      return "bg-muted";
  }
};

const months = computed(() => [
  t("common.months.jan"),
  t("common.months.feb"),
  t("common.months.mar"),
  t("common.months.apr"),
  t("common.months.may"),
  t("common.months.jun"),
  t("common.months.jul"),
  t("common.months.aug"),
  t("common.months.sep"),
  t("common.months.oct"),
  t("common.months.nov"),
  t("common.months.dec"),
]);
</script>

<template>
  <div class="w-full overflow-x-auto pb-4">
    <div class="min-w-[800px] space-y-2">
      <!-- Months Header -->
      <div class="flex text-xs text-muted-foreground">
        <div v-for="month in months" :key="month" class="flex-1">
          {{ month }}
        </div>
      </div>

      <!-- Heatmap Grid -->
      <div class="flex gap-[3px]">
        <!-- We need to render columns (weeks). 365 days is approx 52 weeks -->
        <!-- This is a simplified rendering. A real calendar heatmap needs complex date math for exact alignment. -->
        <!-- For this mock, we'll just render a flex wrap of 365 boxes to look "cool" roughly. -->
        <!-- actually, grid is better for "weeks" columns. -->
        <div class="grid grid-flow-col grid-rows-7 gap-[3px]">
          <TooltipProvider v-for="(day, index) in activityData" :key="index">
            <Tooltip>
              <TooltipTrigger as-child>
                <div
                  class="h-3 w-3 rounded-none transition-colors hover:ring-2 hover:ring-ring hover:ring-offset-1"
                  :class="getColorClass(day.level)"
                ></div>
              </TooltipTrigger>
              <TooltipContent>
                <p>
                  {{ day.level === 0 ? t("common.labels.none") : day.level }}
                  {{ t("personal.profile.contributions") }} on
                  {{ day.date }}
                </p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      </div>

      <div class="flex items-center gap-2 text-xs text-muted-foreground">
        <span>{{ t("common.labels.less") }}</span>
        <div class="flex gap-[2px]">
          <div class="h-3 w-3 rounded-none bg-muted"></div>
          <div
            class="h-3 w-3 rounded-none bg-[oklch(0.6444_0.1508_118.6/0.2)] dark:bg-[oklch(0.6444_0.1508_118.6/0.3)]"
          ></div>
          <div
            class="h-3 w-3 rounded-none bg-[oklch(0.6444_0.1508_118.6/0.4)] dark:bg-[oklch(0.6444_0.1508_118.6/0.5)]"
          ></div>
          <div
            class="h-3 w-3 rounded-none bg-[oklch(0.6444_0.1508_118.6/0.6)] dark:bg-[oklch(0.6444_0.1508_118.6/0.7)]"
          ></div>
          <div
            class="h-3 w-3 rounded-none bg-[oklch(0.6444_0.1508_118.6/0.8)] dark:bg-[oklch(0.6444_0.1508_118.6)]"
          ></div>
        </div>
        <span>{{ t("common.labels.more") }}</span>
      </div>
    </div>
  </div>
</template>
