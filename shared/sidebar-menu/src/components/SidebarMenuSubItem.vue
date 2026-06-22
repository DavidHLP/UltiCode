<script setup lang="ts">
import { useAttrs, computed } from 'vue'
import { cn } from '../utils'

const props = withDefaults(
  defineProps<{
    isActive?: boolean
    size?: 'sm' | 'md'
    class?: string
  }>(),
  { size: 'md' },
)

const attrs = useAttrs()
const attrClass = computed<string | undefined>(() => attrs['class'] as string | undefined)

const mergedClass = computed(() =>
  cn(
    'text-sidebar-foreground ring-sidebar-ring hover:bg-sidebar-accent hover:text-sidebar-accent-foreground flex h-7 min-w-0 -translate-x-px items-center gap-2 overflow-hidden px-2 outline-hidden focus-visible:ring-2 disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50 [&>span:last-child]:truncate [&>svg]:size-4 [&>svg]:shrink-0',
    'group-data-[collapsible=icon]:hidden',
    'h-8 transition-all duration-200 rounded-md',
    props.isActive
      ? 'bg-[var(--accent-electric)]/8 text-[var(--accent-electric)] font-semibold'
      : 'text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--silver-200)]/40 hover:text-foreground',
    attrClass.value,
    props.class,
  ),
)
</script>

<template>
  <component
    :is="$attrs.to ? 'router-link' : 'a'"
    data-slot="sidebar-menu-sub-button"
    :data-size="size"
    :data-active="isActive ? 'true' : 'false'"
    :class="mergedClass"
    v-bind="$attrs"
  >
    <slot />
  </component>
</template>
