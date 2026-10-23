<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@ulticode/design-system/utils/cn";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();
</script>

<template>
  <th
    data-slot="table-head"
    :class="
      cn(
        'text-foreground px-2 py-(--uc-layout-cell-padding-block) text-start align-middle font-medium whitespace-nowrap [&:has([role=checkbox])]:pe-0',
        props.class,
      )
    "
  >
    <slot />
  </th>
</template>
