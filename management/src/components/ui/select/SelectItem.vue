<script setup lang="ts">
import type { SelectItemProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { Check } from 'lucide-vue-next'
import { SelectItem, SelectItemIndicator, SelectItemText, useForwardProps } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<
    SelectItemProps & { class?: HTMLAttributes['class']; variant?: 'default' | 'terminal' }
  >(),
  {
    variant: 'default',
  },
)

const delegatedProps = reactiveOmit(props, 'class', 'variant')

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <SelectItem
    data-slot="select-item"
    :data-variant="variant"
    v-bind="forwardedProps"
    :class="
      cn(
        'relative flex w-full cursor-default items-center gap-2 py-1.5 pr-8 pl-2 text-sm outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=\'size-\'])]:size-4 *:[span]:last:flex *:[span]:last:items-center *:[span]:last:gap-2',
        // Default variant
        variant === 'default' && [
          'focus:bg-accent focus:text-accent-foreground [&_svg:not([class*=\'text-\'])]:text-muted-foreground rounded-none',
        ],
        // Terminal variant
        variant === 'terminal' && [
          'focus:bg-[var(--silver-100)] dark:focus:bg-[var(--silver-100)] focus:text-[var(--foreground)] [&_svg:not([class*=\'text-\'])]:text-[var(--silver-400)] font-data text-xs rounded-none',
        ],
        props.class,
      )
    "
  >
    <span class="absolute right-2 flex size-3.5 items-center justify-center">
      <SelectItemIndicator>
        <slot name="indicator-icon">
          <Check class="size-4" />
        </slot>
      </SelectItemIndicator>
    </span>

    <SelectItemText>
      <slot />
    </SelectItemText>
  </SelectItem>
</template>
