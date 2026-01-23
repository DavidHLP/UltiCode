<template>
  <svg
    v-if="depth > 0"
    class="absolute -left-6 top-0 w-[50px] h-[32px] overflow-visible pointer-events-none z-0"
  >
    <path
      :d="path"
      stroke="black"
      :stroke-width="2"
      fill="none"
      stroke-linecap="round"
      class="transition-colors duration-200"
      :class="{ 'opacity-100': isActive, 'opacity-20': !isActive }"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from "vue";

defineProps<{
  depth: number;
  isActive?: boolean;
}>();

const path = computed(() => {
  const railX = 0;
  const targetX = 41;
  const targetY = 16;
  const r = 12; // curveRadius

  const startY = targetY - r;

  return `
        M ${railX} ${startY}
        Q ${railX} ${targetY} ${railX + r} ${targetY}
        L ${targetX} ${targetY}
    `;
});
</script>
