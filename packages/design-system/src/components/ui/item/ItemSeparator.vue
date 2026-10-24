<script setup lang="ts">
import type { SeparatorProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import { cn } from "@ulticode/design-system/utils/cn";
import { Separator } from "@ulticode/design-system/ui/separator";

const props = defineProps<
  SeparatorProps & { class?: HTMLAttributes["class"] }
>();
</script>

<template>
  <Separator
    data-slot="item-separator"
    orientation="horizontal"
    :class="cn('my-2', props.class)"
  />
</template>
