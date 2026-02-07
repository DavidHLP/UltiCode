<script setup lang="ts">
import { computed } from "vue";
import type { Problem } from "@/types/problem";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { DonutChart } from "@/components/ui/chart-donut";
import { useI18n } from "vue-i18n";

interface Props {
  problems: Problem[];
}

const props = defineProps<Props>();
const { t } = useI18n();

const statusCategories = ["Solved", "Attempted", "Todo"] as const;
const statusColorTokens = {
  Solved: "--chart-status-solved",
  Attempted: "--chart-status-attempted",
  Todo: "--chart-status-todo",
};

const statusColorScale = computed(() =>
  statusCategories.map((label) => `hsl(var(${statusColorTokens[label]}))`),
);

const statusDistribution = computed(() => {
  const derived = props.problems.reduce(
    (acc, problem) => {
      const status = problem.status || "todo";
      if (status === "solved") acc.solved += 1;
      else if (status === "attempted") acc.attempted += 1;
      else acc.todo += 1;
      return acc;
    },
    { solved: 0, attempted: 0, todo: 0 },
  );
  return [
    { label: t("problem.status.solved"), value: derived.solved },
    { label: t("problem.status.attempted"), value: derived.attempted },
    { label: t("problem.status.todo"), value: derived.todo },
  ];
});

const donutData = computed(() =>
  statusDistribution.value.map((item) => ({
    name: item.label,
    total: item.value,
  })),
);

const difficultyStats = computed(() => {
  const buckets = ["Easy", "Medium", "Hard"] as const;
  return buckets.map((difficulty) => {
    const total = props.problems.filter(
      (p) => p.difficulty === difficulty,
    ).length;
    const solved = props.problems.filter(
      (p) => p.difficulty === difficulty && p.status === "solved",
    ).length;
    const percentage = total > 0 ? (solved / total) * 100 : 0;

    // Determine color class based on difficulty
    let colorClass = "bg-green-500";
    if (difficulty === "Medium") colorClass = "bg-yellow-500";
    if (difficulty === "Hard") colorClass = "bg-red-500";

    return {
      difficulty,
      label: t(`problem.difficulty.${difficulty.toLowerCase()}`),
      total,
      solved,
      percentage,
      colorClass,
    };
  });
});

const progressPercentage = computed(() => {
  const total = props.problems.length;
  if (total === 0) return 0;
  const solved = props.problems.filter((p) => p.status === "solved").length;
  return Math.round((solved / total) * 100);
});

const donutValueFormatter = (tick: number | Date) =>
  typeof tick === "number" ? `${tick}` : "";
</script>

<template>
  <div class="space-y-6">
    <!-- Progress Overview -->
    <Card class="border-none shadow-md bg-gradient-to-br from-card to-muted/20">
      <CardHeader class="pb-2">
        <CardTitle class="text-lg font-semibold tracking-tight">{{
          t("problem.problemList.analytics.progress")
        }}</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm text-muted-foreground">{{
            t("problem.problemList.analytics.overallCompletion")
          }}</span>
          <span class="text-xl font-bold">{{ progressPercentage }}%</span>
        </div>
        <Progress :model-value="progressPercentage" class="h-2 mb-6" />

        <div class="h-[200px] w-full flex items-center justify-center">
          <DonutChart
            class="h-full w-full"
            index="name"
            :category="'total'"
            :data="donutData"
            :value-formatter="donutValueFormatter"
            :colors="statusColorScale"
            :show-legend="true"
          />
        </div>
      </CardContent>
    </Card>

    <!-- Difficulty Breakdown -->
    <Card class="border-none shadow-md">
      <CardHeader class="pb-4">
        <CardTitle class="text-lg font-semibold tracking-tight">{{
          t("problem.problemList.analytics.difficulty")
        }}</CardTitle>
      </CardHeader>
      <CardContent class="space-y-5">
        <div
          v-for="stat in difficultyStats"
          :key="stat.difficulty"
          class="space-y-1.5"
        >
          <div class="flex justify-between text-sm font-medium">
            <span
              :class="{
                'text-green-600 dark:text-green-400':
                  stat.difficulty === 'Easy',
                'text-yellow-600 dark:text-yellow-400':
                  stat.difficulty === 'Medium',
                'text-red-600 dark:text-red-400': stat.difficulty === 'Hard',
              }"
              >{{ stat.label }}</span
            >
            <span class="text-muted-foreground">
              <span class="text-foreground">{{ stat.solved }}</span> /
              {{ stat.total }}
            </span>
          </div>
          <Progress
            :model-value="stat.percentage"
            class="h-1.5 bg-muted"
            :indicator-class="stat.colorClass"
          />
        </div>
      </CardContent>
    </Card>
  </div>
</template>
