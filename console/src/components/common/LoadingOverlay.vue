<script setup lang="ts">
import { computed } from "vue";
import { IconLoader2 } from "@tabler/icons-vue";

const props = withDefaults(
  defineProps<{
    loading?: boolean;
    text?: string;
    fullscreen?: boolean;
  }>(),
  {
    loading: true,
    text: "Loading...",
    fullscreen: false,
  },
);

const containerClasses = computed(() => ({
  "fixed inset-0 z-50": props.fullscreen,
  "absolute inset-0": !props.fullscreen,
}));
</script>

<template>
  <Teleport v-if="fullscreen && loading" to="body">
    <div
      class="flex flex-col items-center justify-center bg-background/80 backdrop-blur-sm"
      :class="containerClasses"
    >
      <IconLoader2 class="h-10 w-10 animate-spin text-primary mb-4" />
      <p v-if="text" class="text-muted-foreground">{{ text }}</p>
    </div>
  </Teleport>

  <div
    v-else-if="loading"
    class="flex flex-col items-center justify-center bg-background/80 backdrop-blur-sm"
    :class="containerClasses"
  >
    <IconLoader2 class="h-10 w-10 animate-spin text-primary mb-4" />
    <p v-if="text" class="text-muted-foreground">{{ text }}</p>
  </div>

  <slot v-else />
</template>
