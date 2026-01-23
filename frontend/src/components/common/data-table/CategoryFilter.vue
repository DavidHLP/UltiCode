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
  <div class="flex flex-wrap gap-2 mb-2">
    <button
      v-for="cat in categories"
      :key="cat.value"
      @click="emit('update:modelValue', cat.value)"
      class="flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-medium transition-all duration-200"
      :class="
        modelValue === cat.value
          ? 'bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 shadow-sm ring-1 ring-black/5 dark:ring-white/10'
          : 'text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 hover:bg-zinc-100/50 dark:hover:bg-zinc-800/50'
      "
    >
      <div
        class="p-1 rounded bg-white dark:bg-zinc-950 shadow-sm"
        :class="modelValue === cat.value ? 'text-primary' : 'text-zinc-400'"
      >
        <component :is="cat.icon" v-if="cat.icon" class="w-3 h-3" />
      </div>
      {{ cat.label }}
    </button>
  </div>
</template>
