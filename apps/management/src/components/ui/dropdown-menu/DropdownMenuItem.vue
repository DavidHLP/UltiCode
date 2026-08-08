<script setup lang="ts">
import type { DropdownMenuItemProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { DropdownMenuItem, useForwardProps } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<
    DropdownMenuItemProps & {
      class?: HTMLAttributes['class']
      inset?: boolean
      variant?: 'default' | 'destructive' | 'terminal' | 'terminal_destructive'
    }
  >(),
  {
    variant: 'default',
  },
)

const delegatedProps = reactiveOmit(props, 'inset', 'variant', 'class')

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <DropdownMenuItem
    data-slot="dropdown-menu-item"
    :data-inset="inset ? '' : undefined"
    :data-variant="variant"
    v-bind="forwardedProps"
    :class="
      cn(
        'relative flex cursor-default items-center gap-2 px-2 py-1.5 text-sm outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[inset]:pl-8 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=\'size-\'])]:size-4',
        // Default variant
        variant === 'default' && [
          'focus:bg-accent focus:text-accent-foreground [&_svg:not([class*=\'text-\'])]:text-muted-foreground',
        ],
        // Destructive variant
        variant === 'destructive' && [
          'text-destructive focus:bg-destructive/10 dark:focus:bg-destructive/20 focus:text-destructive [&_svg:not([class*=\'text-\'])]:!text-destructive',
        ],
        // Terminal variant
        variant === 'terminal' && [
          'focus:bg-[var(--silver-100)] dark:focus:bg-[var(--silver-100)] focus:text-[var(--foreground)] [&_svg:not([class*=\'text-\'])]:text-[var(--silver-400)] font-data text-xs rounded-none',
        ],
        // Terminal destructive variant
        variant === 'terminal_destructive' && [
          'text-[var(--terminal-red)] focus:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)] dark:focus:bg-[oklch(0.58_0.18_25/0.15)] focus:text-[var(--terminal-red)] [&_svg:not([class*=\'text-\'])]:!text-[var(--terminal-red)] font-data text-xs rounded-none',
        ],
        props.class,
      )
    "
  >
    <slot />
  </DropdownMenuItem>
</template>
