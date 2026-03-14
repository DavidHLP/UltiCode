<!-- console/src/views/recommendations/components/SimilarProblemSearch.vue -->
<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useDebounceFn } from "@vueuse/core";
import { searchProblems } from "@/api/problem";
import {
  Combobox,
  ComboboxAnchor,
  ComboboxInput,
  ComboboxList,
  ComboboxItem,
  ComboboxEmpty,
} from "@/components/ui/combobox";
import type { Problem } from "@/types/problem";

const emit = defineEmits<{
  select: [problemId: number];
}>();

const { t } = useI18n();

const searchQuery = ref("");
const searchResults = ref<Problem[]>([]);
const selectedProblem = ref<Problem | null>(null);
const isSearching = ref(false);

// Search problems with debounce
const debouncedSearch = useDebounceFn(async (query: string) => {
  if (query.length < 2) {
    searchResults.value = [];
    return;
  }

  isSearching.value = true;
  try {
    searchResults.value = await searchProblems(query);
  } finally {
    isSearching.value = false;
  }
}, 300);

// Watch search input changes
watch(searchQuery, debouncedSearch);

// Watch selection changes
watch(selectedProblem, (problem) => {
  if (problem) {
    emit("select", problem.id);
  }
});
</script>

<template>
  <div class="mb-6">
    <Combobox v-model="selectedProblem">
      <ComboboxAnchor>
        <ComboboxInput
          v-model="searchQuery"
          :placeholder="t('recommendation.search.placeholder')"
        />
      </ComboboxAnchor>
      <ComboboxList>
        <ComboboxEmpty>
          {{ isSearching ? "..." : t("recommendation.search.noResults") }}
        </ComboboxEmpty>
        <ComboboxItem
          v-for="problem in searchResults"
          :key="problem.id"
          :value="problem"
        >
          {{ problem.title }}
        </ComboboxItem>
      </ComboboxList>
    </Combobox>
  </div>
</template>
