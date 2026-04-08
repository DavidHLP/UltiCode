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
  <div class="flex flex-wrap gap-1.5 mb-1.5">
    <button
      v-for="cat in categories"
      :key="cat.value"
      @click="emit('update:modelValue', cat.value)"
      class="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium transition-all duration-200"
      :class="
        modelValue === cat.value
          ? 'bg-zinc-100 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 shadow-sm ring-1 ring-black/5 dark:ring-white/10'
          : 'text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 hover:bg-zinc-100/50 dark:hover:bg-zinc-800/50'
      "
    >
      <div
        class="p-0.5 rounded bg-popover shadow-sm"
        :class="modelValue === cat.value ? 'text-primary' : 'text-zinc-400'"
      >
        <component :is="cat.icon" v-if="cat.icon" class="w-2.5 h-2.5" />
      </div>
      {{ cat.label }}
    </button>
  </div>
</template>
