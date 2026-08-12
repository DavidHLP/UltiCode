<script setup lang="ts">
/**
 * DataBlock - Key-value pair display with terminal styling
 *
 * Displays data as labeled key-value pairs with monospace typography
 * and optional visual treatments for different data types.
 */
import { cn } from '@/lib/utils'

interface Props {
  label: string
  value?: string | number | null
  hint?: string
  mono?: boolean
  size?: 'sm' | 'md' | 'lg'
}

withDefaults(defineProps<Props>(), {
  mono: true,
  size: 'md',
})

const sizeClasses = {
  sm: 'text-xs gap-0.5',
  md: 'text-sm gap-1',
  lg: 'text-base gap-1.5',
}
</script>

<template>
  <div :class="cn('flex flex-col', sizeClasses[size])">
    <span class="terminal-kv-key">{{ label }}</span>
    <span :class="cn(mono && 'font-data', 'text-[var(--foreground)]')">
      <slot>
        <template v-if="value !== null && value !== undefined">
          {{ value }}
        </template>
        <template v-else>
          <span class="text-[var(--foreground-muted)] italic">—</span>
        </template>
      </slot>
    </span>
    <span v-if="hint" class="text-xs text-[var(--foreground-muted)] mt-0.5">{{ hint }}</span>
  </div>
</template>
