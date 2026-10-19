<script setup lang="ts">
import type { Component } from "vue";
import {
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuCheckboxItem,
} from "@ulticode/design-system/ui/dropdown-menu";
import { Button } from "@ulticode/design-system/ui/button";
import {
  CategoryFilter,
  DataTableToolbar,
  TagFilter,
} from "@/components/common/data-table";
import { Shuffle } from "@lucide/vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

defineProps<{
  selectedCategory: string;
  searchQuery: string;
  activeFilterCount: number;
  hasActiveFilters: boolean;
  selectedStatus: string[];
  selectedDifficulty: string[];
  showPremium: boolean | null;
  selectedTags: string[];
  categoryOptions: { label: string; value: string; icon?: Component }[];
  popularTags: string[];
  otherTags: string[];
}>();

const emit = defineEmits<{
  "update:selectedCategory": [value: string];
  "update:searchQuery": [value: string];
  clear: [];
  toggleStatus: [value: string, checked: boolean];
  toggleDifficulty: [value: string, checked: boolean];
  togglePremium: [value: boolean, checked: boolean];
  "update:selectedTags": [value: string[]];
  pickOne: [];
}>();
</script>

<template>
  <div
    class="rounded-xl border bg-card text-card-foreground flex flex-col gap-4 rounded-xl border bg-card p-4"
  >
    <CategoryFilter
      class="flex flex-wrap gap-2"
      :categories="categoryOptions"
      :model-value="selectedCategory"
      @update:model-value="emit('update:selectedCategory', $event)"
    />

    <DataTableToolbar
      class="grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]"
      :model-value="searchQuery"
      :placeholder="t('problem.list.searchPlaceholder')"
      :filter-label="t('problem.explorer.filters')"
      :active-filter-count="activeFilterCount"
      :show-clear="hasActiveFilters"
      :clear-label="
        t('common.actions.clear') + ' ' + t('problem.explorer.filters')
      "
      @update:model-value="emit('update:searchQuery', $event)"
      @clear="emit('clear')"
    >
      <template #filters>
        <DropdownMenuLabel>{{
          t("problem.explorer.status")
        }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuCheckboxItem
          :model-value="selectedStatus.includes('solved')"
          @update:model-value="emit('toggleStatus', 'solved', $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.status.solved")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :model-value="selectedStatus.includes('attempted')"
          @update:model-value="emit('toggleStatus', 'attempted', $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.status.attempted")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :model-value="selectedStatus.includes('todo')"
          @update:model-value="emit('toggleStatus', 'todo', $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.status.todo")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{{
          t("problem.explorer.difficulty")
        }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuCheckboxItem
          :model-value="selectedDifficulty.includes('Easy')"
          @update:model-value="emit('toggleDifficulty', 'Easy', $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.difficulty.easy")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :model-value="selectedDifficulty.includes('Medium')"
          @update:model-value="emit('toggleDifficulty', 'Medium', $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.difficulty.medium")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :model-value="selectedDifficulty.includes('Hard')"
          @update:model-value="emit('toggleDifficulty', 'Hard', $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.difficulty.hard")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{{
          t("problem.explorer.premium")
        }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuCheckboxItem
          :model-value="showPremium === false"
          @update:model-value="emit('togglePremium', false, $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.explorer.free")
          }}</span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :model-value="showPremium === true"
          @update:model-value="emit('togglePremium', true, $event)"
        >
          <span class="flex items-center w-full">{{
            t("problem.explorer.premium")
          }}</span>
        </DropdownMenuCheckboxItem>
      </template>
      <template #actions>
        <Button
          variant="outline"
          size="icon"
          :aria-label="t('problem.explorer.pickOne')"
          :title="t('problem.explorer.pickOne')"
          class="cursor-pointer"
          @click="emit('pickOne')"
        >
          <Shuffle class="h-4 w-4" aria-hidden="true" />
        </Button>
      </template>
    </DataTableToolbar>

    <div class="grid gap-3 border-t pt-4 sm:grid-cols-[max-content_1fr]">
      <div class="flex items-center gap-2 text-sm font-medium">
        <span class="size-1.5 rounded-full bg-primary" aria-hidden="true" />
        <span>{{ t("problem.detail.tags") }}</span>
      </div>
      <TagFilter
        class="min-w-0"
        :model-value="$props.selectedTags"
        @update:model-value="emit('update:selectedTags', $event)"
        :popular-tags="popularTags"
        :other-tags="otherTags"
        :show-more-label="t('problem.explorer.showMoreTags')"
      />
    </div>
  </div>
</template>
