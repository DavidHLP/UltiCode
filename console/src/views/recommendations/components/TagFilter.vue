<!-- console/src/views/recommendations/components/TagFilter.vue -->
<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { RefreshCw, ChevronDown, Check } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";

const selectedTags = defineModel<string[]>({ default: () => [] });

const emit = defineEmits<{
  refresh: [];
}>();

const { t } = useI18n();

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

function toggleTag(tag: string) {
  const index = selectedTags.value.indexOf(tag);
  if (index === -1) {
    selectedTags.value = [...selectedTags.value, tag];
  } else {
    selectedTags.value = selectedTags.value.filter((t) => t !== tag);
  }
}

function toggleAll() {
  if (selectedTags.value.length === availableTags.value.length) {
    selectedTags.value = [];
  } else {
    selectedTags.value = [...availableTags.value];
  }
}
</script>

<template>
  <div class="mb-6 flex flex-wrap items-center gap-3">
    <!-- Label -->
    <span class="terminal-label shrink-0">{{ t("recommendation.filter.tags") }}</span>

    <!-- Tag dropdown -->
    <DropdownMenu>
      <DropdownMenuTrigger as-child>
        <Button
          variant="outline"
          size="sm"
          class="h-7 gap-1.5 rounded-none border-[var(--silver-200)] bg-transparent font-mono text-[11px] uppercase tracking-[0.05em] text-muted-foreground hover:border-[var(--silver-400)] hover:text-foreground dark:border-[var(--silver-300)]"
        >
          {{ t("recommendation.filter.allTags") }}
          <span
            v-if="selectedTags.length > 0"
            class="ml-0.5 inline-flex h-4 w-4 items-center justify-center rounded-none bg-[var(--accent-primary)] font-mono text-[10px] text-[var(--background)]"
          >
            {{ selectedTags.length }}
          </span>
          <ChevronDown class="ml-0.5 h-3 w-3 shrink-0" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        class="w-[280px] overflow-hidden rounded-none border border-[var(--silver-200)] p-0 dark:border-[var(--silver-300)]"
      >
        <div class="flex flex-col">
          <!-- 全选 -->
          <button
            type="button"
            class="tag-dropdown-item flex items-center gap-2 px-3 py-2 text-left text-[11px] font-mono uppercase tracking-wide text-foreground hover:bg-[var(--muted)] transition-colors"
            @click="toggleAll"
          >
            <span
              class="flex h-4 w-4 items-center justify-center border border-[var(--silver-200)] dark:border-[var(--silver-300)]"
            >
              <Check
                v-if="selectedTags.length === availableTags.length"
                class="h-3 w-3 text-[var(--accent-primary)]"
              />
            </span>
            <span class="flex-1">{{ t("recommendation.filter.all") }}</span>
          </button>

          <DropdownMenuSeparator class="my-0 h-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)]" />

          <!-- Scrollable tag list -->
          <div class="max-h-48 overflow-y-auto py-1">
            <DropdownMenuCheckboxItem
              v-for="tag in availableTags"
              :key="tag"
              :checked="selectedTags.includes(tag)"
              class="tag-dropdown-item cursor-pointer px-3 py-1.5 text-[11px] font-mono uppercase tracking-wide"
              @click="toggleTag(tag)"
            >
              <template #check>
                <span
                  class="mr-2 flex h-4 w-4 items-center justify-center border border-[var(--silver-200)] dark:border-[var(--silver-300)]"
                >
                  <Check
                    v-if="selectedTags.includes(tag)"
                    class="h-3 w-3 text-[var(--accent-primary)]"
                  />
                </span>
              </template>
              {{ tag }}
            </DropdownMenuCheckboxItem>
          </div>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>

    <!-- Refresh button -->
    <Button variant="outline" size="sm" type="button" class="ml-auto shrink-0 rounded-none border-[var(--silver-200)] dark:border-[var(--silver-300)]" @click="emit('refresh')">
      <RefreshCw class="mr-1.5 h-3.5 w-3.5" />
      {{ t("recommendation.filter.refresh") }}
    </Button>
  </div>
</template>

<style scoped>
.tag-dropdown-item {
  width: 100%;
  outline: none;
}

.tag-dropdown-item:hover {
  background-color: var(--muted);
}

.tag-dropdown-item:focus-visible {
  background-color: var(--muted);
}
</style>

