<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import ProblemForm from './components/ProblemForm.vue'
import type { ProblemFormData } from '@/lib/schemas/problem'
import type { TestCaseExample } from '@/components/problem/TestCasesEditor.vue'
import type { Problem, ProblemExample } from '@/api/admin/problems'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof ProblemForm>>()
const loadingData = ref(true)
const problemData = ref<Problem | null>(null)

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

function mapExampleToTestCase(example: ProblemExample): TestCaseExample {
  return {
    id: example.id,
    input: example.input,
    output: example.output,
    explanation: example.explanation,
  }
}

async function handleSubmit(data: ProblemFormData) {
  try {
    await problemsStore.updateProblem(problemId.value, {
      ...data,
      difficulty: data.difficulty,
      status: data.status,
      examples: data.examples.map((ex, idx) => ({
        id: ex.id || crypto.randomUUID(),
        input: ex.input,
        output: ex.output,
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
    examples: problemData.value.examples?.map(mapExampleToTestCase) || [
      { id: 'example-1', input: '', output: '', explanation: '' },
    ],
    constraints: problemData.value.detail?.constraints_json || [],
    hints: problemData.value.detail?.hints || [],
    languages: problemData.value.languages?.map((l) => l.language) || [],
    tags: problemData.value.tags?.map((t) => t.label) || [],
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
