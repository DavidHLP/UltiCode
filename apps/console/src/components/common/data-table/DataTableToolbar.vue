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
        class="pl-8.5 h-9 text-xs rounded-none border-border/60 bg-[var(--surface-sunken)] focus-visible:border-[var(--accent-electric)] focus-visible:ring-[var(--accent-electric-glow)] focus-visible:ring-1 text-foreground"
      />
    </div>

    <!-- Right: Actions -->
    <div
      class="flex w-full items-center gap-2 overflow-x-auto pb-1 lg:w-auto lg:pb-0"
    >
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button
            variant="outline"
            class="h-9 gap-1.5 border border-border/60 rounded-none text-xs hover:bg-[var(--surface-sunken)] bg-card text-foreground cursor-pointer"
          >
            <ListFilter class="h-3.5 w-3.5 text-muted-foreground" />
            {{ filterLabel || "Filters" }}
            <Badge
              v-if="(activeFilterCount || 0) > 0"
              variant="secondary"
              class="ml-0.5 h-4 px-1.5 text-2xs font-bold rounded-none bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] border border-[var(--accent-electric)]/20"
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
        class="h-9 w-9 rounded-none border border-border/60 hover:bg-[var(--surface-sunken)] hover:text-foreground text-muted-foreground cursor-pointer"
        @click="emit('clear')"
        :aria-label="clearLabel || 'Clear filters'"
      >
        <X class="h-3.5 w-3.5" />
      </Button>
    </div>
  </div>
</template>
