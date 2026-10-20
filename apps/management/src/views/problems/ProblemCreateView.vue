<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { ArrowLeft as IconArrowLeft, Database as IconDatabase } from '@lucide/vue'

import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@ulticode/design-system/ui/button'
import ProblemForm from './components/ProblemForm.vue'
import type { ProblemFormData } from '@/lib/schemas/problem'

const router = useRouter()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof ProblemForm>>()
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

async function handleSubmit(data: ProblemFormData) {
  try {
    const problem = await problemsStore.createProblem({
      ...data,
      difficulty: data.difficulty,
      status: data.status,
      examples: data.examples.map((ex) => ({
        id: ex.id || crypto.randomUUID(),
        input: ex.input,
        output: ex.output,
        explanation: ex.explanation,
        inputs: ex.inputs,
      })),
    })
    toast.success(t('problems.toast.createSuccess'))
    router.push({ name: 'problem-detail', params: { id: problem.id } })
  } catch (error) {
    console.error('Failed to create problem:', error)
    toast.error(t('problems.toast.createFailed'))
  }
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <Button
            variant="outline"
            size="icon"
            class="w-8"
            @click="router.push({ name: 'problems' })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>
          <div class="h-4 w-px bg-border dark:bg-border" />
          <div class="flex items-center gap-2"></div>
          <h1 class="text-xl font-medium tracking-tight text-foreground">
            {{ t('problems.create.title') }}
          </h1>
        </div>
      </div>

      <!-- Page context -->
      <div
        class="px-4 lg:px-6 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-2 whitespace-nowrap border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('problems.edit.action') }}:</span
          >
          <span class="font-mono text-sm text-primary">{{ t('common.create').toUpperCase() }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('problems.edit.mode') }}:</span
          >
          <span class="font-mono text-sm text-foreground">{{ t('problems.edit.newProblem') }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('problems.edit.problemCreation')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1">
      <ProblemForm ref="formRef" @submit="handleSubmit">
        <template #cancel>
          <Button variant="outline" @click="router.push({ name: 'problems' })">
            <span class="uppercase tracking-wider">{{ t('common.cancel') }}</span>
          </Button>
        </template>
      </ProblemForm>
    </div>
  </div>
</template>
