<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import DescriptionForm from '../components/DescriptionForm.vue'
import type { DescriptionFormData } from '../components/DescriptionForm.vue'
import type { Problem } from '@/api/admin/problems'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const formRef = ref<InstanceType<typeof DescriptionForm>>()
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

async function handleSubmit(data: DescriptionFormData) {
  try {
    // Atomically update problem data and publish state to prevent race condition
    await problemsStore.updateProblemWithPublish(
      problemId.value,
      {
        slug: data.slug,
        title: data.title,
        difficulty: data.difficulty,
        status: data.status,
        is_premium: data.is_premium,
        summary: data.summary,
        content: data.content,
      },
      data.is_published,
    )

    toast.success(t('problems.toast.updateSuccess'))
    await loadData() // Refresh to show current state
    router.push({ name: 'problem-view-description', params: { id: problemId.value } })
  } catch (error) {
    console.error('Failed to update problem description:', error)
    toast.error(t('problems.toast.updateFailed'))
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
  }
})

function handleCancel() {
  router.push({ name: 'problem-view-description', params: { id: problemId.value } })
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <!-- Breadcrumbs -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground">
      <router-link :to="{ name: 'problems' }" class="hover:text-foreground transition-colors">
        {{ t('problems.title') }}
      </router-link>
      <span>/</span>
      <router-link
        :to="{ name: 'problem-view-description', params: { id: problemId } }"
        class="hover:text-foreground transition-colors"
      >
        {{ problemData?.title || t('common.loading') }}
      </router-link>
      <span>/</span>
      <span class="text-foreground">{{ t('problems.edit.description') }}</span>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">{{ t('problems.edit.description') }}</h1>
        <p class="text-muted-foreground">
          {{ t('problems.edit.descriptionSubtitle') }}
        </p>
      </div>
    </div>

    <div v-if="loadingData" class="text-center py-8">
      <div
        class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
      ></div>
      <p class="mt-2 text-muted-foreground">{{ t('problems.edit.loading') }}</p>
    </div>

    <DescriptionForm
      v-else-if="formattedProblem"
      :problem="formattedProblem"
      :is-edit="true"
      ref="formRef"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>
