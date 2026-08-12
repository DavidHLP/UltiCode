<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useVModel } from '@vueuse/core'
import { ref } from 'vue'
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

const inputElement = ref<HTMLInputElement | null>(null)
defineExpose({ focus: () => inputElement.value?.focus() })
</script>

<template>
  <input
    ref="inputElement"
    v-model="modelValue"
    data-slot="input"
    :data-variant="variant"
    :class="
      cn(
        'file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground h-9 w-full min-w-0 rounded-md border border-border-control bg-surface-sunken px-3 py-1 text-base transition-[color,box-shadow] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
        // Default variant
        variant === 'default' && [
          'shadow-xs',
          'focus-visible:border-ring focus-visible:ring-ring focus-visible:ring-2',
          'aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 focus-visible:aria-invalid:ring-destructive dark:focus-visible:aria-invalid:ring-destructive aria-invalid:border-destructive',
        ],
        // Terminal variant
        variant === 'terminal' && [
          'font-data text-sm border-[var(--border-control)] dark:border-[var(--border-control)] rounded-none shadow-none bg-[var(--surface-sunken)]',
          'selection:bg-[var(--primary)] selection:text-primary-foreground',
          'focus-visible:border-[var(--primary)] focus-visible:ring-[var(--accent-glow)] focus-visible:ring-[2px]',
          'aria-invalid:border-[var(--status-error-mark)] aria-invalid:ring-[color-mix(in_oklch,_var(--status-error-mark)_20%,_transparent)]',
        ],
        props.class,
      )
    "
  />
</template>
