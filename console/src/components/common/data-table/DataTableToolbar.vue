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
    class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between"
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
        class="pl-8.5 h-9 text-xs rounded-full"
      />
    </div>

    <!-- Right: Actions -->
    <div class="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button
            variant="outline"
            class="h-9 gap-1.5 border-dashed rounded-full text-xs"
          >
            <ListFilter class="h-3.5 w-3.5" />
            {{ filterLabel || "Filters" }}
            <Badge
              v-if="(activeFilterCount || 0) > 0"
              variant="secondary"
              class="ml-0.5 h-4 px-1 text-[9px] rounded-full"
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
        class="h-9 w-9 rounded-full"
        @click="emit('clear')"
        :aria-label="clearLabel || 'Clear filters'"
      >
        <X class="h-3.5 w-3.5" />
      </Button>
    </div>
  </div>
</template>
