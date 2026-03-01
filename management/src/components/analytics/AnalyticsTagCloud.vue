<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export interface TagItem {
  id: string | number
  label: string
  value: number
  count?: number
}

const props = withDefaults(
  defineProps<{
    title?: string
    description?: string
    tags: TagItem[]
    maxValue?: number
    minValue?: number
    showValue?: boolean
    valueFormat?: 'number' | 'percent'
    limit?: number
  }>(),
  {
    showValue: true,
    valueFormat: 'number',
    limit: 20,
  },
)

const displayTags = computed(() => {
  const tags = props.tags.slice(0, props.limit)
  const max = props.maxValue || Math.max(...tags.map((t) => t.value), 1)
  const min = props.minValue || Math.min(...tags.map((t) => t.value), 0)

  return tags.map((tag) => {
    // Normalize value to 0-1 range for size calculation
    const normalized = max > min ? (tag.value - min) / (max - min) : 0.5
    // Map to font sizes (0.75rem to 1rem)
    const fontSize = 0.75 + normalized * 0.25
    // Map to opacity
    const opacity = 0.6 + normalized * 0.4

    return {
      ...tag,
      fontSize,
      opacity,
    }
  })
})

function formatValue(value: number): string {
  if (props.valueFormat === 'percent') {
    return value.toFixed(1) + '%'
  }
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toString()
}
</script>

<template>
  <Card
    class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] shadow-float h-full"
  >
    <CardHeader v-if="title" class="pb-3 pt-5 px-5">
      <CardTitle class="text-base font-medium tracking-tight">{{ title }}</CardTitle>
      <CardDescription v-if="description" class="text-xs text-[var(--silver-400)] mt-1">
        {{ description }}
      </CardDescription>
    </CardHeader>
    <CardContent class="px-5 pb-5" :class="{ 'pt-5': !title }">
      <div v-if="displayTags.length === 0" class="text-center py-8 text-[var(--silver-400)] text-sm">
        {{ $t('common.noData') }}
      </div>
      <div v-else class="flex flex-wrap gap-2">
        <span
          v-for="tag in displayTags"
          :key="tag.id"
          class="group inline-flex items-center gap-1.5 px-2.5 py-1 rounded border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card hover:border-[var(--accent-primary)] hover:bg-[var(--accent-primary)]/5 transition-all duration-200 cursor-default"
          :style="{
            fontSize: tag.fontSize + 'rem',
            opacity: tag.opacity,
          }"
        >
          <span class="font-medium text-foreground">{{ tag.label }}</span>
          <span
            v-if="showValue"
            class="font-data tabular-nums text-[var(--silver-400)] group-hover:text-[var(--accent-primary)] transition-colors"
          >
            ({{ formatValue(tag.value) }})
          </span>
        </span>
      </div>
    </CardContent>
  </Card>
</template>
