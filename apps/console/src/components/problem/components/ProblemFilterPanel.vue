<script setup lang="ts">
import type { Component } from "vue";
import {
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuCheckboxItem,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import {
  CategoryFilter,
  DataTableToolbar,
  TagFilter,
} from "@/components/common/data-table";
import CheckIcon from "~icons/radix-icons/check";
import { Shuffle } from "lucide-vue-next";
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
  <div class="terminal-card animate-stagger problem-explorer-controls">
    <CategoryFilter
      class="problem-category-filter"
      :categories="categoryOptions"
      :model-value="selectedCategory"
      @update:model-value="emit('update:selectedCategory', $event)"
    />

    <DataTableToolbar
      class="problem-filter-toolbar"
      :model-value="searchQuery"
      :placeholder="t('problem.list.searchPlaceholder')"
      :filter-label="t('problem.explorer.filters')"
      :filter-icon-only="true"
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
          :checked="selectedStatus.includes('solved')"
          @click="
            emit('toggleStatus', 'solved', !selectedStatus.includes('solved'))
          "
        >
          <span class="flex items-center w-full">
            {{ t("problem.status.solved") }}
            <CheckIcon
              v-if="selectedStatus.includes('solved')"
              class="ml-auto h-4 w-4"
            />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :checked="selectedStatus.includes('attempted')"
          @click="
            emit(
              'toggleStatus',
              'attempted',
              !selectedStatus.includes('attempted'),
            )
          "
        >
          <span class="flex items-center w-full">
            {{ t("problem.status.attempted") }}
            <CheckIcon
              v-if="selectedStatus.includes('attempted')"
              class="ml-auto h-4 w-4"
            />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :checked="selectedStatus.includes('todo')"
          @click="
            emit('toggleStatus', 'todo', !selectedStatus.includes('todo'))
          "
        >
          <span class="flex items-center w-full">
            {{ t("problem.status.todo") }}
            <CheckIcon
              v-if="selectedStatus.includes('todo')"
              class="ml-auto h-4 w-4"
            />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{{
          t("problem.explorer.difficulty")
        }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuCheckboxItem
          :checked="selectedDifficulty.includes('Easy')"
          @click="
            emit(
              'toggleDifficulty',
              'Easy',
              !selectedDifficulty.includes('Easy'),
            )
          "
        >
          <span class="flex items-center w-full">
            {{ t("problem.difficulty.easy") }}
            <CheckIcon
              v-if="selectedDifficulty.includes('Easy')"
              class="ml-auto h-4 w-4"
            />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :checked="selectedDifficulty.includes('Medium')"
          @click="
            emit(
              'toggleDifficulty',
              'Medium',
              !selectedDifficulty.includes('Medium'),
            )
          "
        >
          <span class="flex items-center w-full">
            {{ t("problem.difficulty.medium") }}
            <CheckIcon
              v-if="selectedDifficulty.includes('Medium')"
              class="ml-auto h-4 w-4"
            />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :checked="selectedDifficulty.includes('Hard')"
          @click="
            emit(
              'toggleDifficulty',
              'Hard',
              !selectedDifficulty.includes('Hard'),
            )
          "
        >
          <span class="flex items-center w-full">
            {{ t("problem.difficulty.hard") }}
            <CheckIcon
              v-if="selectedDifficulty.includes('Hard')"
              class="ml-auto h-4 w-4"
            />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{{
          t("problem.explorer.premium")
        }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuCheckboxItem
          :checked="showPremium === false"
          @click="emit('togglePremium', false, !(showPremium === false))"
        >
          <span class="flex items-center w-full">
            {{ t("problem.explorer.free") }}
            <CheckIcon v-if="showPremium === false" class="ml-auto h-4 w-4" />
          </span>
        </DropdownMenuCheckboxItem>
        <DropdownMenuCheckboxItem
          :checked="showPremium === true"
          @click="emit('togglePremium', true, !(showPremium === true))"
        >
          <span class="flex items-center w-full">
            {{ t("problem.explorer.premium") }}
            <CheckIcon v-if="showPremium === true" class="ml-auto h-4 w-4" />
          </span>
        </DropdownMenuCheckboxItem>
      </template>
      <template #actions>
        <Button
          variant="outline"
          size="icon"
          :aria-label="t('problem.explorer.pickOne')"
          :title="t('problem.explorer.pickOne')"
          class="h-9 rounded-md border-border-control bg-surface-highlight font-mono text-xs font-bold uppercase tracking-wider text-foreground-strong hover:bg-surface-highlight/80 transition-all cursor-pointer"
          @click="emit('pickOne')"
        >
          <Shuffle class="h-4 w-4" aria-hidden="true" />
        </Button>
      </template>
    </DataTableToolbar>

    <div class="problem-tag-shelf">
      <div class="problem-tag-label">
        <span class="problem-tag-mark" aria-hidden="true" />
        <span>{{ t("problem.detail.tags") }}</span>
      </div>
      <TagFilter
        class="problem-tag-filter"
        :model-value="$props.selectedTags"
        @update:model-value="emit('update:selectedTags', $event)"
        :popular-tags="popularTags"
        :other-tags="otherTags"
        :show-more-label="t('problem.explorer.showMoreTags')"
      />
    </div>
  </div>
</template>

<style scoped>
.problem-explorer-controls {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 1rem 1.25rem 1.125rem;
  border-color: var(--border-subtle);
  background: var(--surface-elevated);
  box-shadow: var(--shadow-float);
}

.problem-tag-mark {
  display: inline-block;
  width: 0.4rem;
  height: 0.4rem;
  flex: none;
  border-radius: 50%;
  background: var(--primary);
}

.problem-explorer-controls :deep(.problem-category-filter) {
  display: flex;
  align-items: stretch;
  flex-wrap: nowrap;
  gap: 0.25rem;
  overflow-x: auto;
  margin: 0 0 0.75rem;
  padding: 0;
  border: 0;
  border-bottom: 1px solid var(--border-subtle);
  border-radius: 0;
  background: transparent;
}

.problem-explorer-controls :deep(.problem-category-filter .terminal-tab) {
  flex: 0 0 auto;
  min-width: max-content;
  height: 2.75rem;
  padding: 0.625rem 0.75rem;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: var(--foreground-muted);
  font-family: var(--uc-font-ui);
  font-size: var(--uc-type-control-size);
  font-weight: var(--uc-type-control-weight);
  line-height: var(--uc-type-control-line-height);
  letter-spacing: var(--uc-tracking-normal);
}

.problem-explorer-controls :deep(.problem-category-filter .terminal-tab:hover) {
  border-bottom-color: var(--border-control);
  background: transparent;
  color: var(--foreground-strong);
}

.problem-explorer-controls
  :deep(.problem-category-filter .terminal-tab.bg-surface-highlight) {
  border-bottom-color: var(--primary);
  background: transparent;
  color: var(--foreground-strong);
  font-weight: var(--uc-font-weight-semibold);
}

.problem-explorer-controls :deep(.problem-filter-toolbar) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.problem-explorer-controls :deep(.problem-filter-toolbar > div:first-child) {
  max-width: none;
}

.problem-explorer-controls :deep(.problem-filter-toolbar > div:last-child) {
  width: auto;
  padding-bottom: 0;
}

.problem-explorer-controls :deep(input),
.problem-explorer-controls :deep(button),
.problem-explorer-controls :deep([role="button"]),
.problem-explorer-controls :deep([data-slot="badge"]) {
  font-family: var(--uc-font-ui);
}

.problem-explorer-controls :deep(.problem-filter-toolbar input) {
  height: 2.5rem;
  border-color: var(--border-control);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--foreground);
  font-size: var(--uc-type-control-size);
  line-height: var(--uc-type-control-line-height);
}

.problem-explorer-controls :deep(.problem-filter-toolbar button) {
  height: 2.5rem;
  min-width: 2.5rem;
  border-color: var(--border-subtle);
  border-radius: var(--radius-md);
  background: transparent;
  box-shadow: none;
  color: var(--foreground-muted);
  font-size: var(--uc-type-control-size);
  line-height: var(--uc-type-control-line-height);
  letter-spacing: var(--uc-tracking-normal);
  text-transform: none;
}

.problem-explorer-controls :deep(.problem-filter-toolbar button:hover) {
  border-color: var(--border-control);
  background: var(--surface-highlight);
  color: var(--foreground-strong);
}

.problem-explorer-controls :deep(.problem-filter-toolbar [data-slot="badge"]) {
  border-radius: var(--radius-md);
  background: var(--surface-highlight);
  font-size: var(--uc-type-label-size);
  line-height: var(--uc-type-label-line-height);
}

.problem-tag-shelf {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  align-items: start;
  gap: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-subtle);
}

.problem-tag-label {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 1.875rem;
  color: var(--foreground-muted);
  font-family: var(--uc-font-ui);
  font-size: var(--uc-type-label-size);
  font-weight: var(--uc-type-label-weight);
  line-height: var(--uc-type-label-line-height);
}

.problem-explorer-controls :deep(.problem-tag-filter) {
  width: 100%;
}

.problem-explorer-controls :deep(.problem-tag-filter [data-slot="badge"]) {
  min-height: 1.875rem;
  padding: 0.375rem 0.625rem;
  border-color: var(--border-subtle);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--foreground-muted);
  font-size: var(--uc-type-label-size);
  font-weight: var(--uc-type-label-weight);
  line-height: var(--uc-type-label-line-height);
}

