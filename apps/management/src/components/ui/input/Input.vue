<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useVModel } from '@vueuse/core'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    defaultValue?: string | number
    modelValue?: string | number
    class?: HTMLAttributes['class']
    variant?: 'default' | 'terminal'
  }>(),
  {
    variant: 'default',
  },
)

const emits = defineEmits<{
  (e: 'update:modelValue', payload: string | number): void
}>()

const modelValue = useVModel(props, 'modelValue', emits, {
  passive: true,
  defaultValue: props.defaultValue,
})
</script>

<template>
  <input
    v-model="modelValue"
    data-slot="input"
    :data-variant="variant"
    :class="
      cn(
        'file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground h-9 w-full min-w-0 border bg-transparent px-3 py-1 text-base transition-[color,box-shadow] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
        // Default variant
        variant === 'default' && [
          'dark:bg-input/30 border-input shadow-xs',
          'focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]',
          'aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive',
        ],
        // Terminal variant
        variant === 'terminal' && [
          'font-data text-sm border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-none shadow-none bg-[var(--surface-sunken)]',
          'selection:bg-[var(--accent-electric)] selection:text-[var(--solarized-base3)]',
          'focus-visible:border-[var(--accent-electric)] focus-visible:ring-[var(--accent-electric-glow)] focus-visible:ring-[2px]',
          'aria-invalid:border-[var(--terminal-red)] aria-invalid:ring-[color-mix(in_oklch,_var(--terminal-red)_20%,_transparent)]',
        ],
        props.class,
      )
    "
  />
</template>
