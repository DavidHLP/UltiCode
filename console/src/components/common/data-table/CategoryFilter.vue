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
      class="flex items-center gap-1.5 px-2.5 py-1 rounded-none text-[11px] font-medium transition-all duration-200 border"
      :class="
        modelValue === cat.value
          ? 'bg-[var(--surface-sunken)] border-[var(--accent-electric)] text-[var(--accent-electric)] shadow-sm'
          : 'border-silver bg-card text-muted-foreground hover:text-foreground hover:bg-[var(--surface-sunken)]'
      "
    >
      <div
        class="p-0.5 rounded-none bg-[var(--surface-sunken)] border border-silver/30 shadow-sm"
        :class="
          modelValue === cat.value
            ? 'text-[var(--accent-electric)]'
            : 'text-muted-foreground'
        "
      >
        <component :is="cat.icon" v-if="cat.icon" class="w-2.5 h-2.5" />
      </div>
      {{ cat.label }}
    </button>
  </div>
</template>