.problem-explorer-controls
  :deep(.problem-tag-filter [data-slot="badge"]:hover) {
  border-color: var(--border-control);
  background: var(--surface-highlight);
  color: var(--foreground-strong);
}

.problem-explorer-controls
  :deep(
    .problem-tag-filter [data-slot="badge"][class~="bg-surface-highlight"]
  ) {
  border-color: var(--primary);
  background: var(--surface-highlight);
  color: var(--foreground-strong);
}

.problem-explorer-controls :deep(.problem-tag-filter > div:last-child) {
  padding-top: 0.5rem;
}

.problem-explorer-controls :deep(.problem-tag-filter button) {
  height: auto;
  min-height: 1.875rem;
  padding: 0.375rem 0.25rem;
  border-radius: 0;
  color: var(--foreground-muted);
  font-size: var(--uc-type-label-size);
  line-height: var(--uc-type-label-line-height);
  letter-spacing: var(--uc-tracking-normal);
}

.problem-explorer-controls :deep(.problem-tag-filter button:hover) {
  background: transparent;
  color: var(--foreground-strong);
}

.problem-explorer-controls :deep(.border-dashed) {
  border-style: solid;
  border-color: var(--border);
}

.problem-explorer-controls :deep(input) {
  border-color: var(--border-control);
  background: var(--surface-sunken);
}

.problem-explorer-controls :deep(input:focus-visible) {
  border-color: var(--ring);
  box-shadow: 0 0 0 2px var(--accent-glow);
}

@media (max-width: 640px) {
  .problem-explorer-controls {
    padding: 0.875rem;
  }

  .problem-explorer-controls :deep(.problem-filter-toolbar) {
    grid-template-columns: 1fr;
    gap: 0.75rem;
  }

  .problem-explorer-controls :deep(.problem-filter-toolbar > div:last-child) {
    justify-content: flex-end;
  }

  .problem-tag-shelf {
    grid-template-columns: 1fr;
    gap: 0.5rem;
  }
}
</style>
