<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Input } from '@/components/ui/input'
import { IconPlus, IconTrash } from '@tabler/icons-vue'
import { SemanticBadge, DIFFICULTY_COLOR_MAP } from '@/components/ui/terminal'
import ContestProblemPicker from '../components/ContestProblemPicker.vue'
import type { ProblemsSlice } from './useContestAuthoring'

defineProps<{ slice: ProblemsSlice }>()
const emit = defineEmits<{
  (e: 'add', problem: { id: string; title: string; slug: string; difficulty: string }): void
  (e: 'remove', problemId: string): void
  (e: 'score', payload: { problemId: string; score: number }): void
}>()

const { t } = useI18n()
const pickerOpen = ref(false)

function handleSelect(problem: {
  id: string
  title: string
  slug: string
  difficulty: string
}): void {
  emit('add', problem)
  pickerOpen.value = false
}

function handleScore(problemId: string, value: string | number): void {
  const n = Number(value)
  if (Number.isFinite(n)) emit('score', { problemId, score: n })
}
</script>

<template>
  <div class="space-y-4">
    <!-- Section Header -->
    <div class="flex justify-between items-center">
      <div class="flex items-center gap-2">
        <span class="terminal-comment">problems_config</span>
        <span class="terminal-label">[{{ slice.problems.length }}]</span>
      </div>
      <Button
        type="button"
        size="sm"
        variant="terminal"
        class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--status-success-mark)] hover:text-foreground-strong"
        @click="pickerOpen = true"
      >
        <IconPlus class="mr-1.5 h-3.5 w-3.5" />
        <span class="uppercase tracking-wider">{{ t('contests.problemsStep.addProblem') }}</span>
      </Button>
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]">
      <Table class="terminal-table">
        <TableHeader>
          <TableRow
            class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
          >
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)] w-[50px]"
            >
              #
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]"
            >
              {{ t('contests.problemsStep.title') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]"
            >
              {{ t('contests.problemsStep.difficulty') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)] w-[100px]"
            >
              {{ t('contests.problemsStep.score') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)] w-[50px]"
            >
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="(problem, index) in slice.problems"
            :key="problem.id"
            class="border-b border-[var(--surface-highlight)] dark:border-[var(--foreground-strong)]"
          >
            <TableCell class="font-data text-xs text-[var(--primary)]">
              {{ String.fromCharCode(65 + index) }}
            </TableCell>
            <TableCell>
              <div class="flex flex-col gap-0.5">
                <span class="font-medium text-sm text-[var(--foreground)]">{{
                  problem.title
                }}</span>
                <span class="font-data text-xs text-[var(--foreground-muted)]">{{ problem.slug }}</span>
              </div>
            </TableCell>
            <TableCell>
              <SemanticBadge
                :color="DIFFICULTY_COLOR_MAP[problem.difficulty] ?? 'neutral'"
                :label="problem.difficulty?.toUpperCase()"
                size="sm"
                dot
              />
            </TableCell>
            <TableCell>
              <Input
                type="number"
                min="0"
                class="h-8 w-20 font-data text-xs border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] focus:border-[var(--primary)]"
                :model-value="problem.score"
                @update:model-value="handleScore(problem.id, $event)"
              />
            </TableCell>
            <TableCell>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                class="h-8 w-8 text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]"
                @click="emit('remove', problem.id)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="slice.problems.length === 0">
            <TableCell colspan="5" class="h-24 text-center">
              <span class="terminal-comment">{{
                t('contests.problemsStep.noProblemsSelected')
              }}</span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <ContestProblemPicker
      v-model:open="pickerOpen"
      :exclude-ids="slice.problems.map((p) => p.id)"
      @select="handleSelect"
    />
  </div>
</template>
