<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    width?: string
    height?: string
    rounded?: 'none' | 'sm' | 'md' | 'lg' | 'full'
    animate?: boolean
    class?: string
  }>(),
  {
    width: '100%',
    height: '1rem',
    rounded: 'md',
    animate: true,
    class: '',
  },
)

const roundedClasses = computed(() => ({
  none: 'rounded-none',
  sm: 'rounded-sm',
  md: 'rounded-md',
  lg: 'rounded-lg',
  full: 'rounded-full',
})[props.rounded])
</script>

<template>
  <div
    class="bg-muted"
    :class="[
      roundedClasses,
      animate ? 'animate-pulse' : '',
      props.class,
    ]"
    :style="{
      width: width,
      height: height,
    }"
  />
</template>
