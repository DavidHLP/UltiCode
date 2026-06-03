<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@/lib/utils";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();
</script>

<template>
  <kbd
    :class="
      cn(
        'border border-silver bg-[var(--surface-sunken)] text-muted-foreground pointer-events-none inline-flex h-5 w-fit min-w-5 items-center justify-center gap-1 px-1.5 font-data text-xs font-medium select-none rounded-none',
        '[&_svg:not([class*=\'size-\'])]:size-3',
        '[[data-slot=tooltip-content]_&]:bg-background/20 [[data-slot=tooltip-content]_&]:text-background dark:[[data-slot=tooltip-content]_&]:bg-background/10',
        props.class,
      )
    "
  >
    <slot />
  </kbd>
</template>
