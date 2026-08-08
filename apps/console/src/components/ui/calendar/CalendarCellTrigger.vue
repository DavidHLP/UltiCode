<script lang="ts" setup>
import type { CalendarCellTriggerProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import { reactiveOmit } from "@vueuse/core";
import { CalendarCellTrigger, useForwardProps } from "reka-ui";
import { cn } from "@/lib/utils";
import { buttonVariants } from "@/components/ui/button";

const props = withDefaults(
  defineProps<CalendarCellTriggerProps & { class?: HTMLAttributes["class"] }>(),
  {
    as: "button",
  },
);

const delegatedProps = reactiveOmit(props, "class");

const forwardedProps = useForwardProps(delegatedProps);
</script>

<template>
  <CalendarCellTrigger
    data-slot="calendar-cell-trigger"
    :class="
      cn(
        buttonVariants({ variant: 'ghost' }),
        'size-8 p-0 font-normal aria-selected:opacity-100 cursor-default rounded-none',
        '[&[data-today]:not([data-selected])]:border [&[data-today]:not([data-selected])]:border-dashed [&[data-today]:not([data-selected])]:border-[var(--accent-electric)]/70 [&[data-today]:not([data-selected])]:bg-[var(--accent-electric)]/10 [&[data-today]:not([data-selected])]:text-[var(--accent-electric)] [&[data-today]:not([data-selected])]:font-bold',
        // Selected
        'data-[selected]:bg-[var(--accent-electric)] data-[selected]:text-white data-[selected]:opacity-100 data-[selected]:hover:bg-[var(--accent-electric)] data-[selected]:hover:text-white data-[selected]:focus:bg-[var(--accent-electric)] data-[selected]:focus:text-white data-[selected]:font-bold data-[selected]:border data-[selected]:border-[var(--accent-electric)]',
        // Disabled
        'data-[disabled]:text-muted-foreground data-[disabled]:opacity-50',
        // Unavailable
        'data-[unavailable]:text-destructive-foreground data-[unavailable]:line-through',
        // Outside months
        'data-[outside-view]:text-muted-foreground',
        props.class,
      )
    "
    v-bind="forwardedProps"
  >
    <slot />
  </CalendarCellTrigger>
</template>
