<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes['class']
    overflow?: boolean
  }>(),
  {
    overflow: true,
  },
)
</script>

<template>
  <div
    data-slot="table-container"
    :class="
      cn('relative w-full', overflow ? 'overflow-y-auto overflow-x-hidden' : 'overflow-visible')
    "
  >
    <table data-slot="table" :class="cn('min-w-full caption-bottom text-sm', props.class)">
      <slot />
    </table>
  </div>
</template>
