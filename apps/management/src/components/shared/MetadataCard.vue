<script setup lang="ts">
import type { Component } from 'vue'
import { IconInfoCircle, IconHash } from '@tabler/icons-vue'

export interface MetadataItem {
  label: string
  value: string | number
  icon?: Component
}

interface Props {
  title: string
  metadata?: MetadataItem[]
}

withDefaults(defineProps<Props>(), {
  metadata: () => [],
})

const defaultIcon = IconHash
</script>

<template>
  <div class="rounded-none border bg-card overflow-hidden shadow-sm">
    <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
      <IconInfoCircle class="h-4 w-4 text-muted-foreground" />
      <h3 class="font-semibold text-sm">{{ title }}</h3>
    </div>
    <div class="p-4 space-y-4">
      <div v-for="item in metadata" :key="item.label" class="space-y-1">
        <span class="text-xs text-muted-foreground flex items-center gap-1">
          <component :is="item.icon || defaultIcon" class="h-3 w-3" />
          {{ item.label }}
        </span>
        <p
          v-if="item.label === 'ID'"
          class="font-mono text-xs bg-muted/50 p-1 rounded-none select-all truncate"
        >
          {{ item.value }}
        </p>
        <p v-else class="text-sm font-medium tabular-nums">{{ item.value }}</p>
      </div>
    </div>
  </div>
</template>
