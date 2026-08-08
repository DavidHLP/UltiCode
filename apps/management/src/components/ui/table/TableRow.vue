<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <tr
    data-slot="table-row"
    :class="
      cn(
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]',
        'hover:bg-[var(--surface-sunken)] transition-colors duration-150',
        'data-[state=selected]:bg-[color-mix(in_oklch,_var(--accent-electric)_8%,_transparent)] dark:data-[state=selected]:bg-[color-mix(in_oklch,_var(--accent-electric)_15%,_transparent)]',
        props.class,
      )
    "
  >
    <slot />
  </tr>
</template>
