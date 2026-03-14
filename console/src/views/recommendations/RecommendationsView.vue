<!-- console/src/views/recommendations/RecommendationsView.vue -->
<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useRecommendationStore } from '@/stores/recommendation'
import RecommendationNav from './components/RecommendationNav.vue'
import ProblemCard from './components/ProblemCard.vue'
import TagFilter from './components/TagFilter.vue'
import SimilarProblemSearch from './components/SimilarProblemSearch.vue'
import { Skeleton } from '@/components/ui/skeleton'
import type { RecommendType, RecommendItem } from '@/types/recommendation'

const route = useRoute()
const { t } = useI18n()
const store = useRecommendationStore()

// Infer current type from route
const currentType = computed<RecommendType>(() => {
  const path = route.path
  if (path.includes('weak-points')) return 'weak-points'
  if (path.includes('challenge')) return 'challenge'
  if (path.includes('similar')) return 'similar'
  return 'daily'
})

// Selected tags for filtering
const selectedTags = ref<string[]>([])

// Selected problem ID for similar problems search
const selectedProblemId = ref<number | null>(null)

// Current type's recommendation data
const recommendations = computed<RecommendItem[]>(() => {
  switch (currentType.value) {
    case 'daily': return store.daily
    case 'weak-points': return store.weakPoints
    case 'challenge': return store.challenge
    case 'similar': return store.similar
    default: return []
  }
})

// Load data
async function loadRecommendations() {
  switch (currentType.value) {
    case 'daily':
      await store.loadDaily(10, false)
      break
    case 'weak-points':
      await store.loadWeakPoints(10, selectedTags.value.length > 0 ? selectedTags.value : undefined)
      break
    case 'challenge':
      await store.loadChallenge(5)
      break
    case 'similar':
      if (selectedProblemId.value) {
        await store.loadSimilar(selectedProblemId.value, 5)
      }
      break
  }
}

// Watch for type changes
watch(currentType, () => {
  selectedTags.value = []
  selectedProblemId.value = null
  loadRecommendations()
}, { immediate: true })

// Handle similar problem selection
function handleProblemSelect(problemId: number) {
  selectedProblemId.value = problemId
  loadRecommendations()
}
</script>

<template>
  <div class="flex gap-6">
    <!-- Left navigation (currentType is computed, so use :model-value, not v-model) -->
    <RecommendationNav :model-value="currentType" />

    <!-- Right content area -->
    <div class="flex-1">
      <h1 class="mb-6 text-2xl font-bold">{{ t('recommendation.title') }}</h1>

      <!-- Filter bar -->
      <SimilarProblemSearch
        v-if="currentType === 'similar'"
        @select="handleProblemSelect"
      />
      <TagFilter
        v-else
        v-model="selectedTags"
        @refresh="loadRecommendations"
      />

      <!-- Loading state -->
      <div v-if="store.loading" class="grid gap-4">
        <Skeleton v-for="i in 3" :key="i" class="h-32 rounded-lg" />
      </div>

      <!-- Error state -->
      <div v-else-if="store.error" class="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-destructive">
        {{ store.error }}
      </div>

      <!-- Empty state -->
      <div v-else-if="recommendations.length === 0" class="rounded-lg border bg-muted/50 p-8 text-center text-muted-foreground">
        <p v-if="currentType === 'similar' && !selectedProblemId">
          {{ t('recommendation.empty.similar') }}
        </p>
        <p v-else>
          {{ t(`recommendation.empty.${currentType}`) }}
        </p>
      </div>

      <!-- Recommendation list -->
      <div v-else class="grid gap-4">
        <ProblemCard
          v-for="item in recommendations"
          :key="item.problemId"
          :item="item"
        />
      </div>
    </div>
  </div>
</template>
