<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";

import type { Component } from "vue";

export interface CategoryOption {
  label: string;
  value: string;
  icon?: Component;
}

defineProps<{
  categories: CategoryOption[];
  modelValue: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();
</script>

<template>
  <div
    class="flex w-full flex-wrap items-center gap-1 rounded-lg border border-border bg-muted p-1"
  >
    <Button
      variant="outline"
      v-for="cat in categories"
      :key="cat.value"
      @click="emit('update:modelValue', cat.value)"
      class="inline-flex items-center gap-2 px-3 py-2 flex min-w-0 items-center justify-center gap-1.5 px-2 py-1.5 cursor-pointer select-none whitespace-nowrap focus-visible:outline-none md:flex-1 md:px-3"
      :class="
        modelValue === cat.value
          ? 'bg-accent text-foreground shadow-sm border border-input font-bold'
          : 'border border-transparent text-muted-foreground hover:bg-accent hover:text-foreground font-medium'
      "
    >
      <component :is="cat.icon" v-if="cat.icon" class="w-3.5 h-3.5 shrink-0" />
      {{ cat.label }}
    </Button>
  </div>
</template>
