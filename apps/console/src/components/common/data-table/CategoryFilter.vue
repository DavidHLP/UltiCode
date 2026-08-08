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
    class="flex flex-wrap items-center gap-1 bg-[var(--surface-sunken)] p-1 rounded-none border border-border/40 w-fit mb-2"
  >
    <button
      v-for="cat in categories"
      :key="cat.value"
      @click="emit('update:modelValue', cat.value)"
      class="flex items-center gap-1.5 px-3 py-1.5 rounded-none text-xs transition-all duration-200 cursor-pointer select-none"
      :class="
        modelValue === cat.value
          ? 'bg-card text-[var(--accent-electric)] shadow-sm border border-[var(--accent-electric)]/20 font-bold'
          : 'border border-transparent text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-card/40 hover:text-foreground font-medium'
      "
    >
      <component :is="cat.icon" v-if="cat.icon" class="w-3.5 h-3.5 shrink-0" />
      {{ cat.label }}
    </button>
  </div>
</template>
