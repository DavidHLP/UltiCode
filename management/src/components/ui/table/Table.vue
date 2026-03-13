<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div data-slot="table-container" class="relative w-full overflow-auto">
    <table
      data-slot="table"
      :class="
        cn(
          'w-full caption-bottom text-sm font-data',
          '[&_th]:font-data [&_th]:text-xs [&_th]:uppercase [&_th]:tracking-wider [&_th]:text-[var(--silver-500)]',
          '[&_td]:text-sm',
          props.class,
        )
      "
    >
      <slot />
    </table>
  </div>
</template>
