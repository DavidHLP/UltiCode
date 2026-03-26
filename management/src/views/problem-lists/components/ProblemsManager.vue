<script setup lang="ts">
import { ref, watch, h } from 'vue'
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

// Terminal-style difficulty badge
function getDifficultyStyle(difficulty: string): string {
  const styles: Record<string, string> = {
    easy: 'bg-[oklch(0.7_0.15_145/0.15)] border-[oklch(0.7_0.15_145/0.4)] text-[var(--terminal-green)]',
    medium:
      'bg-[oklch(0.75_0.15_85/0.15)] border-[oklch(0.75_0.15_85/0.4)] text-[var(--terminal-amber)]',
    hard: 'bg-[oklch(0.6_0.2_25/0.15)] border-[oklch(0.6_0.2_25/0.4)] text-[var(--terminal-red)]',
  }
  return (
    styles[difficulty?.toLowerCase()] ||
    'bg-[var(--silver-100)] border-[var(--silver-300)] text-[var(--silver-500)]'
  )
}

function renderDifficultyBadge(difficulty: string) {
  return h(
    'span',
    {
      class: [
        'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
        'px-2 py-0.5 border rounded-sm',
        getDifficultyStyle(difficulty),
      ].join(' '),
    },
    difficulty?.toLowerCase() || 'unknown',
  )
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex justify-between items-center pb-4 border-b border-[var(--silver-200)]">
      <div class="flex items-center gap-3">
        <span class="terminal-comment">{{ t('problemLists.problemsManager.manageProblems') }}</span>
        <span class="font-data text-xs text-[var(--terminal-cyan)]">
          {{ t('problemLists.problemsManager.problemsCount', { count: problems.length }) }}
        </span>
      </div>
      <div class="flex gap-2">
        <Button variant="terminal" size="sm" class="font-data text-xs" @click="pickerOpen = true">
          <IconPlus class="mr-1.5 h-3.5 w-3.5" />
          {{ t('problemLists.problemsManager.addProblem').toUpperCase() }}
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs bg-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/90"
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

    <!-- Table -->
    <div class="border border-[var(--silver-200)] rounded-md">
      <Table>
        <TableHeader>
          <TableRow class="hover:bg-transparent">
            <TableHead class="terminal-column w-[80px]">
              <span
                class="font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]"
              >
                {{ t('problemLists.problemsManager.order') }}
              </span>
            </TableHead>
            <TableHead class="terminal-column">
              <span
                class="font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]"
              >
                {{ t('problemLists.problemsManager.problem') }}
              </span>
            </TableHead>
            <TableHead class="terminal-column">
              <span
                class="font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]"
              >
                {{ t('problemLists.problemsManager.difficulty') }}
              </span>
            </TableHead>
            <TableHead class="terminal-column w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="problem in problems"
            :key="problem.id"
            class="border-[var(--silver-200)] hover:bg-[var(--surface-sunken)]"
          >
            <TableCell>
              <Input
                type="number"
                class="terminal-input w-16 h-8 font-data text-xs tabular-nums"
                :model-value="problem.sortOrder"
                @update:model-value="updateSortOrder(problem.id, Number($event))"
              />
            </TableCell>
            <TableCell>
              <div class="flex flex-col gap-0.5 py-1">
                <span class="font-medium text-sm text-[var(--foreground)]">{{
                  problem.title
                }}</span>
                <span class="font-data text-xs text-[var(--silver-400)]">{{ problem.slug }}</span>
              </div>
            </TableCell>
            <TableCell>
              <component :is="renderDifficultyBadge(problem.difficulty)" />
            </TableCell>
            <TableCell>
              <Button
                size="icon"
                variant="ghost"
                class="h-8 w-8 hover:bg-[oklch(0.6_0.2_25/0.15)]"
                @click="removeProblem(problem.id)"
              >
                <IconTrash class="h-4 w-4 text-[var(--terminal-red)]" />
              </Button>
            </TableCell>
          </TableRow>

          <!-- Empty State -->
          <TableRow v-if="problems.length === 0">
            <TableCell colspan="4" class="h-24 text-center">
              <span class="terminal-comment">{{
                t('problemLists.problemsManager.noProblems')
              }}</span>
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
