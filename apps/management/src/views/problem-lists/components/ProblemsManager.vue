<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { IconPlus, IconTrash } from '@tabler/icons-vue'
import { toast } from 'vue-sonner'
import { formatDateByLocale } from '@/i18n/utils'
import { SemanticBadge, DIFFICULTY_COLOR_MAP, type SemanticColor } from '@/components/ui/terminal'
import ContestProblemPicker from '@/views/contests/components/ContestProblemPicker.vue'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import type { ProblemListDetail, ProblemListProblem } from '@/api/admin/problem-lists'

const props = defineProps<{
  list: ProblemListDetail | null
}>()

const { t } = useI18n()
const store = useAdminProblemListsStore()
const loading = ref(false)
const problems = ref<ProblemListProblem[]>([])
const pickerOpen = ref(false)
const isDirty = ref(false)

watch(
  () => props.list,
  (newList) => {
    if (newList) {
      problems.value = [...newList.problems].sort((a, b) => a.sortOrder - b.sortOrder)
    } else {
      problems.value = []
    }
  },
  { immediate: true },
)

function addProblem(problem: { id: string; title: string; difficulty: string; slug: string }) {
  const problemId = parseInt(problem.id)
  if (problems.value.some((p) => p.id === problemId)) return

  const maxOrder =
    problems.value.length > 0 ? Math.max(...problems.value.map((p) => p.sortOrder)) : 0

  problems.value.push({
    id: problemId,
    title: problem.title,
    slug: problem.slug,
    difficulty: problem.difficulty,
    status: 'todo',
    sortOrder: maxOrder + 1,
    addedAt: new Date().toISOString(),
  })
  isDirty.value = true
  pickerOpen.value = false
}

function removeProblem(problemId: number) {
  problems.value = problems.value.filter((p) => p.id !== problemId)
  isDirty.value = true
}

function updateSortOrder(problemId: number, order: number) {
  const problem = problems.value.find((p) => p.id === problemId)
  if (problem) {
    problem.sortOrder = order
    // Re-sort the list for display
    problems.value.sort((a, b) => a.sortOrder - b.sortOrder)
    isDirty.value = true
  }
}

async function saveProblems() {
  if (!props.list) return
  loading.value = true
  try {
    await store.updateListProblems(props.list.id, {
      problems: problems.value.map((p) => ({
        problemId: p.id,
        sortOrder: p.sortOrder,
      })),
    })
    toast.success(t('problemLists.toast.problemsUpdated'))
    isDirty.value = false
  } catch {
    toast.error(t('problemLists.toast.problemsUpdateFailed'))
  } finally {
    loading.value = false
  }
}

function difficultyColor(difficulty: string): SemanticColor {
  return DIFFICULTY_COLOR_MAP[difficulty?.toUpperCase()] ?? 'neutral'
}
</script>

