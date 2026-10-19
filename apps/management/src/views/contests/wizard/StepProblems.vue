<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@ulticode/design-system/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@ulticode/design-system/ui/table'
import { Input } from '@ulticode/design-system/ui/input'
import { Plus as IconPlus, Trash as IconTrash } from '@lucide/vue'

import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { DIFFICULTY_COLOR_MAP } from '@ulticode/badge-config'
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
        <span class="text-sm text-muted-foreground">{{ t('contests.wizard.problems') }}</span>
        <span class="text-sm font-medium">[{{ slice.problems.length }}]</span>
      </div>
      <Button type="button" size="sm" variant="outline" @click="pickerOpen = true">
        <IconPlus class="mr-1.5 h-3.5 w-3.5" />
        <span class="uppercase tracking-wider">{{ t('contests.problemsStep.addProblem') }}</span>
      </Button>
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-border">
      <Table class="w-full">
        <TableHeader>
          <TableRow>
            <TableHead class="w-[50px]"> # </TableHead>
            <TableHead>
              {{ t('contests.problemsStep.title') }}
            </TableHead>
            <TableHead>
              {{ t('contests.problemsStep.difficulty') }}
            </TableHead>
            <TableHead class="w-[100px]">
              {{ t('contests.problemsStep.score') }}
            </TableHead>
            <TableHead class="w-[50px]"> </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="(problem, index) in slice.problems" :key="problem.id">
            <TableCell>
              {{ String.fromCharCode(65 + index) }}
            </TableCell>
            <TableCell>
              <div class="flex flex-col gap-0.5">
                <span class="font-medium text-sm text-foreground">{{ problem.title }}</span>
                <span class="font-mono text-xs text-muted-foreground">{{ problem.slug }}</span>
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
                class="w-20"
                :model-value="problem.score"
                @update:model-value="handleScore(problem.id, $event)"
              />
            </TableCell>
            <TableCell>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                class="w-8"
                @click="emit('remove', problem.id)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="slice.problems.length === 0">
            <TableCell colspan="5" class="h-24 text-center">
              <span class="text-sm text-muted-foreground">{{
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
