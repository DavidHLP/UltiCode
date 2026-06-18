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

const props = defineProps<{
  formData: {
    selectedProblems?: {
      id: string
      title: string
      slug: string
      difficulty: string
      score?: number
    }[]
    [key: string]: unknown
  }
}>()

const emit = defineEmits<{
  (e: 'update:formData', value: unknown): void
}>()

const { t } = useI18n()
const pickerOpen = ref(false)

function addProblem(problem: { id: string; title: string; slug: string; difficulty: string }) {
  const currentProblems = props.formData.selectedProblems || []
  if (currentProblems.find((p) => p.id === problem.id)) return

  const newProblem = {
    ...problem,
    score: 100,
  }

  emit('update:formData', {
    ...props.formData,
    selectedProblems: [...currentProblems, newProblem],
  })
  pickerOpen.value = false
}

function removeProblem(problemId: string) {
  const currentProblems = props.formData.selectedProblems || []
  emit('update:formData', {
    ...props.formData,
    selectedProblems: currentProblems.filter((p) => p.id !== problemId),
  })
}

function updateScore(problemId: string, score: number) {
  const currentProblems = props.formData.selectedProblems || []
  emit('update:formData', {
    ...props.formData,
    selectedProblems: currentProblems.map((p) => (p.id === problemId ? { ...p, score } : p)),
  })
}
</script>

<template>
  <div class="space-y-4">
    <!-- Section Header -->
    <div class="flex justify-between items-center">
      <div class="flex items-center gap-2">
        <span class="terminal-comment">problems_config</span>
        <span class="terminal-label">[{{ formData.selectedProblems?.length || 0 }}]</span>
      </div>
      <Button
        type="button"
        size="sm"
        variant="terminal"
        class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
        @click="pickerOpen = true"
      >
        <IconPlus class="mr-1.5 h-3.5 w-3.5" />
        <span class="uppercase tracking-wider">{{ t('contests.problemsStep.addProblem') }}</span>
      </Button>
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-[var(--silver-200)] dark:border-[var(--silver-700)]">
      <Table class="terminal-table">
        <TableHeader>
          <TableRow
            class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
          >
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[50px]"
            >
              #
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]"
            >
              {{ t('contests.problemsStep.title') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]"
            >
              {{ t('contests.problemsStep.difficulty') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[100px]"
            >
              {{ t('contests.problemsStep.score') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[50px]"
            >
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="(problem, index) in formData.selectedProblems || []"
            :key="problem.id"
            class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)]"
          >
            <TableCell class="font-data text-xs text-[var(--accent-electric)]">
              {{ String.fromCharCode(65 + index) }}
            </TableCell>
            <TableCell>
              <div class="flex flex-col gap-0.5">
                <span class="font-medium text-sm text-[var(--foreground)]">{{
                  problem.title
                }}</span>
                <span class="font-data text-xs text-[var(--silver-400)]">{{ problem.slug }}</span>
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
                class="h-8 w-20 font-data text-xs border-[var(--silver-200)] dark:border-[var(--silver-700)] focus:border-[var(--accent-electric)]"
                :model-value="problem.score"
                @update:model-value="updateScore(problem.id, Number($event))"
              />
            </TableCell>
            <TableCell>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                class="h-8 w-8 text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
                @click="removeProblem(problem.id)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="!formData.selectedProblems?.length">
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
      :exclude-ids="formData.selectedProblems?.map((p: any) => p.id) || []"
      @select="addProblem"
    />
  </div>
</template>
