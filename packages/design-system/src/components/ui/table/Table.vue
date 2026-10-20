<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@ulticode/design-system/utils/cn";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();
const handleHorizontalKeydown = (event: KeyboardEvent) => {
  if (event.target !== event.currentTarget) return;
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;

  const container = event.currentTarget as HTMLDivElement;
  container.scrollLeft += event.key === "ArrowRight" ? 40 : -40;
  event.preventDefault();
};
</script>

<template>
  <div
    data-slot="table-container"
    tabindex="0"
    class="relative w-full overflow-x-auto focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    @keydown="handleHorizontalKeydown"
  >
    <table
      data-slot="table"
      :class="cn('w-full caption-bottom text-sm', props.class)"
    >
      <slot />
    </table>
  </div>
</template>
