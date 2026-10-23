<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@ulticode/design-system/utils/cn";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();
</script>

<template>
  <div
    data-slot="alert-action"
    :class="cn('absolute top-2 end-2', props.class)"
  >
    <slot />
  </div>
</template>
