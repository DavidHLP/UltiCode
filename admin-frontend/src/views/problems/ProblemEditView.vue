<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import ProblemForm from './components/ProblemForm.vue'
import type { ProblemFormData } from '@/lib/schemas/problem'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof ProblemForm>>()
const loadingData = ref(true)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const problemData = ref<any>(null)

const problemId = computed(() => route.params.id as string)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  const problem = await problemsStore.fetchProblem(problemId.value)
  if (problem) {
    problemData.value = problem
  }
  loadingData.value = false
}

async function handleSubmit(data: ProblemFormData) {
  try {
    await problemsStore.updateProblem(problemId.value, {
      ...data,
      difficulty: data.difficulty as Difficulty,
      status: data.status as ProblemStatus,
      examples: data.examples.map((ex, idx) => ({
        id: ex.id || crypto.randomUUID(),
        input_text: ex.input,
        output_text: ex.output,
        explanation: ex.explanation,
        order: idx,
      })),
    })
    toast.success('Problem updated successfully')
    router.push({ name: 'problem-detail', params: { id: problemId.value } })
  } catch (error) {
    console.error('Failed to update problem:', error)
    toast.error('Failed to update problem')
  }
}

// Convert backend problem data to form format
const formattedProblem = computed(() => {
  if (!problemData.value) return undefined

  return {
    slug: problemData.value.slug,
    title: problemData.value.title,
    difficulty: problemData.value.difficulty,
    status: problemData.value.status,
    is_premium: problemData.value.is_premium,
    is_published: problemData.value.is_published,
    summary: problemData.value.detail?.summary || '',
    content: problemData.value.detail?.content || '',
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    examples: problemData.value.examples?.map((ex: any) => ({
      id: ex.id,
      input: ex.input_text,
      output: ex.output_text,
      explanation: ex.explanation,
    })) || [{ id: 'example-1', input: '', output: '', explanation: '' }],
    constraints: problemData.value.detail?.constraints_json || [],
    hints: problemData.value.detail?.hints || [],
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    languages: problemData.value.languages?.map((l: any) => l.language) || [],
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    tags: problemData.value.tags?.map((t: any) => t.label) || [],
  }
})
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Edit Problem</h1>
        <p class="text-muted-foreground">Update problem information</p>
      </div>
      <Button
        variant="outline"
        @click="router.push({ name: 'problem-detail', params: { id: problemId } })"
      >
        Cancel
      </Button>
    </div>

    <div v-if="loadingData" class="text-center py-8">
      <div
        class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
      ></div>
      <p class="mt-2 text-muted-foreground">Loading problem data...</p>
    </div>

    <ProblemForm
      v-else-if="formattedProblem"
      :problem="formattedProblem"
      :is-edit="true"
      ref="formRef"
      @submit="handleSubmit"
    >
      <template #cancel>
        <Button
          variant="outline"
          @click="router.push({ name: 'problem-detail', params: { id: problemId } })"
        >
          Cancel
        </Button>
      </template>
    </ProblemForm>
  </div>
</template>
