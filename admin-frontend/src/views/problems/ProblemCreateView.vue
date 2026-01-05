<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import ProblemForm from './components/ProblemForm.vue'
import type { ProblemFormData } from '@/lib/schemas/problem'

const router = useRouter()
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
        input_text: ex.input,
        output_text: ex.output,
        explanation: ex.explanation,
        order: idx,
      })),
    })
    toast.success('Problem created successfully')
    router.push({ name: 'problem-detail', params: { id: problem.id } })
  } catch (error) {
    console.error('Failed to create problem:', error)
    toast.error('Failed to create problem')
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Create Problem</h1>
        <p class="text-muted-foreground">Add a new problem to the platform</p>
      </div>
      <Button variant="outline" @click="router.push({ name: 'problems' })">Cancel</Button>
    </div>

    <ProblemForm ref="formRef" @submit="handleSubmit">
      <template #cancel>
        <Button variant="outline" @click="router.push({ name: 'problems' })">Cancel</Button>
      </template>
    </ProblemForm>
  </div>
</template>
