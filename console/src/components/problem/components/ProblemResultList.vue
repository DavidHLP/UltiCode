<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Video, Lock, Trash2 } from "lucide-vue-next";
import { DataTable, type ColumnDef } from "@/components/common/data-table";
import { useI18n } from "vue-i18n";
import type { Problem } from "@/types/problem";
import type { EnrichedProblem } from "../composables/useProblemExplorer";

const { t } = useI18n();

defineProps<{
  displayedProblems: EnrichedProblem[];
  columns: ColumnDef[];
  hasMore: boolean;
  editable: boolean;
}>();

const emit = defineEmits<{
  "load-more": [];
  remove: [problem: Problem];
  "row-click": [problem: EnrichedProblem];
}>();

const difficultyBadgeClass = (difficulty: Problem["difficulty"]) => {
  switch (difficulty) {
    case "EASY":
      return "bg-[oklch(0.6444_0.1508_118.6_/_0.1)] text-[var(--terminal-green)] border border-[oklch(0.6444_0.1508_118.6_/_0.2)]";
    case "MEDIUM":
      return "bg-[oklch(0.6545_0.1340_85.7_/_0.1)] text-[var(--terminal-amber)] border border-[oklch(0.6545_0.1340_85.7_/_0.2)]";
    case "HARD":
      return "bg-[oklch(0.5863_0.2064_27.1_/_0.1)] text-[var(--terminal-red)] border border-[oklch(0.5863_0.2064_27.1_/_0.2)]";
    default:
      return "";
  }
};

const formatAcceptance = (value: number | string | undefined | null) => {
  if (value === undefined || value === null) return "-";
  const num = Number(value);
  return isNaN(num) ? "-" : `${num.toFixed(1)}%`;
};

const handleRemove = (e: Event, problem: EnrichedProblem) => {
  e.stopPropagation();
  emit("remove", problem as Problem);
};
</script>

<template>
  <div class="terminal-card overflow-hidden bg-card">
    <DataTable
      :data="displayedProblems"
      :columns="columns"
      :has-more="hasMore"
      :empty-label="t('problem.table.noResults')"
      :empty-description="t('problem.table.tryAdjusting')"
      :loading-label="t('problem.table.loadingMore')"
      @load-more="emit('load-more')"
      @row-click="(problem: EnrichedProblem) => emit('row-click', problem)"
    >
      <template #cell-status="{ item: problem }">
        <div class="flex justify-center items-center">
          <component
            :is="(problem as EnrichedProblem).statusIcon"
            v-if="(problem as EnrichedProblem).statusIcon"
            class="h-5 w-5"
            :class="{
              'text-[var(--terminal-green)]':
                (problem as EnrichedProblem).status === 'solved',
            }"
          />
        </div>
      </template>

      <template #cell-title="{ item: problem }">
        <div class="flex items-center gap-2">
          <span
            class="truncate font-bold text-[var(--solarized-base03)] dark:text-[var(--silver-900)] hover:text-[var(--accent-electric)] transition-colors duration-200"
            >{{ (problem as EnrichedProblem).id }}.
            {{ (problem as EnrichedProblem).title }}</span
          >
          <a
            v-if="(problem as EnrichedProblem).hasSolution"
            href="#"
            class="no-underline hover:no-underline text-muted-foreground hover:text-foreground"
            @click.stop
          >
            <Video class="h-4 w-4" />
          </a>
          <Lock
            v-if="(problem as EnrichedProblem).isPremium"
            class="h-4 w-4 text-[var(--terminal-amber)]"
          />
        </div>
      </template>

      <template #cell-acceptance="{ item: problem }">
        <span class="font-data tabular-nums text-foreground/80 font-medium">
          {{
            formatAcceptance(
              (problem as EnrichedProblem).acceptanceRate ??
                (problem as EnrichedProblem).acceptance_rate,
            )
          }}
        </span>
      </template>

      <template #cell-difficulty="{ item: problem }">
        <span
          class="font-sans text-xs font-semibold px-2 py-0.5 rounded-none inline-block tracking-wide"
          :class="difficultyBadgeClass((problem as EnrichedProblem).difficulty)"
        >
          {{
            t(
              "problem.difficulty." +
                (problem as EnrichedProblem).difficulty.toLowerCase(),
            )
          }}
        </span>
      </template>

      <template v-if="editable" #cell-actions="{ item: problem }">
        <Button
          variant="ghost"
          size="icon"
          class="h-8 w-8 text-muted-foreground hover:text-destructive rounded-none border border-transparent hover:border-destructive/30"
          @click="
            (e: MouseEvent) => handleRemove(e, problem as EnrichedProblem)
          "
        >
          <Trash2 class="h-4 w-4" />
        </Button>
      </template>
    </DataTable>
  </div>
</template>
