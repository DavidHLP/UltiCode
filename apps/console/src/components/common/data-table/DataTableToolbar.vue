<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Search, ListFilter, X } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

defineProps<{
  modelValue: string; // Search query
  placeholder?: string;
  filterLabel?: string;
  filterIconOnly?: boolean;
  activeFilterCount?: number;
  showClear?: boolean;
  clearLabel?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
  clear: [];
}>();
</script>

<template>
  <div
    class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"
  >
    <!-- Left: Search -->
    <div class="relative w-full max-w-md">
      <Search
        class="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground"
      />
      <Input
        :model-value="modelValue"
        @update:model-value="(v) => emit('update:modelValue', v as string)"
        :placeholder="placeholder || 'Search...'"
        class="data-table-toolbar-search terminal-input h-9 text-xs rounded-md border-border-control bg-surface-sunken text-foreground focus-visible:border-ring focus-visible:ring-ring focus-visible:ring-2"
      />
    </div>

    <!-- Right: Actions -->
    <div
      class="flex w-full items-center gap-2 overflow-x-auto pb-1 lg:w-auto lg:pb-0"
    >
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button
            :size="filterIconOnly ? 'icon' : 'default'"
            :aria-label="filterIconOnly ? filterLabel || 'Filters' : undefined"
            :title="filterIconOnly ? filterLabel || 'Filters' : undefined"
            variant="outline"
            class="h-9 gap-1.5 rounded-md border-border-control bg-surface-highlight text-xs text-foreground-strong hover:bg-surface-highlight/80 cursor-pointer"
            :class="filterIconOnly ? 'p-0' : undefined"
          >
            <ListFilter
              class="h-3.5 w-3.5 text-muted-foreground"
              aria-hidden="true"
            />
            <span v-if="!filterIconOnly">{{ filterLabel || "Filters" }}</span>
            <Badge
              v-if="(activeFilterCount || 0) > 0"
              variant="secondary"
              class="ml-0.5 h-4 rounded-sm border border-border-control bg-surface-elevated px-1.5 text-2xs font-bold text-foreground-strong"
            >
              {{ activeFilterCount }}
            </Badge>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent class="w-56" align="end">
          <slot name="filters" />
        </DropdownMenuContent>
      </DropdownMenu>

      <slot name="actions" />

      <Button
        v-if="showClear"
        variant="ghost"
        size="icon"
        class="h-9 w-9 rounded-md border-border-control text-muted-foreground hover:bg-surface-highlight hover:text-foreground-strong cursor-pointer"
        @click="emit('clear')"
        :aria-label="clearLabel || 'Clear filters'"
      >
        <X class="h-3.5 w-3.5" />
      </Button>
    </div>
  </div>
</template>

<style scoped>
.data-table-toolbar-search {
  padding-left: calc(var(--uc-layout-control-padding-inline) + 1.25rem);
}
</style>
