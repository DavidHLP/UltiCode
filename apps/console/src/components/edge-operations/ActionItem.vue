<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";
import { computed, type Component } from "vue";

interface Props {
  icon?: Component;
  count?: number | string;
  label?: string;
  active?: boolean;
  variant?: "button" | "simple";
  activeClass?: string;
  class?:
    | string
    | Record<string, boolean | undefined | null>
    | Array<string | Record<string, boolean | undefined | null>>;
}

const props = withDefaults(defineProps<Props>(), {
  variant: "button",
});

const isButton = computed(() => props.variant === "button");
</script>

<template>
  <component
    :is="isButton ? Button : 'div'"
    :variant="isButton ? 'ghost' : undefined"
    :size="isButton ? 'sm' : undefined"
    class="rounded-lg flex items-center transition-all duration-[var(--duration-fast)] [transition-timing-function:var(--ease-out-expo)] h-8 select-none border border-border dark:border-border bg-background dark:bg-accent hover:bg-accent dark:hover:bg-border hover:border-border dark:hover:border-muted-foreground"
    :class="[
      isButton
        ? 'text-muted-foreground hover:text-foreground'
        : 'text-muted-foreground/70',
      active && isButton
        ? activeClass ||
          'text-primary hover:text-primary bg-primary/10 hover:bg-primary/20 border-primary/10 font-medium'
        : '',
      isButton ? 'px-3 gap-2' : 'px-2 gap-1.5',
      props.class,
    ]"
  >
    <component :is="icon" v-if="icon" class="h-4 w-4" />
    <span
      v-if="count !== undefined && count !== ''"
      class="font-mono font-bold text-xs tracking-tight tabular-nums"
    >
      {{ count }}
    </span>
    <span v-if="label" class="hidden sm:inline text-xs font-bold opacity-80">
      {{ label }}
    </span>
  </component>
</template>
