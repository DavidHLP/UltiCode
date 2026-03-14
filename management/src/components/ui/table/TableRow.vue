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
        'data-[state=selected]:bg-[oklch(0.65_0.15_250/0.08)] dark:data-[state=selected]:bg-[oklch(0.6_0.18_250/0.15)]',
        props.class,
      )
    "
  >
    <slot />
  </tr>
</template>
