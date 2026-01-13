<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import ProblemForm from './components/ProblemForm.vue'
import type { ProblemFormData } from '@/lib/schemas/problem'

const router = useRouter()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof ProblemForm>>()

async function handleSubmit(data: ProblemFormData) {
  try {
    const problem = await problemsStore.createProblem({
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
    toast.success(t('problems.toast.createSuccess'))
    router.push({ name: 'problem-view-description', params: { id: problem.id } })
  } catch (error) {
    console.error('Failed to create problem:', error)
    toast.error(t('problems.toast.createFailed'))
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">{{ t('problems.create.title') }}</h1>
        <p class="text-muted-foreground">{{ t('problems.create.description') }}</p>
      </div>
      <Button variant="outline" @click="router.push({ name: 'problems' })">{{
        t('common.cancel')
      }}</Button>
    </div>

    <ProblemForm ref="formRef" @submit="handleSubmit">
      <template #cancel>
        <Button variant="outline" @click="router.push({ name: 'problems' })">{{
          t('common.cancel')
        }}</Button>
      </template>
    </ProblemForm>
  </div>
</template>
