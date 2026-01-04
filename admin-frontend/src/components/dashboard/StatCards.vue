<script setup lang="ts">
import { IconTrendingDown, IconTrendingUp, IconMinus } from '@tabler/icons-vue'

import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardAction,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export interface StatItem {
  title: string
  value: string
  change: string
  trend: 'up' | 'down' | 'neutral'
  description: string
}

defineProps<{
  stats: StatItem[]
}>()

const getTrendIcon = (trend: string) => {
  switch (trend) {
    case 'up':
      return IconTrendingUp
    case 'down':
      return IconTrendingDown
    default:
      return IconMinus
  }
}
</script>

<template>
  <div class="grid grid-cols-1 gap-4 px-4 lg:px-6 md:grid-cols-2 lg:grid-cols-4">
    <Card
      v-for="(stat, index) in stats"
      :key="index"
      class="bg-gradient-to-t from-primary/5 to-card shadow-xs dark:bg-card"
    >
      <CardHeader>
        <CardDescription>{{ stat.title }}</CardDescription>
        <CardTitle class="text-3xl font-semibold tabular-nums">
          {{ stat.value }}
        </CardTitle>
        <CardAction>
          <Badge variant="outline">
            <component :is="getTrendIcon(stat.trend)" />
            {{ stat.change }}
          </Badge>
        </CardAction>
      </CardHeader>
      <CardFooter class="flex-col items-start gap-1.5 text-sm">
        <div class="line-clamp-1 flex gap-2 font-medium">
          <span v-if="stat.trend === 'up'">Trending up</span>
          <span v-else-if="stat.trend === 'down'">Trending down</span>
          <span v-else>Steady</span>
          <component :is="getTrendIcon(stat.trend)" class="size-4" />
        </div>
        <div class="text-muted-foreground">{{ stat.description }}</div>
      </CardFooter>
    </Card>
  </div>
</template>
