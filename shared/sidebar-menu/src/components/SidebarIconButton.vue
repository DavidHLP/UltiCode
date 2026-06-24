<script setup lang="ts">
import { cn } from '../utils'

const props = withDefaults(
  defineProps<{
    /** Accessible label for the button. */
    label: string
    class?: string
  }>(),
  {},
)

const emit = defineEmits<{
  click: [MouseEvent]
}>()
</script>

<template>
  <button
    type="button"
    :aria-label="label"
    :class="cn(
      'uc-sidebar-icon-button flex size-7 shrink-0 items-center justify-center rounded-md text-[var(--silver-500)] hover:bg-[var(--silver-200)]/40 hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2',
      props.class,
    )"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>
