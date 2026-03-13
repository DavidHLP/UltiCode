<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { AvatarRoot } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes['class']
    shape?: 'circle' | 'square'
  }>(),
  {
    shape: 'circle',
  },
)
</script>

<template>
  <AvatarRoot
    data-slot="avatar"
    :data-shape="shape"
    :class="
      cn(
        'relative flex size-8 shrink-0 overflow-hidden',
        shape === 'circle' && '',
        shape === 'square' && '',
        props.class,
      )
    "
  >
    <slot />
  </AvatarRoot>
</template>
