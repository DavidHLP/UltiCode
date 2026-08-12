<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCompactNumber, formatNumberByLocale } from '@/i18n/utils'

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
    compact?: boolean
  }>(),
  {
    showValue: true,
    valueFormat: 'number',
    limit: 20,
    compact: false,
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
    return formatNumberByLocale(value / 100, { style: 'percent', maximumFractionDigits: 1 })
  }
  return formatCompactNumber(value)
}
</script>

<template>
  <Card
    class="border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/60 bg-card shadow-float overflow-hidden rounded-none"
    :class="compact ? 'self-start gap-0 py-0' : 'h-full gap-0 py-0'"
  >
    <CardHeader
      v-if="title"
      class="bg-[var(--surface)] dark:bg-[var(--surface-highlight)]/10 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/50"
      :class="compact ? 'px-4 py-3.5' : 'pb-4 pt-5 px-5'"
    >
      <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">{{
        title
      }}</CardTitle>
      <CardDescription v-if="description" class="text-xs text-[var(--foreground-muted)] mt-1">
        {{ description }}
      </CardDescription>
    </CardHeader>
    <CardContent :class="compact ? 'p-4' : 'px-5 pb-5 pt-5'">
      <div
        v-if="displayTags.length === 0"
        class="text-center py-8 text-[var(--foreground-muted)] text-sm"
      >
        {{ $t('common.noData') }}
      </div>
      <div
        v-else
        data-testid="tag-cloud"
        class="flex flex-wrap"
        :class="compact ? 'gap-1.5' : 'gap-2'"
      >
        <span
          v-for="tag in displayTags"
          :key="tag.id"
          data-testid="tag-item"
          class="group inline-flex items-center rounded-none border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-card hover:border-[var(--accent-primary)] hover:bg-[var(--accent-primary)]/5 transition-all duration-200 cursor-default"
          :class="compact ? 'gap-1 px-2 py-0.5' : 'gap-1.5 px-2.5 py-1'"
          :style="{
            fontSize: (compact ? Math.min(tag.fontSize, 0.875) : tag.fontSize) + 'rem',
            opacity: tag.opacity,
          }"
        >
          <span class="font-medium text-foreground">{{ tag.label }}</span>
          <span
            v-if="showValue"
            class="font-data tabular-nums text-[var(--foreground-muted)] group-hover:text-[var(--accent-primary)] transition-colors"
          >
            ({{ formatValue(tag.value) }})
          </span>
        </span>
      </div>
    </CardContent>
  </Card>
</template>
