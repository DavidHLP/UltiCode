<script setup lang="ts">
import { Button } from "@/components/ui/button";
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
    class="rounded-full flex items-center transition-all h-8 select-none border border-transparent"
    :class="[
      isButton
        ? 'bg-muted/50 hover:bg-muted/80 text-muted-foreground'
        : 'text-muted-foreground/70',
      active && isButton
        ? activeClass ||
          'text-primary bg-primary/10 hover:bg-primary/20 border-primary/10 font-medium'
        : '',
      isButton ? 'px-3 gap-2' : 'px-2 gap-1.5',
      props.class,
    ]"
  >
    <component :is="icon" v-if="icon" class="h-4 w-4" />
    <span
      v-if="count !== undefined && count !== ''"
      class="font-bold text-[11px] tracking-tight"
    >
      {{ count }}
    </span>
    <span
      v-if="label"
      class="hidden sm:inline text-[11px] font-bold opacity-80"
    >
      {{ label }}
    </span>
  </component>
</template>
