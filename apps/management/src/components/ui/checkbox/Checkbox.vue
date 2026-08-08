<script setup lang="ts">
import type { CheckboxRootEmits, CheckboxRootProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { Check } from 'lucide-vue-next'
import { CheckboxIndicator, CheckboxRoot, useForwardPropsEmits } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<
    CheckboxRootProps & { class?: HTMLAttributes['class']; variant?: 'default' | 'terminal' }
  >(),
  {
    variant: 'default',
  },
)
const emits = defineEmits<CheckboxRootEmits>()

const delegatedProps = reactiveOmit(props, 'class', 'variant')

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <CheckboxRoot
    v-slot="slotProps"
    data-slot="checkbox"
    :data-variant="variant"
    v-bind="forwarded"
    :class="
      cn(
        'peer size-4 shrink-0 border transition-shadow outline-none disabled:cursor-not-allowed disabled:opacity-50',
        // Default variant
        variant === 'default' && [
          'border-input data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground data-[state=checked]:border-primary focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive shadow-xs focus-visible:ring-[3px]',
        ],
        // Terminal variant
        variant === 'terminal' && [
          'border-[var(--silver-300)] dark:border-[var(--silver-400)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:text-white data-[state=checked]:border-[var(--accent-electric)] focus-visible:border-[var(--accent-electric)] focus-visible:ring-[var(--accent-electric-glow)] rounded-none shadow-none focus-visible:ring-[2px]',
        ],
        props.class,
      )
    "
  >
    <CheckboxIndicator
      data-slot="checkbox-indicator"
      class="grid place-content-center text-current transition-none"
    >
      <slot v-bind="slotProps">
        <Check class="size-3.5" />
      </slot>
    </CheckboxIndicator>
  </CheckboxRoot>
</template>
