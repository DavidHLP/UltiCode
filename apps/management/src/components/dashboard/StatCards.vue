<script setup lang="ts">
import type { Component } from 'vue'
import { TrendingDown, TrendingUp, Minus } from '@lucide/vue'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@ulticode/design-system/ui/card'
import { Badge } from '@ulticode/design-system/ui/badge'

export interface StatItem {
  title: string
  value: string
  change: string
  trend: 'up' | 'down' | 'neutral'
  description: string
  icon?: Component
  href?: string
}

defineProps<{ stats: StatItem[] }>()
const trendIcons = { up: TrendingUp, down: TrendingDown, neutral: Minus }
const trendColors = {
  up: 'text-status-success-mark',
  down: 'text-status-error-mark',
  neutral: 'text-muted-foreground',
}
</script>

<template>
  <div class="grid gap-4 @xl/dashboard:grid-cols-2 @4xl/dashboard:grid-cols-4">
    <component
      :is="stat.href ? 'a' : 'div'"
      v-for="stat in stats"
      :key="stat.title"
      :href="stat.href"
      data-testid="dashboard-stat-card"
    >
      <Card class="h-full">
        <CardHeader class="flex-row items-center justify-between gap-3">
          <CardTitle>{{ stat.title }}</CardTitle>
          <component
            :is="stat.icon"
            v-if="stat.icon"
            class="size-4 text-muted-foreground"
            aria-hidden="true"
          />
        </CardHeader>
        <CardContent
          ><p class="text-3xl font-semibold tabular-nums">{{ stat.value }}</p></CardContent
        >
        <CardFooter class="flex-wrap gap-2">
          <Badge variant="secondary"
            ><component
              :is="trendIcons[stat.trend]"
              :class="trendColors[stat.trend]"
              aria-hidden="true"
            />{{ stat.change }}</Badge
          >
          <CardDescription>{{ stat.description }}</CardDescription>
        </CardFooter>
      </Card>
    </component>
  </div>
</template>
