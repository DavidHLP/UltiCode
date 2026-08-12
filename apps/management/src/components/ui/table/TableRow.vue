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
        'border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]',
        'hover:bg-[var(--surface-sunken)] transition-colors duration-150',
        'data-[state=selected]:bg-[color-mix(in_oklch,_var(--primary)_8%,_transparent)] dark:data-[state=selected]:bg-[color-mix(in_oklch,_var(--primary)_15%,_transparent)]',
        props.class,
      )
    "
  >
    <slot />
  </tr>
</template>
