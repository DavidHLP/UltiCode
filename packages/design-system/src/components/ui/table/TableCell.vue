<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@ulticode/design-system/utils/cn";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();
</script>

<template>
  <td
    data-slot="table-cell"
    :class="
      cn(
        'px-2 py-(--uc-layout-cell-padding-block) align-middle whitespace-nowrap [&:has([role=checkbox])]:pe-0',
        props.class,
      )
    "
  >
    <slot />
  </td>
</template>
