<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  ArrowLeft as IconArrowLeft,
  Brackets as IconBrackets,
  Database as IconDatabase,
} from '@lucide/vue'

import { useProblemsStore } from '@/stores/admin/problems'
import { useProblemTab } from '../composables/useProblemTab'
import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import CodeForm from '../components/CodeForm.vue'
import type { CodeFormData } from '../components/CodeForm.vue'

const router = useRouter()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const { problemId, data, loading, isReady } = useProblemTab('code', (id) =>
  problemsStore.fetchCode(id),
)

// Header is prefetched by `useProblemTab` so `headerData` will be populated
// by the time the page is ready. Read it directly from the store instead of
// peeking at `getRawTabState('header')`.
const title = computed(() => problemsStore.headerData?.title ?? '')

async function handleSubmit(formData: CodeFormData) {
  try {
    await problemsStore.updateProblem(problemId.value, {
      languages: formData.languages.map((lang) => ({
        language: lang.language,
        starterCode: lang.starterCode,
      })),
    })
    toast.success(t('problems.toast.updateSuccess'))
    router.push({ name: 'problem-detail', params: { id: problemId.value, tab: 'code' } })
  } catch (error) {
    console.error('Failed to update problem languages:', error)
    toast.error(t('problems.toast.updateFailed'))
  }
}

const formattedProblem = computed(() => {
  if (!data.value) return undefined
  return {
    languages: data.value.languages || [],
  }
})

function handleCancel() {
  router.push({ name: 'problem-detail', params: { id: problemId.value, tab: 'code' } })
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
            @click="router.push({ name: 'problem-detail', params: { id: problemId, tab: 'code' } })"
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
          <span class="font-mono text-sm text-foreground">{{ t('problems.edit.code') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <IconBrackets class="h-4 w-4 text-muted-foreground" />
          <span class="text-xs text-muted-foreground">{{ t('problems.edit.codeSubtitle') }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-muted-foreground">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('problems.edit.languageConfig')
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

      <CodeForm
        v-else-if="formattedProblem"
        :problem="formattedProblem"
        @submit="handleSubmit"
        @cancel="handleCancel"
      />
    </div>
  </div>
</template>
