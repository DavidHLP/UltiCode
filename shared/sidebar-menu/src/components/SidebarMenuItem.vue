<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '../utils'

const props = withDefaults(
  defineProps<{
    isActive?: boolean
    as?: 'link' | 'button' | 'a'
    class?: string
  }>(),
  { as: 'link' },
)

const tag = computed(() => {
  if (props.as === 'link') return 'router-link'
  if (props.as === 'a') return 'a'
  return 'button'
})

const mergedClass = computed(() =>
  cn(
    'group flex items-center gap-2.5 pl-2.5 pr-3 py-1.5 transition-all duration-200 select-none text-sm font-medium h-9 mx-1 rounded-md border-l-4',
    props.isActive
      ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/8 text-[var(--accent-electric)] font-bold'
      : 'border-transparent text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--silver-200)]/40 hover:text-foreground',
    props.class,
  ),
)
</script>

<template>
  <component
    :is="tag"
    :class="mergedClass"
    v-bind="$attrs"
  >
    <slot />
  </component>
</template>
