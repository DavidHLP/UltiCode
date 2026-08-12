<script setup lang="ts">
import type { DropdownMenuItemProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { MENU_ITEM_VARIANT_CLASSES } from '@ulticode/design-system'
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
        variant === 'default' && MENU_ITEM_VARIANT_CLASSES.default,
        // Destructive variant
        variant === 'destructive' && MENU_ITEM_VARIANT_CLASSES.destructive,
        // Terminal variant
        variant === 'terminal' && [
          'focus:bg-[var(--surface-highlight)] dark:focus:bg-[var(--surface-highlight)] focus:text-[var(--foreground)] [&_svg:not([class*=\'text-\'])]:text-[var(--foreground-muted)] font-data text-xs rounded-none',
        ],
        // Terminal destructive variant
        variant === 'terminal_destructive' && [
          MENU_ITEM_VARIANT_CLASSES.destructive,
          '[&_svg:not([class*=\'text-\'])]:!text-foreground-strong font-data text-xs rounded-none',
        ],
        props.class,
      )
    "
  >
    <slot />
  </DropdownMenuItem>
</template>
