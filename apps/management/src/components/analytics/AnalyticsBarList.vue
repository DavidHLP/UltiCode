<script setup lang="ts">
import { computed } from 'vue'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@ulticode/design-system/ui/card'
import { formatCompactNumber } from '@/i18n/utils'

export interface BarListItem {
  id: string | number
  label: string
  value: number
  max?: number
  subtitle?: string
  color?: string
}

const props = withDefaults(
  defineProps<{
    title?: string
    description?: string
    items: BarListItem[]
    showValue?: boolean
    showPercentage?: boolean
    maxValue?: number
    color?: string
    limit?: number
    compact?: boolean
  }>(),
  {
    showValue: true,
    showPercentage: false,
    color: 'color-mix(in oklch, var(--primary) 35%, var(--border))',
    limit: 10,
    compact: false,
  },
)

const displayItems = computed(() => {
  const items = props.items.slice(0, props.limit)
  const max = props.maxValue || Math.max(...items.map((i) => i.value), 1)
  return items.map((item) => ({
    ...item,
    percentage: (item.value / max) * 100,
    color: item.color || props.color,
  }))
})
</script>

<template>
  <Card class="overflow-hidden" :class="compact ? 'h-full gap-0 py-0' : 'h-full gap-0 py-0'">
    <CardHeader v-if="title" :class="compact ? 'px-4 py-3.5' : 'pb-4 pt-5 px-5'">
      <CardTitle>{{ title }}</CardTitle>
      <CardDescription v-if="description" class="mt-1">
        {{ description }}
      </CardDescription>
    </CardHeader>
    <CardContent :class="compact ? 'p-4' : 'px-5 pb-5 pt-5'">
      <div v-if="displayItems.length === 0" class="text-center py-8 text-muted-foreground text-sm">
        {{ $t('common.noData') }}
      </div>
      <div v-else data-testid="bar-list" :class="compact ? 'space-y-2' : 'space-y-3'">
        <div
          v-for="(item, index) in displayItems"
          :key="item.id"
          class="group flex items-center gap-3 py-1.5 transition-colors duration-150 hover:bg-accent dark:hover:bg-foreground/30 -mx-2 px-2 rounded-lg"
        >
          <!-- Rank number -->
          <span
            class="shrink-0 w-5 text-right font-mono text-xs tabular-nums text-muted-foreground"
          >
            {{ index + 1 }}
          </span>

          <!-- Label and bar container -->
          <div class="flex-1 min-w-0">
            <!-- Label row -->
            <div class="flex items-center justify-between gap-2 mb-1">
              <span class="text-sm font-medium text-foreground truncate">{{ item.label }}</span>
              <span
                v-if="showValue"
                class="shrink-0 font-mono text-sm tabular-nums text-foreground"
              >
                {{ formatCompactNumber(item.value) }}
              </span>
            </div>

            <!-- Progress bar -->
            <div class="relative h-1.5 bg-accent dark:bg-foreground rounded-full overflow-hidden">
              <div
                class="absolute inset-y-0 left-0 rounded-full transition-all duration-500 ease-out"
                :style="{
                  width: item.percentage + '%',
                  backgroundColor: item.color,
                }"
              />
            </div>

            <!-- Subtitle if provided -->
            <p v-if="item.subtitle" class="text-xs text-muted-foreground mt-1 truncate">
              {{ item.subtitle }}
            </p>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
