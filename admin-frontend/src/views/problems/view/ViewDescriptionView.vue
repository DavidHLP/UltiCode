<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import DescriptionDisplay from '../components/DescriptionDisplay.vue'

const route = useRoute()
const problemsStore = useProblemsStore()

const problemId = computed(() => route.params.id as string)
const problem = computed(() => problemsStore.currentProblem)

onMounted(async () => {
  if (problemId.value && !problem.value) {
    await problemsStore.fetchProblem(problemId.value)
  }
})
</script>

<template>
  <div v-if="problem" class="space-y-4">
    <!-- Breadcrumbs -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground">
      <router-link :to="{ name: 'problems' }" class="hover:text-foreground transition-colors">
        Problems
      </router-link>
      <span>/</span>
      <router-link
        :to="{ name: 'problem-detail', params: { id: problemId } }"
        class="hover:text-foreground transition-colors"
      >
        {{ problem.title }}
      </router-link>
      <span>/</span>
      <span class="text-foreground">Description</span>
    </div>

    <DescriptionDisplay :problem="problem" />
  </div>

  <!-- Loading State -->
  <div v-else class="text-center py-12">
    <div
      class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
    ></div>
    <p class="mt-2 text-muted-foreground">Loading problem...</p>
  </div>
</template>
