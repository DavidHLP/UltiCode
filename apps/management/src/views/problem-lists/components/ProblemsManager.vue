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
} from '@ulticode/design-system/ui/table'
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import { Plus as IconPlus, Trash as IconTrash } from '@lucide/vue'

import { toast } from 'vue-sonner'
import { formatDateByLocale } from '@/i18n/utils'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { DIFFICULTY_COLOR_MAP, type SemanticColor } from '@ulticode/badge-config'
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
  <div class="border border-border bg-card rounded-lg">
    <!-- Card Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border select-none">
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-1.5">
          <span class="text-xs font-mono font-bold text-foreground uppercase tracking-wider">
            04 // {{ t('problemLists.problemsManager.manageProblems') }}
          </span>
        </div>
        <span
          class="text-xs font-mono px-1.5 py-0.5 bg-muted border border-input text-primary font-bold"
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
        <Button class=" " @click="pickerOpen = true">
          <IconPlus class="mr-1 h-3 w-3" />
          <span>{{ t('problemLists.problemsManager.addProblem') }}</span>
        </Button>
        <Button class=" " :disabled="!isDirty || loading" @click="saveProblems">
          <span v-if="loading" class="animate-pulse">{{
            t('problemLists.problemsManager.saving')
          }}</span>
          <span v-else>{{ t('problemLists.problemsManager.saveChanges') }}</span>
        </Button>
      </div>
    </div>

    <!-- Table Container -->
    <div class="w-full overflow-x-auto">
      <Table class="w-full">
        <TableHeader class="select-none">
          <TableRow>
            <TableHead class="h-9 px-4 text-left w-[80px]">
              {{ t('problemLists.problemsManager.order') }}
            </TableHead>
            <TableHead class="h-9 px-4 text-left">
              {{ t('problemLists.problemsManager.problem') }}
            </TableHead>
            <TableHead class="h-9 px-4 text-center w-[120px]">
              {{ t('problemLists.problemsManager.difficulty') }}
            </TableHead>
            <TableHead class="h-9 px-4 text-center w-[100px]">
              {{ t('table.columnNames.status') }}
            </TableHead>
            <TableHead class="h-9 px-4 text-right w-[160px]">
              {{ t('table.columnNames.addedAt') }}
            </TableHead>
            <TableHead class="h-9 px-4 w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="problem in problems" :key="problem.id">
            <!-- Sort Order Input -->
            <TableCell class="p-2 px-4 align-middle">
              <Input
                type="number"
                class="text-center w-14 tabular-nums p-0"
                :model-value="problem.sortOrder"
                @update:model-value="updateSortOrder(problem.id, Number($event))"
              />
            </TableCell>

            <!-- Problem Info -->
            <TableCell class="p-2 px-4 align-middle">
              <div class="flex flex-col py-0.5">
                <span class="font-mono text-xs font-bold text-foreground">
                  {{ problem.title }}
                </span>
                <span class="font-mono text-xs text-muted-foreground">
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
                  class="rounded-lg border border-[color-mix(in_srgb,_currentColor_25%,_transparent)] font-bold"
                />
              </div>
            </TableCell>

            <!-- Status -->
            <TableCell class="p-2 px-4 text-center align-middle">
              <span class="font-mono text-xs font-bold text-muted-foreground uppercase">
                {{ problem.status?.toUpperCase() || '-' }}
              </span>
            </TableCell>

            <!-- Added At Date -->
            <TableCell class="p-2 px-4 text-right align-middle tabular-nums">
              {{ problem.addedAt ? formatDateByLocale(problem.addedAt) : '-' }}
            </TableCell>

            <!-- Actions (Delete button) -->
            <TableCell class="p-2 px-4 text-center align-middle">
              <Button
                size="icon"
                variant="ghost"
                class="w-8 flex items-center justify-center"
                @click="removeProblem(problem.id)"
              >
                <IconTrash class="h-3.5 w-3.5" />
              </Button>
            </TableCell>
          </TableRow>

          <!-- Empty State -->
          <TableRow v-if="problems.length === 0">
            <TableCell colspan="6" class="h-28 text-center align-middle select-none">
              <span class="font-mono text-xs text-muted-foreground">
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
