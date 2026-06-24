<script setup lang="ts">
import type { Component } from 'vue'
import {
  CollapsibleRoot,
  type CollapsibleRootProps,
  type CollapsibleRootEmits,
} from 'reka-ui'
import { cn } from '../utils'

const props = withDefaults(
  defineProps<
    CollapsibleRootProps & {
      /** Optional group title rendered in the label row. */
      title?: string
      /** Optional leading icon component for the title. */
      icon?: Component
      /** Render the label in the active accent color. */
      active?: boolean
      /** Extra class applied to the label row. */
      labelClass?: string
    }
  >(),
  { defaultOpen: true, active: false },
)

const emit = defineEmits<CollapsibleRootEmits>()

// Forward only collapsible-relevant props explicitly, so the visual additions
// (title/icon/active/labelClass) do not leak into CollapsibleRoot's attr
// fallthrough. Uncontrolled usage (no `open`) falls back to `defaultOpen`.
</script>

<template>
  <CollapsibleRoot
    v-slot="{ open }"
    :default-open="props.defaultOpen"
    :open="props.open"
    :disabled="props.disabled"
    data-slot="collapsible"
    class="group/collapsible"
    @update:open="emit('update:open', $event)"
  >
    <div
      v-if="title"
      :class="cn('uc-sidebar-group-label flex items-center gap-1.5', active && 'text-[var(--accent-electric)]', labelClass)"
    >
      <component :is="icon" v-if="icon" class="size-3.5 shrink-0" />
      <span>{{ title }}</span>
    </div>
    <slot :open="open" />
  </CollapsibleRoot>
</template>
