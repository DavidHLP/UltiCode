<script setup lang="ts">
import type { Problem } from "@/types/problem";
import type { ProblemExplorerProps } from "./type";
import {
  useProblemExplorer,
  type EnrichedProblem,
} from "./composables/useProblemExplorer";
import ProblemFilterPanel from "./components/ProblemFilterPanel.vue";
import ProblemResultList from "./components/ProblemResultList.vue";
import { useRouter } from "vue-router";

const router = useRouter();

const props = defineProps<ProblemExplorerProps>();

const emit = defineEmits<{
  remove: [problem: Problem];
}>();

const {
  searchQuery,
  selectedTags,
  selectedStatus,
  selectedDifficulty,
  showPremium,
  selectedCategory,
  categoryOptions,
  popularTags,
  otherTags,
  hasMore,
  displayedProblems,
  columns,
  activeFilterCount,
  hasActiveFilters,
  toggleStatus,
  toggleDifficulty,
  togglePremium,
  setSearchQuery,
  setSelectedCategory,
  setSelectedTags,
  clearFilters,
  pickOne,
  loadMore,
} = useProblemExplorer(props);

const handleRowClick = (problem: EnrichedProblem) => {
  if (problem && problem.slug) {
    router.push({ name: "problem-detail", params: { slug: problem.slug } });
  }
};
</script>

<template>
  <section class="flex flex-col gap-6">
    <slot name="header" />

    <ProblemFilterPanel
      :selected-category="selectedCategory"
      :search-query="searchQuery"
      :active-filter-count="activeFilterCount"
      :has-active-filters="hasActiveFilters"
      :selected-status="selectedStatus"
      :selected-difficulty="selectedDifficulty"
      :show-premium="showPremium"
      :category-options="categoryOptions"
      :popular-tags="popularTags"
      :other-tags="otherTags"
      :selected-tags="selectedTags"
      @update:selected-category="setSelectedCategory"
      @update:search-query="setSearchQuery"
      @update:selected-tags="setSelectedTags"
      @clear="clearFilters"
      @toggle-status="toggleStatus"
      @toggle-difficulty="toggleDifficulty"
      @toggle-premium="togglePremium"
      @pick-one="pickOne"
    />

    <ProblemResultList
      :displayed-problems="displayedProblems"
      :columns="columns"
      :has-more="hasMore"
      :editable="props.editable"
      @load-more="loadMore"
      @remove="emit('remove', $event)"
      @row-click="handleRowClick"
    />
  </section>
</template>