<template>
  <div class="border border-[var(--editor-panel-border)] bg-[var(--editor-panel-bg)] rounded-none">
    <!-- Card Header -->
    <div
      class="flex items-center justify-between px-4 py-3 border-b border-[var(--editor-border-weak)] select-none"
    >
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-1.5">
          <span
            class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider"
          >
            04 // {{ t('problemLists.problemsManager.manageProblems') }}
          </span>
        </div>
        <span
          class="text-2xs font-mono px-1.5 py-0.5 bg-[var(--editor-control-bg)] border border-[var(--editor-control-border)] text-[var(--editor-cyan)] font-bold"
        >
          {{ problems.length }}
          {{
            t('problemLists.problemsManager.problemsCount', { count: problems.length }).split(
              ' ',
            )[1] || '题目'
          }}
        </span>
      </div>

      <!-- Action Buttons in Header -->
      <div class="flex items-center gap-2">
        <Button
          class="custom-terminal-button custom-terminal-button-secondary"
          @click="pickerOpen = true"
        >
          <IconPlus class="mr-1 h-3 w-3" />
          <span>{{ t('problemLists.problemsManager.addProblem') }}</span>
        </Button>
        <Button
          class="custom-terminal-button custom-terminal-button-primary"
          :disabled="!isDirty || loading"
          @click="saveProblems"
        >
          <span v-if="loading" class="animate-pulse">{{
            t('problemLists.problemsManager.saving')
          }}</span>
          <span v-else>{{ t('problemLists.problemsManager.saveChanges') }}</span>
        </Button>
      </div>
    </div>

    <!-- Table Container -->
    <div class="w-full overflow-x-auto">
      <Table class="w-full border-collapse">
        <TableHeader class="bg-[var(--editor-control-bg)]/20 select-none">
          <TableRow class="hover:bg-transparent border-b border-[var(--editor-border-weak)]">
            <TableHead
              class="h-9 px-4 text-left font-mono text-2xs uppercase tracking-wider text-[var(--editor-text-muted)] w-[80px]"
            >
              {{ t('problemLists.problemsManager.order') }}
            </TableHead>
            <TableHead
              class="h-9 px-4 text-left font-mono text-2xs uppercase tracking-wider text-[var(--editor-text-muted)]"
            >
              {{ t('problemLists.problemsManager.problem') }}
            </TableHead>
            <TableHead
              class="h-9 px-4 text-center font-mono text-2xs uppercase tracking-wider text-[var(--editor-text-muted)] w-[120px]"
            >
              {{ t('problemLists.problemsManager.difficulty') }}
            </TableHead>
            <TableHead
              class="h-9 px-4 text-center font-mono text-2xs uppercase tracking-wider text-[var(--editor-text-muted)] w-[100px]"
            >
              {{ t('table.columnNames.status') }}
            </TableHead>
            <TableHead
              class="h-9 px-4 text-right font-mono text-2xs uppercase tracking-wider text-[var(--editor-text-muted)] w-[160px]"
            >
              {{ t('table.columnNames.addedAt') }}
            </TableHead>
            <TableHead class="h-9 px-4 w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="problem in problems"
            :key="problem.id"
            class="border-b border-[var(--editor-border-weak)] hover:bg-[var(--editor-control-bg)]/15 transition-colors duration-150"
          >
            <!-- Sort Order Input -->
            <TableCell class="p-2 px-4 align-middle">
              <Input
                type="number"
                class="custom-terminal-input text-center w-14 h-7.5 font-mono text-xs tabular-nums p-0"
                :model-value="problem.sortOrder"
                @update:model-value="updateSortOrder(problem.id, Number($event))"
              />
            </TableCell>

            <!-- Problem Info -->
            <TableCell class="p-2 px-4 align-middle">
              <div class="flex flex-col py-0.5">
                <span class="font-mono text-xs font-bold text-[var(--editor-text-primary)]">
                  {{ problem.title }}
                </span>
                <span class="font-mono text-2xs text-[var(--editor-text-muted)]">
                  {{ problem.slug }}
                </span>
              </div>
            </TableCell>

            <!-- Difficulty Badge -->
            <TableCell class="p-2 px-4 text-center align-middle">
              <div class="inline-flex justify-center w-full">
                <SemanticBadge
                  :color="difficultyColor(problem.difficulty)"
                  :label="problem.difficulty?.toUpperCase() || 'UNKNOWN'"
                  size="xs"
                  class="rounded-none border border-[color-mix(in_srgb,_currentColor_25%,_transparent)] font-bold"
                />
              </div>
            </TableCell>

            <!-- Status -->
            <TableCell class="p-2 px-4 text-center align-middle">
              <span class="font-mono text-xxs font-bold text-[var(--editor-text-muted)] uppercase">
                {{ problem.status?.toUpperCase() || '-' }}
              </span>
            </TableCell>

            <!-- Added At Date -->
            <TableCell
              class="p-2 px-4 text-right align-middle font-mono text-xxs text-[var(--editor-text-muted)] tabular-nums"
            >
              {{ problem.addedAt ? formatDateByLocale(problem.addedAt) : '-' }}
            </TableCell>

            <!-- Actions (Delete button) -->
            <TableCell class="p-2 px-4 text-center align-middle">
              <Button
                size="icon"
                variant="ghost"
                class="h-8 w-8 custom-action-trash rounded-none flex items-center justify-center border border-transparent"
                @click="removeProblem(problem.id)"
              >
                <IconTrash class="h-3.5 w-3.5" />
              </Button>
            </TableCell>
          </TableRow>

          <!-- Empty State -->
          <TableRow v-if="problems.length === 0">
            <TableCell colspan="6" class="h-28 text-center align-middle select-none">
              <span class="font-mono text-xs text-[var(--editor-text-muted)]">
                // {{ t('problemLists.problemsManager.noProblems') }}
              </span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <ContestProblemPicker
      v-model:open="pickerOpen"
      :exclude-ids="problems.map((p) => p.id.toString())"
      @select="addProblem"
    />
  </div>
</template>

<style scoped>
.custom-terminal-input {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-xs);
  border-radius: var(--radius-md) !important;
  border: 1px solid var(--editor-control-border);
  background: var(--editor-control-bg);
  color: var(--editor-text-primary);
  transition:
    border-color 0.15s ease-in-out,
    box-shadow 0.15s ease-in-out;
}

:deep(.custom-terminal-input) {
  border-radius: var(--radius-md) !important;
  font-family: var(--uc-font-code);
}

.custom-terminal-input:hover:not(:disabled) {
  border-color: var(--editor-panel-border);
}

.custom-terminal-input:focus {
  outline: none;
  border-color: var(--editor-cyan) !important;
  box-shadow: 0 0 0 1px var(--editor-cyan) !important;
}

.custom-terminal-button {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-xxs);
  font-weight: var(--uc-font-weight-bold);
  text-transform: uppercase;
  border-radius: var(--radius-md) !important;
  padding: 6px 12px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease-in-out;
  cursor: pointer;
}

.custom-terminal-button-primary {
  background-color: var(--primary);
  color: var(--primary-foreground);
  border: 1px solid transparent;
}

.custom-terminal-button-primary:hover:not(:disabled) {
  background-color: color-mix(in srgb, var(--primary) 85%, transparent);
}

.custom-terminal-button-primary:disabled {
  opacity: 0.45;
  background-color: var(--editor-control-bg);
  border-color: var(--editor-control-border);
  color: var(--editor-text-muted);
  cursor: not-allowed;
}

.custom-terminal-button-secondary {
  background-color: transparent;
  color: var(--editor-text-primary);
  border: 1px solid var(--editor-control-border);
}

.custom-terminal-button-secondary:hover:not(:disabled) {
  background-color: var(--editor-control-bg);
  border-color: var(--editor-panel-border);
}

.custom-action-trash {
  color: var(--editor-text-muted);
  opacity: 0.6;
  transition: all 0.15s ease-in-out;
}

.custom-action-trash:hover {
  opacity: 1;
  background-color: color-mix(in srgb, var(--editor-red) 12%, transparent);
  border-color: color-mix(in srgb, var(--editor-red) 30%, transparent);
  color: var(--editor-red);
}
</style>
