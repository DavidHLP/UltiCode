<script setup lang="ts">
import type { SeparatorProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { Separator } from 'reka-ui'
import { cn } from '../../utils/cn'

/**
 * Shared `Separator` — a single horizontal/vertical rule.
 *
 * Migrated from `console/src/components/ui/separator/Separator.vue` and
 * `management/src/components/ui/separator/Separator.vue`, which were
 * byte-identical except for Prettier quote style (arch review 2026-07-10,
 * candidate #1). Both apps now re-export this component from their local
 * `components/ui/separator/index.ts` shim.
 */
const props = withDefaults(
  defineProps<SeparatorProps & { class?: HTMLAttributes['class'] }>(),
  {
    orientation: 'horizontal',
    decorative: true,
  },
)

const delegatedProps = reactiveOmit(props, 'class')
</script>

<template>
  <Separator
    data-slot="separator"
    v-bind="delegatedProps"
    :class="
      cn(
        'bg-border shrink-0 data-[orientation=horizontal]:h-px data-[orientation=horizontal]:w-full data-[orientation=vertical]:h-full data-[orientation=vertical]:w-px',
        props.class,
      )
    "
  />
</template>
