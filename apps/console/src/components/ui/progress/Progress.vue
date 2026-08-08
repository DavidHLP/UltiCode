<script setup lang="ts">
import {
  ProgressIndicator,
  ProgressRoot,
  type ProgressRootProps,
} from "reka-ui";
import { cn } from "@/lib/utils";
import { type HTMLAttributes } from "vue";
import { reactiveOmit } from "@vueuse/core";

defineOptions({
  name: "UiProgress",
});

const props = withDefaults(
  defineProps<
    ProgressRootProps & {
      class?: HTMLAttributes["class"];
      indicatorClass?: HTMLAttributes["class"];
    }
  >(),
  {
    modelValue: 0,
  },
);

const delegatedProps = reactiveOmit(props, "class", "indicatorClass");
</script>

<template>
  <!-- eslint-disable vue/multi-word-component-names, vue/no-reserved-component-names -->
  <ProgressRoot
    v-bind="delegatedProps"
    :class="
      cn(
        'relative h-4 w-full overflow-hidden rounded-full bg-secondary',
        props.class,
      )
    "
  >
    <ProgressIndicator
      class="h-full w-full flex-1 bg-primary transition-all"
      :class="props.indicatorClass"
      :style="`transform: translateX(-${100 - (props.modelValue ?? 0)}%);`"
    />
  </ProgressRoot>
</template>
