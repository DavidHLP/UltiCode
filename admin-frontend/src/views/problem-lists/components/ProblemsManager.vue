<script setup lang="ts">
import { ref, watch } from 'vue'
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
import { Badge } from '@/components/ui/badge'
import { IconPlus, IconTrash } from '@tabler/icons-vue'
import { toast } from 'vue-sonner'
import ContestProblemPicker from '@/views/contests/components/ContestProblemPicker.vue'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import type { ProblemListDetail, ProblemListProblem } from '@/api/admin/problem-lists'

const props = defineProps<{
  list: ProblemListDetail | null
}>()

const store = useAdminProblemListsStore()
const loading = ref(false)
const problems = ref<ProblemListProblem[]>([])
const pickerOpen = ref(false)
const isDirty = ref(false)

watch(
  () => props.list,
  (newList) => {
    if (newList) {
      problems.value = [...newList.problems].sort((a, b) => a.sort_order - b.sort_order)
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
    problems.value.length > 0 ? Math.max(...problems.value.map((p) => p.sort_order)) : 0

  problems.value.push({
    id: problemId,
    title: problem.title,
    slug: problem.slug,
    difficulty: problem.difficulty,
    status: 'todo',
    sort_order: maxOrder + 1,
    added_at: new Date().toISOString(),
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
    problem.sort_order = order
    // Re-sort the list for display
    problems.value.sort((a, b) => a.sort_order - b.sort_order)
    isDirty.value = true
  }
}

async function saveProblems() {
  if (!props.list) return
  loading.value = true
  try {
    await store.updateListProblems(props.list.id, {
      problems: problems.value.map((p) => ({
        problem_id: p.id,
        sort_order: p.sort_order,
      })),
    })
    toast.success('Problems updated successfully')
    isDirty.value = false
  } catch {
    toast.error('Failed to update problems')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex justify-between items-center">
      <h3 class="text-lg font-medium">Problems</h3>
      <div class="flex gap-2">
        <Button size="sm" variant="outline" @click="pickerOpen = true">
          <IconPlus class="mr-2 h-4 w-4" />
          Add Problem
        </Button>
        <Button size="sm" @click="saveProblems" :disabled="!isDirty || loading">
          {{ loading ? 'Saving...' : 'Save Changes' }}
        </Button>
      </div>
    </div>

    <div class="border rounded-md">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-[80px]">Order</TableHead>
            <TableHead>Problem</TableHead>
            <TableHead>Difficulty</TableHead>
            <TableHead class="w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="problem in problems" :key="problem.id">
            <TableCell>
              <Input
                type="number"
                class="w-16 h-8"
                :model-value="problem.sort_order"
                @update:model-value="updateSortOrder(problem.id, Number($event))"
              />
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

          <TableRow v-if="problems.length === 0">
            <TableCell colspan="4" class="h-24 text-center text-muted-foreground">
              No problems in this list.
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
