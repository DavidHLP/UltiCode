<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '../utils'

// Root binds $attrs explicitly (v-bind="$attrs" below) so `to` / events reach
// the rendered <a> / router-link; disable automatic fallthrough to avoid the
// attrs being applied twice. `class` is a declared prop, not an attr.
defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    isActive?: boolean
    size?: 'sm' | 'md'
    class?: string
    badge?: string | number
    iconClass?: string
  }>(),
  { size: 'md' },
)

// Activation + hover are driven by [data-active] + the .uc-sidebar-sub-item
// CSS contract (single source of truth), shared with the top-level item.
const mergedClass = computed(() =>
  cn(
    'uc-sidebar-sub-item text-sidebar-foreground ring-sidebar-ring flex h-7 min-w-0 -translate-x-px items-center gap-2 overflow-hidden px-2 outline-hidden focus-visible:ring-2 disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50 [&>span:last-child]:truncate [&>svg]:size-4 [&>svg]:shrink-0',
    'group-data-[collapsible=icon]:hidden',
    'h-8 transition-all duration-200 rounded-md',
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
    <span v-if="$slots.icon" :class="cn('flex shrink-0 items-center', iconClass)">
      <slot name="icon" />
    </span>
    <slot />
    <span
      v-if="badge !== undefined && badge !== null"
      class="ml-auto inline-flex items-center rounded-full bg-[var(--silver-200)]/60 px-2 py-0.5 text-xs font-medium tabular-nums text-[var(--solarized-base01)] dark:text-[var(--silver-400)]"
    >
      {{ badge }}
    </span>
  </component>
</template>
