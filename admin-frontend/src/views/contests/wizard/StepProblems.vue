<script setup lang="ts">
import { ref } from 'vue'
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
import { Badge } from '@/components/ui/badge'
import { IconPlus, IconTrash } from '@tabler/icons-vue'
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

const pickerOpen = ref(false)

function addProblem(problem: { id: string; title: string; slug: string; difficulty: string }) {
  const currentProblems = props.formData.selectedProblems || []
  if (currentProblems.find((p) => p.id === problem.id)) return

  const newProblem = {
    ...problem,
    score: 100, // Default score
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
    <div class="flex justify-between items-center">
      <h3 class="text-sm font-medium">Contest Problems</h3>
      <Button size="sm" variant="outline" @click="pickerOpen = true">
        <IconPlus class="mr-2 h-4 w-4" />
        Add Problem
      </Button>
    </div>

    <div class="border rounded-md">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-[50px]">Index</TableHead>
            <TableHead>Title</TableHead>
            <TableHead>Difficulty</TableHead>
            <TableHead class="w-[100px]">Score</TableHead>
            <TableHead class="w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="(problem, index) in formData.selectedProblems || []" :key="problem.id">
            <TableCell class="font-medium">
              {{ String.fromCharCode(65 + index) }}
            </TableCell>
            <TableCell>
              <div class="flex flex-col">
                <span class="font-medium">{{ problem.title }}</span>
                <span class="text-xs text-muted-foreground">{{ problem.slug }}</span>
              </div>
            </TableCell>
            <TableCell>
              <Badge variant="outline" class="capitalize">
                {{ problem.difficulty?.toLowerCase() }}
              </Badge>
            </TableCell>
            <TableCell>
              <Input
                type="number"
                min="0"
                class="h-8 w-20"
                :model-value="problem.score"
                @update:model-value="updateScore(problem.id, Number($event))"
              />
            </TableCell>
            <TableCell>
              <Button
                size="icon"
                variant="ghost"
                class="h-8 w-8 text-destructive"
                @click="removeProblem(problem.id)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="!formData.selectedProblems?.length">
            <TableCell colspan="5" class="h-24 text-center text-muted-foreground">
              No problems selected. Add problems to the contest.
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
