<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import CasesForm from '../components/CasesForm.vue'
import type { CasesFormData } from '../components/CasesForm.vue'
import type { Problem, ProblemExample } from '@/api/admin/problems'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof CasesForm>>()
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

function mapExampleToTestCase(example: ProblemExample): CasesFormData['examples'][number] {
  return {
    id: example.id,
    input: example.input,
    output: example.output,
    explanation: example.explanation,
  }
}

async function handleSubmit(data: CasesFormData) {
  try {
    await problemsStore.updateProblem(problemId.value, {
      examples: data.examples.map((ex, idx) => ({
        id: ex.id || crypto.randomUUID(),
        input: ex.input,
        output: ex.output,
        explanation: ex.explanation,
        order: idx,
      })),
      constraints: data.constraints,
      hints: data.hints,
      tags: data.tags,
    })
    toast.success('Test cases updated successfully')
    router.push({ name: 'problem-view-cases', params: { id: problemId.value } })
  } catch (error) {
    console.error('Failed to update problem test cases:', error)
    toast.error('Failed to update test cases')
  }
}

// Convert backend problem data to form format
const formattedProblem = computed(() => {
  if (!problemData.value) return undefined

  return {
    examples: problemData.value.examples?.map(mapExampleToTestCase) || [],
    constraints: problemData.value.detail?.constraints_json || [],
    hints: problemData.value.detail?.hints || [],
    tags: problemData.value.tags?.map((t) => t.label) || [],
  }
})

function handleCancel() {
  router.push({ name: 'problem-view-cases', params: { id: problemId.value } })
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <!-- Breadcrumbs -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground">
      <router-link :to="{ name: 'problems' }" class="hover:text-foreground transition-colors">
        Problems
      </router-link>
      <span>/</span>
      <router-link
        :to="{ name: 'problem-view-cases', params: { id: problemId } }"
        class="hover:text-foreground transition-colors"
      >
        {{ problemData?.title || 'Loading...' }}
      </router-link>
      <span>/</span>
      <span class="text-foreground">Edit Test Cases</span>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Edit Test Cases</h1>
        <p class="text-muted-foreground">Update examples, constraints, hints, and tags</p>
      </div>
    </div>

    <div v-if="loadingData" class="text-center py-8">
      <div
        class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
      ></div>
      <p class="mt-2 text-muted-foreground">Loading problem data...</p>
    </div>

    <CasesForm
      v-else-if="formattedProblem"
      :problem="formattedProblem"
      ref="formRef"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>
