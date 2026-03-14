<!-- console/src/views/recommendations/components/TagFilter.vue -->
<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { RefreshCw } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
  Combobox,
  ComboboxAnchor,
  ComboboxTrigger,
  ComboboxList,
  ComboboxItem,
  ComboboxEmpty,
} from "@/components/ui/combobox";

const selectedTags = defineModel<string[]>({ default: () => [] });

const emit = defineEmits<{
  refresh: [];
}>();

const { t } = useI18n();

// Available tags list (can be fetched from API or use predefined list)
const availableTags = ref<string[]>([
  "Array",
  "String",
  "Linked List",
  "Tree",
  "Graph",
  "Dynamic Programming",
  "Greedy",
  "Binary Search",
  "DFS",
  "BFS",
  "Hash Table",
  "Stack",
  "Queue",
  "Heap",
  "Sorting",
  "Backtracking",
]);
</script>

<template>
  <div class="mb-6 flex items-center gap-4">
    <Combobox v-model="selectedTags" multiple>
      <ComboboxAnchor>
        <ComboboxTrigger class="w-[200px]">
          {{
            selectedTags.length > 0
              ? selectedTags.join(", ")
              : t("recommendation.filter.tags")
          }}
        </ComboboxTrigger>
      </ComboboxAnchor>
      <ComboboxList>
        <ComboboxEmpty>
          {{ t("recommendation.search.noResults") }}
        </ComboboxEmpty>
        <ComboboxItem v-for="tag in availableTags" :key="tag" :value="tag">
          {{ tag }}
        </ComboboxItem>
      </ComboboxList>
    </Combobox>

    <Button variant="outline" size="sm" type="button" @click="emit('refresh')">
      <RefreshCw class="mr-2 h-4 w-4" />
      {{ t("recommendation.filter.refresh") }}
    </Button>
  </div>
</template>
