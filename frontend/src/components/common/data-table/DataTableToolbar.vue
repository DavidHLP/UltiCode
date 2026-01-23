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
    class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between"
  >
    <!-- Left: Search -->
    <div class="relative w-full max-w-md">
      <Search
        class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
      />
      <Input
        :model-value="modelValue"
        @update:model-value="(v) => emit('update:modelValue', v as string)"
        :placeholder="placeholder || 'Search...'"
        class="pl-9 h-10 rounded-full"
      />
    </div>

    <!-- Right: Actions -->
    <div class="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button
            variant="outline"
            class="h-10 gap-2 border-dashed rounded-full"
          >
            <ListFilter class="h-4 w-4" />
            {{ filterLabel || "Filters" }}
            <Badge
              v-if="(activeFilterCount || 0) > 0"
              variant="secondary"
              class="ml-1 h-5 px-1 text-[10px] rounded-full"
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
        class="h-10 w-10 rounded-full"
        @click="emit('clear')"
        :aria-label="clearLabel || 'Clear filters'"
      >
        <X class="h-4 w-4" />
      </Button>
    </div>
  </div>
</template>
