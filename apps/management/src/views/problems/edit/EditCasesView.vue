<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  ArrowLeft as IconArrowLeft,
  FlaskConical as IconFlask,
  Database as IconDatabase,
} from '@lucide/vue'

import { useProblemsStore } from '@/stores/admin/problems'
import { useProblemTab } from '../composables/useProblemTab'
import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@ulticode/design-system/ui/tabs'
import CasesForm from '../components/CasesForm.vue'
import type { CasesFormData } from '../components/CasesForm.vue'
import type { ProblemExample } from '@/api/admin/problems'
import HiddenTestCasesEditor from '@/components/problem/HiddenTestCasesEditor.vue'

type CasesTab = 'samples' | 'hidden'

const router = useRouter()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const { problemId, data, loading, isReady } = useProblemTab('cases', (id) =>
  problemsStore.fetchCases(id),
)

const activeTab = ref<CasesTab>('samples')

// Header is prefetched by `useProblemTab` so `headerData` will be populated
// by the time the page is ready. Read it directly from the store instead of
// peeking at `getRawTabState('header')`.
const title = computed(() => problemsStore.headerData?.title ?? '')

function mapExampleToTestCase(example: ProblemExample): CasesFormData['examples'][number] {
  return {
    id: example.id,
    input: example.input,
    output: example.output,
    explanation: example.explanation,
    inputs: example.inputs,
  }
}

async function handleSubmit(formData: CasesFormData) {
  try {
    await problemsStore.updateProblem(problemId.value, {
      examples: formData.examples.map((ex, idx) => ({
        id: ex.id || crypto.randomUUID(),
        input: ex.input,
        output: ex.output,
        explanation: ex.explanation,
        inputs: ex.inputs,
        order: idx,
      })),
      constraintsJson: formData.constraints,
      hints: formData.hints,
    })
    toast.success(t('problems.toast.updateSuccess'))
    router.push({ name: 'problem-detail', params: { id: problemId.value } })
  } catch (error) {
    console.error('Failed to update problem test cases:', error)
    toast.error(t('problems.toast.updateFailed'))
  }
}

const formattedProblem = computed(() => {
  if (!data.value) return undefined

  return {
    examples: data.value.examples?.map(mapExampleToTestCase) || [],
    constraints: data.value.detail?.constraintsJson || [],
    hints: data.value.detail?.hints || [],
    tags: data.value.tags?.map((t) => t.label) || [],
  }
})

function handleCancel() {
  router.push({ name: 'problem-detail', params: { id: problemId.value } })
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isReady ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="outline"
            size="icon"
            class="w-8"
            @click="router.push({ name: 'problem-detail', params: { id: problemId } })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>

          <div class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2">
              <span class="hidden" />
            </div>
            <h1 v-if="title" class="text-sm font-medium text-foreground truncate">
              {{ title }}
            </h1>
            <Skeleton v-else class="h-5 w-32" />
          </div>
        </div>
      </div>

      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('problems.edit.action') }}:</span
          >
          <span class="font-mono text-sm text-primary">{{ t('common.edit').toUpperCase() }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('problems.edit.section') }}:</span
          >
          <span class="font-mono text-sm text-foreground">{{ t('problems.edit.testCases') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <IconFlask class="h-4 w-4 text-muted-foreground" />
          <span class="text-xs text-muted-foreground">{{
            t('problems.edit.testCasesSubtitle')
          }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-muted-foreground">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('problems.edit.testCaseEditor')
          }}</span>
        </div>
      </div>
    </div>

    <div class="flex-1">
      <div v-if="loading" class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 rounded-full bg-muted border border-border dark:border-border flex items-center justify-center mb-3"
        >
          <div
            class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"
          ></div>
        </div>
        <h2 class="text-sm font-medium mb-1 font-mono">{{ t('problems.edit.loading') }}</h2>
        <p class="text-xs text-muted-foreground font-mono">// fetching problem data...</p>
      </div>

      <Tabs v-else-if="formattedProblem" v-model="activeTab" default-value="samples" class="w-full">
        <TabsList class="mb-4">
          <TabsTrigger value="samples">
            {{ t('testCases.tabs.samples') }}
          </TabsTrigger>
          <TabsTrigger value="hidden">
            {{ t('testCases.tabs.hidden') }}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="samples">
          <CasesForm :problem="formattedProblem" @submit="handleSubmit" @cancel="handleCancel" />
        </TabsContent>
        <TabsContent value="hidden">
          <HiddenTestCasesEditor :problem-id="problemId" />
        </TabsContent>
      </Tabs>
    </div>
  </div>
</template>
