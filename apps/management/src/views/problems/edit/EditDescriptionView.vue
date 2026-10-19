<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  ArrowLeft as IconArrowLeft,
  Pencil as IconPencil,
  Database as IconDatabase,
} from '@lucide/vue'

import { useProblemsStore } from '@/stores/admin/problems'
import { useProblemTab } from '../composables/useProblemTab'
import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import DescriptionForm from '../components/DescriptionForm.vue'
import type { DescriptionFormData } from '../components/DescriptionForm.vue'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

const router = useRouter()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const { problemId, data, loading, isReady } = useProblemTab('description', (id) =>
  problemsStore.fetchDescription(id),
)

async function handleSubmit(formData: DescriptionFormData) {
  try {
    await problemsStore.updateProblemWithPublish(
      problemId.value,
      {
        slug: formData.slug,
        title: formData.title,
        difficulty: (formData.difficulty === 'EASY'
          ? 'Easy'
          : formData.difficulty === 'MEDIUM'
            ? 'Medium'
            : formData.difficulty === 'HARD'
              ? 'Hard'
              : formData.difficulty) as Difficulty,
        isPremium: formData.isPremium,
        summary: formData.summary,
        content: formData.content,
        constraintsJson: formData.constraints,
        hints: formData.hints,
        examples: formData.examples,
        tags: formData.tags || [],
        languages:
          (
            formData as DescriptionFormData & {
              languages?: Array<{ language: string; starterCode: string }>
            }
          ).languages || [],
      },
      formData.isPublished,
    )

    toast.success(t('problems.toast.updateSuccess'))
    router.push({ name: 'problem-detail', params: { id: problemId.value } })
  } catch (error) {
    console.error('Failed to update problem description:', error)
    toast.error(t('problems.toast.updateFailed'))
  }
}

const formattedProblem = computed(() => {
  const problem = data.value
  if (!problem) return undefined

  return {
    slug: problem.slug,
    title: problem.title,
    difficulty: problem.difficulty as Difficulty,
    status: problem.status as ProblemStatus,
    isPremium: problem.isPremium,
    isPublished: problem.isPublished,
    summary: problem.detail?.summary ?? '',
    content: problem.detail?.content ?? '',
    examples:
      problem.examples?.map((ex) => ({
        input: ex.input,
        output: ex.output,
        explanation: ex.explanation || '',
        inputs: ex.inputs,
      })) || [],
    constraints: problem.detail?.constraintsJson || [],
    hints: problem.detail?.hints || [],
    tags: problem.tags?.map((t) => t.label) || [],
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
            <h1 v-if="data" class="text-sm font-medium text-foreground truncate">
              {{ data.title }}
            </h1>
            <Skeleton v-else class="h-5 w-32" />
          </div>
        </div>
      </div>

      <div
        class="px-4 lg:px-6 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2 whitespace-nowrap">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('problems.edit.action') }}:</span
          >
          <span class="font-mono text-sm text-primary">{{ t('common.edit').toUpperCase() }}</span>
        </div>
        <div class="flex items-center gap-2 whitespace-nowrap">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('problems.edit.section') }}:</span
          >
          <span class="font-mono text-sm text-foreground">{{
            t('problems.edit.description')
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <IconPencil class="h-4 w-4 text-muted-foreground" />
          <span class="text-xs text-muted-foreground">{{
            t('problems.edit.descriptionSubtitle')
          }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-muted-foreground">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('problems.edit.problemEditor')
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

      <DescriptionForm
        v-else-if="formattedProblem"
        :problem="formattedProblem"
        :is-edit="true"
        @submit="handleSubmit"
        @cancel="handleCancel"
      />
    </div>
  </div>
</template>
