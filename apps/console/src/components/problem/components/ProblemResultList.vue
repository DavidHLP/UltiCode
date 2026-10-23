<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";
import { Video, Lock, Trash2 } from "@lucide/vue";
import { DataTable, type ColumnDef } from "@/components/common/data-table";
import { TableHead, TableRow } from "@ulticode/design-system/ui/table";
import { useI18n } from "vue-i18n";
import { getDifficultyBadgeClass } from "@ulticode/design-system";
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
  <div
    class="rounded-xl border bg-card text-card-foreground overflow-hidden [&_table]:table-fixed [&_table]:min-w-[30rem]"
  >
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
      <template #header>
        <TableRow>
          <TableHead
            v-for="column in columns"
            :key="column.key"
            :class="
              column.key === 'status'
                ? 'w-[50px] px-2 text-center'
                : ['px-4', column.headerClass]
            "
          >
            <span :class="column.key === 'status' ? 'sr-only' : undefined">
              {{ column.header }}
            </span>
          </TableHead>
        </TableRow>
      </template>

      <template #cell-status="{ item: problem }">
        <div class="flex justify-center items-center">
          <component
            :is="(problem as EnrichedProblem).statusIcon"
            v-if="(problem as EnrichedProblem).statusIcon"
            class="h-5 w-5"
            :class="{
              'text-foreground':
                (problem as EnrichedProblem).status === 'solved',
            }"
          />
        </div>
      </template>

      <template #cell-title="{ item: problem }">
        <div class="flex min-w-0 items-center gap-2">
          <span
            class="truncate font-bold text-foreground transition-colors duration-200 hover:underline hover:decoration-primary"
            >{{ (problem as EnrichedProblem).id }}.
            {{ (problem as EnrichedProblem).title }}</span
          >
          <a
            v-if="(problem as EnrichedProblem).hasSolution"
            href="#"
            class="shrink-0 no-underline hover:no-underline text-muted-foreground hover:text-foreground"
            @click.stop
          >
            <Video class="h-4 w-4" />
          </a>
          <Lock
            v-if="(problem as EnrichedProblem).isPremium"
            class="h-4 w-4 shrink-0 text-status-warning-mark"
          />
        </div>
      </template>

      <template #cell-acceptance="{ item: problem }">
        <span class="font-mono tabular-nums text-foreground/80 font-medium">
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
          class="font-sans text-xs font-semibold px-2 py-0.5 rounded-md inline-block tracking-wide"
          :class="
            getDifficultyBadgeClass((problem as EnrichedProblem).difficulty)
          "
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
          class="w-8"
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
