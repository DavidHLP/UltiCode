<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@ulticode/design-system/utils/cn";
import { Input } from "@ulticode/design-system/ui/input";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();
</script>

<template>
  <Input
    data-slot="sidebar-input"
    data-sidebar="input"
    :class="cn('bg-background h-8 w-full shadow-none', props.class)"
  >
    <slot />
  </Input>
</template>
