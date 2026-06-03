<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <th
    data-slot="table-head"
    :class="
      cn(
        'h-9 px-3 text-left align-middle whitespace-nowrap',
        'text-[var(--silver-500)] font-data text-xs uppercase tracking-wider',
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]',
        '[&:has([role=checkbox])]:pr-0 [&>[role=checkbox]]:translate-y-[2px]',
        props.class,
      )
    "
  >
    <slot />
  </th>
</template>
