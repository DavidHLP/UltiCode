<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconArrowLeft, IconFlask, IconDatabase } from '@tabler/icons-vue'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import CasesForm from '../components/CasesForm.vue'
import type { CasesFormData } from '../components/CasesForm.vue'
import type { Problem, ProblemExample } from '@/api/admin/problems'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof CasesForm>>()
const loadingData = ref(true)
const problemData = ref<Problem | null>(null)
const isLoaded = ref(false)

const problemId = computed(() => route.params.id as string)

onMounted(async () => {
  await loadData()
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
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
    toast.success(t('problems.toast.updateSuccess'))
    router.push({ name: 'problem-detail', params: { id: problemId.value } })
  } catch (error) {
    console.error('Failed to update problem test cases:', error)
    toast.error(t('problems.toast.updateFailed'))
  }
}

// Convert backend problem data to form format
const formattedProblem = computed(() => {
  if (!problemData.value) return undefined

  return {
    examples: problemData.value.examples?.map(mapExampleToTestCase) || [],
    constraints: problemData.value.detail?.constraintsJson || [],
    hints: problemData.value.detail?.hints || [],
    tags: problemData.value.tags?.map((t) => t.label) || [],
  }
})

function handleCancel() {
  router.push({ name: 'problem-detail', params: { id: problemId.value } })
}
</script>

<template>
  <div class="relative flex flex-col gap-0 overflow-auto">
    <!-- Terminal Header -->
    <div
      :class="[
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="terminal"
            size="icon"
            class="h-8 w-8 border-[var(--silver-300)]"
            @click="router.push({ name: 'problem-detail', params: { id: problemId } })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>

          <div class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2">
              <span class="terminal-prompt text-sm">problem</span>
              <span class="terminal-cursor" />
            </div>
            <h1 v-if="problemData" class="text-sm font-medium text-[var(--foreground)] truncate">
              {{ problemData.title }}
            </h1>
            <Skeleton v-else class="h-5 w-32" />
          </div>
        </div>
      </div>

      <!-- Info Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">action:</span>
          <span class="font-data text-sm text-[var(--accent-electric)]">EDIT</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">section:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)]">test_cases</span>
        </div>
        <div class="flex items-center gap-2">
          <IconFlask class="h-4 w-4 text-[var(--silver-400)]" />
          <span class="text-xs text-[var(--silver-500)]">{{
            t('problems.edit.testCasesSubtitle')
          }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-[var(--silver-400)]">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">test case editor</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1 py-4">
      <!-- Loading State - Terminal Style -->
      <div v-if="loadingData" class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 rounded-full bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] flex items-center justify-center mb-3"
        >
          <div
            class="h-6 w-6 animate-spin rounded-full border-2 border-[var(--accent-electric)] border-t-transparent"
          ></div>
        </div>
        <h2 class="text-sm font-medium mb-1 font-data">{{ t('problems.edit.loading') }}</h2>
        <p class="text-xs text-[var(--silver-500)] font-data">// fetching problem data...</p>
      </div>

      <CasesForm
        v-else-if="formattedProblem"
        :problem="formattedProblem"
        ref="formRef"
        @submit="handleSubmit"
        @cancel="handleCancel"
      />
    </div>
  </div>
</template>
