<script setup lang="ts">
import type { SelectTriggerProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { ChevronDown } from 'lucide-vue-next'
import { SelectIcon, SelectTrigger, useForwardProps } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<
    SelectTriggerProps & {
      class?: HTMLAttributes['class']
      size?: 'sm' | 'default'
      variant?: 'default' | 'terminal'
    }
  >(),
  { size: 'default', variant: 'default' },
)

const delegatedProps = reactiveOmit(props, 'class', 'size', 'variant')
const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <SelectTrigger
    data-slot="select-trigger"
    :data-size="size"
    :data-variant="variant"
    v-bind="forwardedProps"
    :class="
      cn(
        'flex w-fit items-center justify-between gap-2 border bg-transparent px-3 py-2 text-sm whitespace-nowrap transition-[color,box-shadow] outline-none disabled:cursor-not-allowed disabled:opacity-50 data-[size=default]:h-9 data-[size=sm]:h-8 *:data-[slot=select-value]:line-clamp-1 *:data-[slot=select-value]:flex *:data-[slot=select-value]:items-center *:data-[slot=select-value]:gap-2 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=\'size-\'])]:size-4',
        // Default variant
        variant === 'default' && [
          'border-input data-[placeholder]:text-muted-foreground [&_svg:not([class*=\'text-\'])]:text-muted-foreground focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:bg-input/30 dark:hover:bg-input/50 shadow-xs focus-visible:ring-[3px]',
        ],
        // Terminal variant
        variant === 'terminal' && [
          'border-[var(--silver-200)] dark:border-[var(--silver-300)] data-[placeholder]:text-[var(--silver-400)] [&_svg:not([class*=\'text-\'])]:text-[var(--silver-400)] focus-visible:border-[var(--accent-electric)] focus-visible:ring-[var(--accent-electric-glow)] aria-invalid:border-[var(--terminal-red)] aria-invalid:ring-[color-mix(in_oklch,_var(--terminal-red)_20%,_transparent)] font-data text-xs rounded-none shadow-none focus-visible:ring-[2px]',
        ],
        props.class,
      )
    "
  >
    <slot />
    <SelectIcon as-child>
      <ChevronDown class="size-4 opacity-50" />
    </SelectIcon>
  </SelectTrigger>
</template>
