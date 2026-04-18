<!-- console/src/views/recommendations/RecommendationsView.vue -->
<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { useRecommendationStore } from "@/stores/recommendation";
import TagFilter from "./components/TagFilter.vue";
import SimilarProblemSearch from "./components/SimilarProblemSearch.vue";
import RecommendationResultList from "./components/RecommendationResultList.vue";
import { Skeleton } from "@/components/ui/skeleton";
import type { RecommendType, RecommendItem } from "@/types/recommendation";

const route = useRoute();
const { t } = useI18n();
const store = useRecommendationStore();

// Infer current type from route
const currentType = computed<RecommendType>(() => {
  const path = route.path;
  if (path.includes("weak-points")) return "weak-points";
  if (path.includes("challenge")) return "challenge";
  if (path.includes("similar")) return "similar";
  return "daily";
});

// Selected tags for filtering
const selectedTags = ref<string[]>([]);

// Selected problem ID for similar problems search
const selectedProblemId = ref<number | null>(null);

// Current type's recommendation data
const recommendations = computed<RecommendItem[]>(() => {
  switch (currentType.value) {
    case "daily":
      return store.daily;
    case "weak-points":
      return store.weakPoints;
    case "challenge":
      return store.challenge;
    case "similar":
      return store.similar;
    default:
      return [];
  }
});

// Section title label
const sectionLabel = computed(() => {
  switch (currentType.value) {
    case "daily":
      return "// DAILY RECOMMENDATIONS";
    case "weak-points":
      return "// WEAK POINT PRACTICE";
    case "challenge":
      return "// CHALLENGE MODE";
    case "similar":
      return "// SIMILAR PROBLEMS";
    default:
      return "// RECOMMENDATIONS";
  }
});

// Section description
const sectionDescription = computed(() => {
  switch (currentType.value) {
    case "daily":
      return t("recommendation.description.daily");
    case "weak-points":
      return t("recommendation.description.weakPoints");
    case "challenge":
      return t("recommendation.description.challenge");
    case "similar":
      return t("recommendation.description.similar");
    default:
      return "";
  }
});

// Load data
async function loadRecommendations() {
  switch (currentType.value) {
    case "daily":
      await store.loadDaily(10, false);
      break;
    case "weak-points":
      await store.loadWeakPoints(
        10,
        selectedTags.value.length > 0 ? selectedTags.value : undefined,
      );
      break;
    case "challenge":
      await store.loadChallenge(5);
      break;
    case "similar":
      if (selectedProblemId.value) {
        await store.loadSimilar(selectedProblemId.value, 5);
      }
      break;
  }
}

// Watch for type changes
watch(
  currentType,
  () => {
    selectedTags.value = [];
    selectedProblemId.value = null;
    loadRecommendations();
  },
  { immediate: true },
);

// Handle similar problem selection
function handleProblemSelect(problemId: number) {
  selectedProblemId.value = problemId;
  loadRecommendations();
}
</script>

<template>
  <div>
    <!-- Section header -->
    <header class="mb-6">
      <span class="terminal-label">{{ sectionLabel }}</span>
      <h1 class="text-2xl font-semibold tracking-tight mt-1">
        {{ t("recommendation.title") }}
      </h1>
      <p
        v-if="sectionDescription"
        class="mt-1.5 text-sm text-muted-foreground leading-relaxed"
      >
        {{ sectionDescription }}
      </p>
    </header>
    <div class="terminal-separator mb-6" />

    <!-- Filter bar -->
    <SimilarProblemSearch
      v-if="currentType === 'similar'"
      @select="handleProblemSelect"
    />
    <TagFilter v-else v-model="selectedTags" @refresh="loadRecommendations" />

    <!-- Loading state -->
    <div v-if="store.loading" class="animate-stagger grid gap-3">
      <div
        v-for="i in 3"
        :key="i"
        class="terminal-card precision-card p-5 flex items-center gap-4"
      >
        <Skeleton class="h-10 w-10 shrink-0 rounded-none" />
        <div class="flex-1 space-y-2.5">
          <Skeleton class="h-4 w-3/5 rounded-none" />
          <Skeleton class="h-3 w-2/5 rounded-none" />
        </div>
        <Skeleton class="h-8 w-16 rounded-none" />
      </div>
    </div>

    <!-- Error state -->
    <div
      v-else-if="store.error"
      class="terminal-card p-4 border-destructive/50 bg-destructive/5"
    >
      <div class="flex items-center gap-3">
        <span class="terminal-badge terminal-badge-error">ERROR</span>
        <span class="text-sm text-destructive">{{ store.error }}</span>
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-else-if="recommendations.length === 0"
      class="terminal-card p-12 text-center"
    >
      <div class="terminal-comment mb-2">NO_DATA_FOUND</div>
      <p class="text-sm text-muted-foreground">
        <template v-if="currentType === 'similar' && !selectedProblemId">
          {{ t("recommendation.empty.similar") }}
        </template>
        <template v-else>
          {{ t(`recommendation.empty.${currentType}`) }}
        </template>
      </p>
    </div>

    <!-- Recommendation list -->
    <RecommendationResultList
      v-else
      :items="recommendations"
      @row-click="(item: RecommendItem) => $router.push(`/problems/${item.slug}`)"
    />
  </div>
</template>
