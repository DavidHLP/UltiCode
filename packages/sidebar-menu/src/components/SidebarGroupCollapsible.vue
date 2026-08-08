<script setup lang="ts">
import type { Component } from 'vue'
import { CollapsibleRoot, type CollapsibleRootProps } from 'reka-ui'
import { cn } from '../utils'

// Uncontrolled only — same rationale as SidebarParentItem: binding `:open`
// makes reka treat CollapsibleRoot as controlled, and `:open="undefined"` makes
// it controlled-closed so CollapsibleContent never renders (the fc266ce10
// regression). Only `defaultOpen` / `disabled` are forwarded; `as` / `asChild`
// are intentionally NOT forwarded (CollapsibleRoot stays the root), so the
// prop type is narrowed to match what is actually bound.
type ForwardedCollapsibleProps = Pick<CollapsibleRootProps, 'defaultOpen' | 'disabled'>

const props = withDefaults(
  defineProps<
    ForwardedCollapsibleProps & {
      /** Optional group title rendered in the label row. */
      title?: string
      /** Optional leading icon component for the title. */
      icon?: Component
      /** Render the label in the active accent color (via [data-active]). */
      active?: boolean
      /** Extra class applied to the label row. */
      labelClass?: string
    }
  >(),
  { defaultOpen: true, active: false },
)
</script>

<template>
  <CollapsibleRoot
    v-slot="{ open }"
    :default-open="props.defaultOpen"
    :disabled="props.disabled"
    data-slot="collapsible"
    class="group/collapsible"
  >
    <div
      v-if="title"
      :data-active="active ? 'true' : 'false'"
      :class="cn('uc-sidebar-group-label flex items-center gap-1.5', labelClass)"
    >
      <component :is="icon" v-if="icon" class="size-3.5 shrink-0" />
      <span>{{ title }}</span>
    </div>
    <slot :open="open" />
  </CollapsibleRoot>
</template>
