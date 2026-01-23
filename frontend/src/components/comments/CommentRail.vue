<template>
  <div
    class="absolute left-0 top-0 w-full z-0 group"
    :style="{ height: `${height + 16}px` }"
    @click="$emit('click')"
    @mouseenter="$emit('mouseenter')"
    @mouseleave="$emit('mouseleave')"
  >
    <svg class="w-full h-full overflow-visible">
      <!-- Hit Area -->
      <line
        x1="16"
        y1="32"
        x2="16"
        :y2="height"
        stroke="transparent"
        stroke-width="16"
        class="cursor-pointer"
      />
      <!-- Visible Line -->
      <line
        x1="16"
        y1="32"
        x2="16"
        :y2="height"
        stroke="black"
        stroke-width="2"
        stroke-linecap="round"
        class="transition-colors duration-200"
        :class="{ 'opacity-100': isActive, 'opacity-20': !isActive }"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  height: number;
  isActive?: boolean;
}>();

defineEmits<{
  (e: "click"): void;
  (e: "mouseenter"): void;
  (e: "mouseleave"): void;
}>();
</script>
