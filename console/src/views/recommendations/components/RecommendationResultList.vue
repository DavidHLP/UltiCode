<!-- console/src/views/recommendations/components/RecommendationResultList.vue -->
<script setup lang="ts">

import { useI18n } from "vue-i18n";
import { DataTable, type ColumnDef } from "@/components/common/data-table";
import type { RecommendItem } from "@/types/recommendation";

const props = withDefaults(
  defineProps<{
    items: RecommendItem[];
    emptyLabel?: string;
  }>(),
  {
    emptyLabel: "",
  },
);

const emit = defineEmits<{
  "row-click": [item: RecommendItem];
}>();

const { t } = useI18n();

const columns: ColumnDef[] = [
  {
    key: "rank",
    header: "#",
    class: "w-12 text-center",
    headerClass: "text-center",
  },
  {
    key: "title",
    header: t("problem.table.title"),
    class: "min-w-0",
  },
  {
    key: "tags",
    header: t("problem.table.tags"),
    class: "w-48",
  },
  {
    key: "difficulty",
    header: t("problem.table.difficulty"),
    class: "w-24",
    headerClass: "text-center",
  },
  {
    key: "score",
    header: t("recommendation.card.score"),
    class: "w-20 text-right",
    headerClass: "text-right",
  },
];

const difficultyClass = (difficulty: string) => {
  switch (difficulty.toLowerCase()) {
    case "easy":
      return "text-[var(--terminal-green)]";
    case "medium":
      return "text-[var(--terminal-amber)]";
    case "hard":
      return "text-[var(--terminal-red)]";
    default:
      return "text-muted-foreground";
  }
};

const difficultyLabel = (difficulty: string) => {
  switch (difficulty.toLowerCase()) {
    case "easy":
      return t("problem.difficulty.easy");
    case "medium":
      return t("problem.difficulty.medium");
    case "hard":
      return t("problem.difficulty.hard");
    default:
      return difficulty;
  }
};

const scoreColor = (score: number) => {
  if (score >= 0.8) return "var(--terminal-green)";
  if (score >= 0.6) return "var(--terminal-amber)";
  return "var(--accent-primary)";
};
</script>

<template>
  <DataTable
    :data="items"
    :columns="columns"
    :empty-label="props.emptyLabel || t('recommendation.empty.daily')"
    @row-click="(item: RecommendItem) => emit('row-click', item)"
  >
    <template #cell-rank="{ item }">
      <span
        class="font-mono text-sm font-semibold tabular-nums"
        :class="
          items.findIndex((i) => i.problemId === item.problemId) < 3
            ? 'text-primary'
            : 'text-muted-foreground'
        "
      >
        {{ String(items.findIndex((i) => i.problemId === item.problemId) + 1).padStart(2, "0") }}
      </span>
    </template>

    <template #cell-title="{ item }">
      <div class="flex items-center gap-2 min-w-0">
        <span class="truncate text-sm font-medium text-foreground hover:text-primary cursor-pointer transition-colors">
          {{ item.title }}
        </span>
      </div>
    </template>

    <template #cell-tags="{ item }">
      <div class="flex flex-wrap gap-1">
        <span
          v-for="tag in item.tags.slice(0, 3)"
          :key="tag"
          class="terminal-badge terminal-badge-neutral text-[10px]"
        >
          {{ tag }}
        </span>
        <span v-if="item.tags.length > 3" class="text-[10px] text-muted-foreground">
          +{{ item.tags.length - 3 }}
        </span>
      </div>
    </template>

    <template #cell-difficulty="{ item }">
      <span
        :class="difficultyClass(item.difficulty)"
        class="font-mono text-xs uppercase tracking-wide"
      >
        {{ difficultyLabel(item.difficulty) }}
      </span>
    </template>

    <template #cell-score="{ item }">
      <span
        class="font-mono text-sm font-semibold tabular-nums"
        :style="{ color: scoreColor(item.score) }"
      >
        {{ (item.score * 100).toFixed(1) }}
      </span>
    </template>
  </DataTable>
</template>
