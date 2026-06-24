<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '../utils'

const props = withDefaults(
  defineProps<{
    isActive?: boolean
    as?: 'link' | 'button' | 'a'
    class?: string
    /** Optional badge (count/label) pinned to the row end. */
    badge?: string | number
    /** Badge tone. */
    badgeVariant?: 'default' | 'accent' | 'muted'
    /** Extra class applied to the leading `#icon` slot wrapper. */
    iconClass?: string
    /** When true, render a trailing chevron toggle that emits `toggle`. */
    showChevron?: boolean
  }>(),
  { as: 'link', badgeVariant: 'default', showChevron: false },
)

const emit = defineEmits<{
  toggle: []
}>()

const tag = computed(() => {
  if (props.as === 'link') return 'router-link'
  if (props.as === 'a') return 'a'
  return 'button'
})

const badgeClass = computed(() => {
  switch (props.badgeVariant) {
    case 'accent':
      return 'bg-[var(--accent-electric)]/15 text-[var(--accent-electric)]'
    case 'muted':
      return 'bg-[var(--silver-200)]/50 text-[var(--silver-600)] dark:text-[var(--silver-400)]'
    default:
      return 'bg-[var(--silver-200)]/60 text-[var(--solarized-base01)] dark:text-[var(--silver-400)]'
  }
})

// Activation visuals are driven by [data-active] + the .uc-sidebar-item CSS
// contract (single source of truth); this component no longer hand-writes
// `border-l-4 border-[--accent-electric]`.
const mergedClass = computed(() =>
  cn(
    'uc-sidebar-item group flex items-center gap-2.5 pl-2.5 pr-3 py-1.5 transition-all duration-200 select-none text-sm font-medium h-9 mx-1 rounded-md',
    props.class,
  ),
)

function onChevronClick(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  emit('toggle')
}
</script>

<template>
  <component
    :is="tag"
    :class="mergedClass"
    :data-active="isActive ? 'true' : 'false'"
    v-bind="$attrs"
  >
    <span v-if="$slots.icon" :class="cn('flex shrink-0 items-center', iconClass)">
      <slot name="icon" />
    </span>
    <slot />
    <span
      v-if="badge !== undefined && badge !== null"
      :class="cn('ml-auto inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium tabular-nums', badgeClass)"
    >
      {{ badge }}
    </span>
    <button
      v-if="showChevron"
      type="button"
      class="ml-auto flex size-4 shrink-0 items-center text-[var(--silver-500)] hover:text-[var(--foreground)]"
      aria-label="toggle section"
      @click="onChevronClick"
    >
      <slot name="chevron">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="size-4 transition-transform duration-200"
        >
          <path d="m9 18 6-6-6-6" />
        </svg>
      </slot>
    </button>
  </component>
</template>
