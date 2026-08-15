<script setup lang="ts">
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
    class="flex w-full flex-wrap items-center gap-1 rounded-lg border border-border-subtle bg-surface-sunken p-1"
  >
    <button
      v-for="cat in categories"
      :key="cat.value"
      @click="emit('update:modelValue', cat.value)"
      class="terminal-tab flex min-w-0 items-center justify-center gap-1.5 rounded-md px-2 py-1.5 text-xs transition-all duration-200 cursor-pointer select-none whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background md:flex-1 md:px-3"
      :class="
        modelValue === cat.value
          ? 'bg-surface-highlight text-foreground-strong shadow-sm border border-border-control font-bold'
          : 'border border-transparent text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong font-medium'
      "
    >
      <component :is="cat.icon" v-if="cat.icon" class="w-3.5 h-3.5 shrink-0" />
      {{ cat.label }}
    </button>
  </div>
</template>
